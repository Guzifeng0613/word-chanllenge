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

# V4.3 题型阶梯（先认后背，拼写压轴）
# 毕业前，每个词按 step 0→3 爬升，题型由 step 决定（不再随机）
TYPE_LADDER = ["judge", "en2cn", "cn2en", "spell"]
# step 0/1/2 答对后，下一级的间隔（分钟）；step 3 拼写答对 = 毕业
STEP_DELAY_MIN = [2, 3, 5]
# 毕业后每个 stage 的复习题型：10分轻确认→1天拼写→3天识别→7天回顾→16天识别→30天拼写
GRAD_REVIEW_TYPE = ["cn2en", "spell", "en2cn", "cn2en", "en2cn", "spell"]

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


# ---------------- V4.3 单词记录 / 题型阶梯 ----------------
def norm_rec(rec):
    """补齐单词记录的缺省字段（内部时间统一用毫秒）。"""
    rec = dict(rec or {})
    rec.setdefault("state", "NEW")
    rec.setdefault("step", 0)
    rec.setdefault("graduated", False)
    rec.setdefault("stage", 0)
    rec.setdefault("lapses", 0)
    rec.setdefault("streak", 0)
    rec.setdefault("wrong_count", 0)
    rec.setdefault("due", 0)
    return rec


def type_for_word(rec):
    """该词当前应该出现的题型：毕业前按 step，毕业后按 stage。"""
    rec = norm_rec(rec)
    if rec["graduated"]:
        return GRAD_REVIEW_TYPE[rec["stage"]]
    return TYPE_LADDER[rec["step"]]


def learn_step(rec, correct, fast=False, now=None):
    """毕业前：按题型阶梯爬升；答错记录在案并退回一级。"""
    now = now if now is not None else int(time.time() * 1000)
    rec = norm_rec(rec)
    s = rec["step"]
    if not correct:
        rec["wrong_count"] += 1
        rec["state"] = "LEARNING"
        rec["step"] = max(0, s - 1)
        rec["due"] = now + STEP_DELAY_MIN[rec["step"]] * 60000
        return rec
    if s < 3:
        rec["step"] = s + 1
        rec["state"] = "LEARNING"
        rec["due"] = now + STEP_DELAY_MIN[s] * 60000
    else:
        # 拼写（最终关卡）答对 → 毕业，进入长期间隔重复
        rec["graduated"] = True
        rec["state"] = "FAMILIAR"
        rec["stage"] = 0
        rec["streak"] = 0
        rec["due"] = now + SRS_LADDER[0] * 60000
    return rec


def grad_step(rec, correct, fast=False, now=None):
    """毕业后 SM-2：按 stage 升级；答错 lapse、记录并退回阶梯。"""
    now = now if now is not None else int(time.time() * 1000)
    rec = norm_rec(rec)
    top = len(SRS_LADDER) - 1
    if not correct:
        rec["wrong_count"] += 1
        rec["lapses"] += 1
        rec["stage"] = 0
        rec["streak"] = 0
        rec["graduated"] = False
        rec["state"] = "LEARNING"
        rec["step"] = 1 if rec["wrong_count"] >= 2 else 2
        rec["due"] = now + STEP_DELAY_MIN[rec["step"]] * 60000
        return rec
    if fast:
        rec["stage"] = min(rec["stage"] + 1, top)
        rec["streak"] = 0
    else:
        rec["streak"] += 1
        if rec["streak"] >= 2:
            rec["stage"] = min(rec["stage"] + 1, top)
            rec["streak"] = 0
    st = rec["stage"]
    rec["state"] = "MASTERED" if st == top else "FAMILIAR"
    rec["due"] = now + SRS_LADDER[st] * 60000
    return rec


def schedule(rec, correct, fast=False, now=None):
    """统一调度：毕业前走阶梯，毕业后走 SM-2。"""
    rec = norm_rec(rec)
    if rec["graduated"]:
        return grad_step(rec, correct, fast, now)
    return learn_step(rec, correct, fast, now)


def due_learning(progress, now):
    """到期的未毕业词（含错词、中段学习）。"""
    return [w for w, r in progress.items()
            if not r.get("graduated") and r.get("due", 0) and r["due"] <= now]


def due_graduated(progress, now):
    """到期的已毕业词（SM-2 复习）。"""
    return [w for w, r in progress.items()
            if r.get("graduated") and r.get("due", 0) and r["due"] <= now]


# ---------------- 组卷（V4.3 题型阶梯驱动） ----------------
def draw_question(layer=1, progress=None, force=None,
                  book=None, difficulty=None, theta=None,
                  new_today=0, now=None):
    """出一道题。
    优先级：到期未毕业（错词/中段学习）→ 到期已毕业（SM-2 复习）→ 新词（step0）。
    θ / 手动难度控制"选词难度档"；题型由单词的 step / stage 决定（拼写压轴）。
    """
    progress = progress or {}
    now_ms = now if now is not None else int(time.time() * 1000)

    # 词书范围
    if book:
        book_words = [o["w"] for o in words_by_book(book)]
    else:
        book_words = [o["w"] for o in BANK_WORDS]
    book_set = set(book_words)

    # 难度档（θ 自适应或手动）
    if force:
        tier = None
    elif theta is not None:
        tier, _bucket = adaptive_tier(theta)
    elif difficulty:
        tier = difficulty
    else:
        tier = min(layer, 3) if not book else None

    # 到期队列（限定当前词书）
    dl = [w for w in due_learning(progress, now_ms) if w in book_set]
    dg = [w for w in due_graduated(progress, now_ms) if w in book_set]
    due = dl + dg

    word, from_review = None, False
    cap = CONFIG["daily"]["newCap"]
    can_new = new_today < cap
    try_new = can_new and (not due or random.random() < 0.30)

    if try_new:
        # 新词：当前词书内、无记录、按难度档
        unseen = [w for w in book_words if w not in progress
                  and (tier is None or WMAP[w]["t"] == tier)]
        if not unseen:
            unseen = [w for w in book_words if w not in progress]
        if unseen:
            word = random.choice(unseen)
    if word is None:
        if due:
            word = random.choice(dl) if dl else random.choice(dg)
            from_review = True
        else:
            # 无到期：优先继续推进最接近毕业的学习词（会话节奏，不硬等计时）
            learning = [w for w in progress
                        if not progress[w].get("graduated") and w in book_set]
            learning.sort(key=lambda w: -progress[w].get("step", 0))
            if learning:
                word = learning[0]
            elif can_new:
                unseen = [w for w in book_words if w not in progress
                          and (tier is None or WMAP[w]["t"] == tier)]
                if not unseen:
                    unseen = [w for w in book_words if w not in progress]
                if unseen:
                    word = random.choice(unseen)
            if word is None:
                # 真正兜底：任意词（遵守手动难度档）
                pool = [w for w in book_words
                        if tier is None or WMAP[w]["t"] == tier]
                word = random.choice(pool or book_words)

    # 题型：force 覆盖；否则按阶梯；超长词不拼写
    qtype = force or type_for_word(progress.get(word))
    if qtype == "spell" and len(word) > 8:
        qtype = "cn2en"

    entry = WMAP[word]
    rec = progress.get(word)
    q = {"qtype": qtype, "word": word, "pos": entry.get("p", ""),
         "mean": entry["m"], "review": from_review,
         "step": (rec or {}).get("step", 0),
         "graduated": (rec or {}).get("graduated", False)}

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
        is_first = rec is None or (not rec.get("graduated")
                                   and rec.get("step", 0) == 0)
        if is_first:
            # 初识：固定给出正确释义（呈现/学习）
            shown, correct = entry["m"], "yes"
        elif random.random() < 0.5:
            shown, correct = entry["m"], "yes"
        else:
            shown, correct = random.choice(make_distractors(word, 1)), "no"
        q.update(judgeShown=shown, correct=correct,
                 timeLimit=CONFIG["speed"]["normalT"])
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
