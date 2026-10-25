"""单词地下城 — 后端核心逻辑
词库加载、组卷（四题型 + 近义干扰）、错词分级、间隔重复、伤害结算。
客户端（APK/H5）通过 REST API 调用，核心规则以这里为权威。
"""
import json
import os
import random
import time

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")


def _load(name):
    with open(os.path.join(DATA_DIR, name), encoding="utf-8") as f:
        return json.load(f)


WORDS = _load("words.json")            # 示例词库 [{w,p,m,t}]
WB_BANK = _load("wordbooks.json")       # 完整词库 {meta, words:[{w,p,m,t,b,f}]}
BANK_WORDS = WB_BANK["words"]
GROUPS = _load("confuse-groups.json")  # [[...], ...]
WMAP = {o["w"]: o for o in BANK_WORDS}
BOOKS = {b["id"]: b for b in WB_BANK["meta"]["books"]}
DIFFS = {d["id"]: d for d in WB_BANK["meta"]["difficulties"]}

# 配置（与 GDD / Demo 同一版本）
CONFIG = {
    "player": {"maxHp": 100, "baseDamage": 10},
    "speed": {"fastT": 3, "normalT": 6, "fastM": 1.5, "normalM": 1.2},
    "spell": {"base": 4, "perChar": 2,
              "speed": [[60, 2.0], [40, 1.6], [25, 1.25], [0, 1.0]]},
    "combo": {"step": 0.05, "cap": 1.0},
    "pool": {"en2cn": 0.55, "cn2en": 0.15, "spell": 0.10, "judge": 0.20},
    "proficiency": {"NEW": 1.0, "LEARNING": 1.05, "FAMILIAR": 1.2, "MASTERED": 1.4},
    "revenge": {"base": 1.5, "perMiss": 0.25, "cap": 2.0},
    "review": {"count": 3, "springHeal": 2, "springHealTotal": 30},
    "wrongbook": {"hardRate": 0.75, "normalRate": 0.6},
    # V4.2 自适应难度引擎
    "adaptive": {"thetaInit": 1.5, "thetaMin": 1.0, "thetaMax": 3.0,
                 "fastGain": 0.10, "gain": 0.05, "penalty": 0.10,
                 "match": 0.70, "stretch": 0.15, "consolidate": 0.15,
                 "targetRate": 0.85},
    "daily": {"newCap": 20},
}

# V4.2 SM-2 间隔梯（分钟）：10分钟→1天→3天→7天→16天→30天
SRS_LADDER = [10, 1440, 4320, 10080, 23040, 43200]

# 记忆状态到期间隔（分钟）
INTERVALS = {"NEW": 10, "LEARNING": 1440, "FAMILIAR": [2880, 5760, 10080, 21600]}


# ---------------- 词库 ----------------
def words_by_tier(tier):
    return [o for o in BANK_WORDS if o["t"] == tier]


def words_by_book(book, difficulty=None):
    """按词书筛选；difficulty 1 简单 / 2 中等 / 3 困难，可指定。"""
    out = [o for o in BANK_WORDS if book in o.get("b", [])]
    if difficulty:
        out = [o for o in out if o["t"] == difficulty]
    return out


def book_meta():
    """词书与难度定义（供客户端渲染选择界面）。"""
    return {"books": WB_BANK["meta"]["books"],
            "difficulties": WB_BANK["meta"]["difficulties"],
            "counts": WB_BANK["meta"]["counts"]}


def _confuse_group(word):
    for g in GROUPS:
        if word in g:
            return g
    return None


def _same_pos(word):
    p = WMAP.get(word, {}).get("p")
    return [o["w"] for o in BANK_WORDS if o.get("p") == p and o["w"] != word]


def make_distractors(word, n=3):
    """生成释义相近的干扰项：优先易混组，再补同词性词。"""
    out, seen = [], set()
    g = _confuse_group(word)
    if g:
        for w in g:
            if w != word and w in WMAP and w not in seen:
                out.append(w); seen.add(w)
    for w in _same_pos(word):
        if w not in seen:
            out.append(w); seen.add(w)
        if len(out) >= n:
            break
    random.shuffle(out)
    return [WMAP[w]["m"] for w in out[:n]]


# ---------------- 错词分级 ----------------
def grade_of(wrong_count):
    """L3 顽固 ≥3 / L2 易错 =2 / L1 生疏 =1。"""
    if wrong_count >= 3:
        return 3
    if wrong_count == 2:
        return 2
    return 1


def grade_label(wrong_count):
    return {3: "顽固", 2: "易错", 1: "生疏"}[grade_of(wrong_count)]


def weighted_pick(items, weights):
    total = sum(weights)
    r = random.random() * total
    for item, w in zip(items, weights):
        r -= w
        if r <= 0:
            return item
    return items[-1]


# ---------------- V4.2 自适应难度 ----------------
def update_theta(theta, correct, fast=False):
    """按单题表现更新玩家能力 θ（范围 1.0–3.0）。"""
    a = CONFIG["adaptive"]
    if correct:
        theta += a["fastGain"] if fast else a["gain"]
    else:
        theta -= a["penalty"]
    return round(max(a["thetaMin"], min(a["thetaMax"], theta)), 3)


def adaptive_tier(theta):
    """抽题难度配比：70% 匹配 / 15% 拉伸 / 15% 巩固。返回 (难度档, 桶名)。"""
    a = CONFIG["adaptive"]
    r = random.random()
    base = max(1, min(3, round(theta)))
    if r < a["match"]:
        return base, "match"
    if r < a["match"] + a["stretch"]:
        return min(3, base + 1), "stretch"
    return max(1, base - 1), "consolidate"


# ---------------- V4.2 SM-2 间隔重复 ----------------
def srs_schedule(rec, correct, fast=False, now=None):
    """简化 SM-2 调度。
    rec: {stage,due,lapses,streak,state}；fast 答对直接升级，normal 需连续 2 次。
    """
    now = now if now is not None else int(time.time() * 1000)
    rec = dict(rec or {})
    rec.setdefault("stage", 0)
    rec.setdefault("lapses", 0)
    rec.setdefault("streak", 0)
    if not correct:
        rec["stage"] = 0
        rec["streak"] = 0
        rec["lapses"] += 1
        rec["state"] = "LEARNING"
    else:
        if fast:
            rec["stage"] = min(rec["stage"] + 1, len(SRS_LADDER) - 1)
            rec["streak"] = 0
        else:
            rec["streak"] += 1
            if rec["streak"] >= 2:
                rec["stage"] = min(rec["stage"] + 1, len(SRS_LADDER) - 1)
                rec["streak"] = 0
        stage = rec["stage"]
        rec["state"] = ("MASTERED" if stage == len(SRS_LADDER) - 1
                        else "FAMILIAR" if stage >= 1 else "LEARNING")
    rec["due"] = now + SRS_LADDER[rec["stage"]] * 60 * 1000
    return rec


def due_queue(progress, now=None, book=None):
    """到期需复习的词；可按词书范围过滤。
    时间单位与 progress 中 due 一致（DB 存秒；调用方传同单位 now）。
    """
    now = now if now is not None else int(time.time())
    bookset = set(o["w"] for o in words_by_book(book)) if book else None
    out = []
    for word, rec in progress.items():
        if rec.get("due") and rec["due"] <= now and (bookset is None or word in bookset):
            out.append(word)
    return out


def review_qtype(grade):
    """复习题型按顽固度：L3 拼写 / L2 英中或中英 / L1 判断。"""
    if grade >= 3:
        return "spell"
    if grade == 2:
        return random.choice(["en2cn", "cn2en"])
    return "judge"


# ---------------- 组卷 ----------------
def _pick_qtype():
    r = random.random()
    acc = 0
    for qt, prob in CONFIG["pool"].items():
        acc += prob
        if r < acc:
            return qt
    return "en2cn"


def draw_question(layer=1, wrong_words=None, force=None,
                  book=None, difficulty=None, theta=None, due_words=None):
    """出一道题（V4.2）。
    - wrong_words: {word: wrong_count} 错词按分级加权反复出现，题型按顽固度强制。
    - book: 词书；difficulty: 1/2/3 手动硬过滤（0/None=全部）。
    - theta: 自适应能力值（给定则按 70/15/15 配比，consolidate 桶优先到期词）。
    - due_words: 到期复习词列表。
    """
    wrong_words = wrong_words or {}
    due_words = due_words or []

    # 词书范围（全部难度）
    if book:
        book_words = [o["w"] for o in words_by_book(book)]
    else:
        book_words = [o["w"] for o in BANK_WORDS]

    # 目标难度档与桶
    bucket = "normal"
    if force:
        tier = None
    elif theta is not None:
        tier, bucket = adaptive_tier(theta)
    elif difficulty:
        tier = difficulty
    else:
        tier = min(layer, 3) if not book else None

    word, qtype, from_review = None, None, False

    # 1) 错词优先（只在当前词书内），题型按顽固度
    pending = [w for w in wrong_words if w in book_words]
    if pending:
        has_hard = any(wrong_words[w] >= 2 for w in pending)
        rate = (CONFIG["wrongbook"]["hardRate"] if has_hard
                else CONFIG["wrongbook"]["normalRate"])
        if random.random() < rate:
            w = weighted_pick(pending, [min(3, wrong_words[w]) for w in pending])
            qt = force or review_qtype(grade_of(wrong_words[w]))
            if qt != "spell" or len(w) <= 8:
                word, qtype, from_review = w, qt, True

    # 2) 自适应巩固桶：到期词（快速判断）
    if word is None and bucket == "consolidate":
        due = [w for w in due_words if w in book_words]
        if due:
            w = random.choice(due)
            qt = force or "judge"
            if qt != "spell" or len(w) <= 8:
                word, qtype, from_review = w, qt, True

    # 3) 常规：按目标难度抽词
    if word is None:
        qtype = force or _pick_qtype()
        def ok(w):
            return len(w) <= 8 if qtype == "spell" else True
        if tier is not None:
            cand = [w for w in book_words if ok(w) and WMAP[w]["t"] == tier]
        else:
            cand = [w for w in book_words if ok(w)]
        if not cand:
            cand = [w for w in book_words if ok(w)] or \
                   [o["w"] for o in BANK_WORDS if ok(o["w"])]
        word = random.choice(cand)

    entry = WMAP[word]
    q = {"qtype": qtype, "word": word, "pos": entry.get("p", ""),
         "mean": entry["m"], "bucket": bucket, "review": from_review}

    if qtype == "en2cn":
        opts = make_distractors(word) + [entry["m"]]
        random.shuffle(opts)
        q.update(options=opts, correct=entry["m"], timeLimit=CONFIG["speed"]["normalT"])
    elif qtype == "cn2en":
        opts = _distractor_words(word) + [word]
        random.shuffle(opts)
        q.update(options=opts, correct=word, timeLimit=CONFIG["speed"]["normalT"])
    elif qtype == "spell":
        limit = CONFIG["spell"]["base"] + len(word) * CONFIG["spell"]["perChar"]
        q.update(correct=word, hint=entry["m"], timeLimit=limit)
    else:  # judge
        if random.random() < 0.5:
            shown = entry["m"]; correct = "yes"
        else:
            shown = random.choice(make_distractors(word, 1))
            correct = "no"
        q.update(judgeShown=shown, correct=correct, timeLimit=CONFIG["speed"]["normalT"])
    return q


def _distractor_words(word, n=3):
    """英文干扰词（cn2en 用）。"""
    out, seen = [], set()
    g = _confuse_group(word)
    if g:
        for w in g:
            if w != word and w in WMAP and w not in seen:
                out.append(w); seen.add(w)
    for w in _same_pos(word):
        if w not in seen:
            out.append(w); seen.add(w)
        if len(out) >= n:
            break
    random.shuffle(out)
    return out[:n]


# ---------------- 结算 ----------------
def speed_mult(qtype, time_limit, remain):
    """按提交快慢动态分档；拼写题按剩余时间比例连续分档。"""
    if qtype == "spell":
        pct = remain / time_limit * 100 if time_limit else 0
        for threshold, mult in CONFIG["spell"]["speed"]:
            if pct >= threshold:
                return mult
        return 1.0
    if remain >= time_limit - CONFIG["speed"]["fastT"]:
        return CONFIG["speed"]["fastM"]
    return CONFIG["speed"]["normalM"]


def is_fast(qtype, time_limit, remain):
    """是否属于"快速答对"（拼写剩余≥60%，其余在 fastT 窗口内）。"""
    if qtype == "spell":
        pct = remain / time_limit * 100 if time_limit else 0
        return pct >= 60
    return remain >= time_limit - CONFIG["speed"]["fastT"]


def combo_mult(combo):
    return 1 + min(combo, 10) * CONFIG["combo"]["step"]


def revenge_mult(wrong_count):
    """错过的词再答对：首次 1.5，每多错过 +0.25，上限 2.0。"""
    if wrong_count <= 0:
        return 0
    r = CONFIG["revenge"]["base"] + (wrong_count - 1) * CONFIG["revenge"]["perMiss"]
    return min(r, CONFIG["revenge"]["cap"])


def calc_damage(word, state, combo, qtype, time_limit, remain,
                wrong_count=0, crit=False, card_mult=1.0):
    """完整伤害结算，返回 (总伤害, 分项明细)。"""
    base = CONFIG["player"]["baseDamage"]
    sm = speed_mult(qtype, time_limit, remain)
    cm = combo_mult(combo)
    pm = CONFIG["proficiency"].get(state, 1.0)
    rm = revenge_mult(wrong_count)
    dmg = base * sm * cm * pm * card_mult
    if rm:
        dmg *= rm
    if crit:
        dmg *= 2
    detail = {"base": base, "speed": round(sm, 2), "combo": round(cm, 2),
              "proficiency": pm, "revenge": round(rm, 2) if rm else 0,
              "crit": crit, "total": round(dmg)}
    return round(dmg), detail


# ---------------- 间隔重复 ----------------
def next_memory(state, correct):
    if not correct:
        return "LEARNING", INTERVALS["LEARNING"]
    order = ["NEW", "LEARNING", "FAMILIAR", "MASTERED"]
    i = order.index(state)
    new_state = order[min(i + 1, 3)]
    if new_state == "FAMILIAR":
        return new_state, INTERVALS["FAMILIAR"][0]
    return new_state, INTERVALS.get(new_state, INTERVALS["LEARNING"])


def wrong_book(progress):
    """progress: {word: {state, wrong_count,...}} → 分级错词清单。"""
    rows = []
    for word, p in progress.items():
        wc = p.get("wrong_count", 0)
        if wc > 0 or p.get("state") == "LEARNING":
            rows.append({"word": word, "pos": WMAP.get(word, {}).get("p", ""),
                         "mean": WMAP.get(word, {}).get("m", ""),
                         "wrong_count": wc,
                         "grade": grade_of(wc) if wc else 1})
    rows.sort(key=lambda x: (-x["grade"], -x["wrong_count"]))
    return rows


def review_order(progress, n=3):
    """分级复习：顽固 → 易错 → 生疏。"""
    return [r["word"] for r in wrong_book(progress)][:n]
