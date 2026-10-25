"""单词地下城 — FastAPI 后端
词库下发、后端组卷、答题结算（错词分级 + 间隔重复）、错词本、进度同步与统计。
运行：uvicorn main:app --host 0.0.0.0 --port 8000
"""
import secrets
import sqlite3
import time
from contextlib import contextmanager
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel

import core

DB_PATH = "word_dungeon.db"


def init_db():
    with get_db() as db:
        db.executescript(
            """
        CREATE TABLE IF NOT EXISTS users(
          id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, token TEXT UNIQUE,
          created INTEGER);
        CREATE TABLE IF NOT EXISTS progress(
          user_id INTEGER, word TEXT, state TEXT DEFAULT 'NEW',
          interval_min INTEGER DEFAULT 10, wrong_count INTEGER DEFAULT 0,
          last_review INTEGER, due INTEGER,
          PRIMARY KEY(user_id, word));
        CREATE TABLE IF NOT EXISTS answers(
          id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, word TEXT,
          qtype TEXT, correct INTEGER, damage INTEGER, ts INTEGER);
        """
        )
        # V4.2 迁移：用户能力 θ；词条 SRS 字段
        for stmt in (
            "ALTER TABLE users ADD COLUMN theta REAL DEFAULT 1.5",
            "ALTER TABLE progress ADD COLUMN stage INTEGER DEFAULT 0",
            "ALTER TABLE progress ADD COLUMN lapses INTEGER DEFAULT 0",
            "ALTER TABLE progress ADD COLUMN streak INTEGER DEFAULT 0",
            # V4.3 迁移：题型阶梯 step、是否毕业、首见时间
            "ALTER TABLE progress ADD COLUMN step INTEGER DEFAULT 0",
            "ALTER TABLE progress ADD COLUMN graduated INTEGER DEFAULT 0",
            "ALTER TABLE progress ADD COLUMN created INTEGER DEFAULT 0",
        ):
            try:
                db.execute(stmt)
            except sqlite3.OperationalError:
                pass


@contextmanager
def get_db():
    db = sqlite3.connect(DB_PATH)
    db.row_factory = sqlite3.Row
    try:
        yield db
        db.commit()
    finally:
        db.close()


init_db()
app = FastAPI(title="单词地下城 API", version="4.3")


# ---------------- 认证（设备令牌） ----------------
def current_user(authorization: Optional[str] = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "缺少 Bearer token")
    token = authorization.split(" ", 1)[1]
    with get_db() as db:
        row = db.execute("SELECT * FROM users WHERE token=?", (token,)).fetchone()
        if not row:
            raise HTTPException(401, "无效 token")
        return dict(row)


@app.post("/auth/guest")
def guest_login():
    """设备免密登录，返回 token。"""
    token = secrets.token_hex(20)
    with get_db() as db:
        cur = db.execute(
            "INSERT INTO users(name,token,created) VALUES(?,?,?)",
            ("词灵使", token, int(time.time())))
        uid = cur.lastrowid
    return {"token": token, "user_id": uid, "name": "词灵使"}


# ---------------- 词库 ----------------
@app.get("/bank/meta")
def bank_meta():
    """词书与难度定义（客户端渲染"选择词书 / 难度"界面）。"""
    return core.book_meta()


@app.get("/bank/tier")
def bank_tier(tier: int = 1):
    """按难度层下发词库（开源词库 COCA/四六级/CEFR 映射 tier）。"""
    return {"tier": tier, "words": core.words_by_tier(tier)}


@app.get("/bank/book")
def bank_book(book: str, difficulty: Optional[int] = None):
    """按词书（gaokao/cet4/cet6/kaoyan）下发，可指定难度 1/2/3。"""
    if book not in core.BOOKS:
        raise HTTPException(400, "未知词书，可用：" + ",".join(core.BOOKS))
    if difficulty and difficulty not in core.DIFFS:
        raise HTTPException(400, "难度需为 1/2/3")
    return {"book": book, "difficulty": difficulty,
            "words": core.words_by_book(book, difficulty)}


@app.get("/bank/word")
def bank_word(word: str):
    if word not in core.WMAP:
        raise HTTPException(404, "词条不存在")
    o = core.WMAP[word]
    return {"word": o["w"], "pos": o.get("p", ""), "mean": o["m"],
            "tier": o["t"], "books": o.get("b", []),
            "distractors": core.make_distractors(word)}


# ---------------- 组卷（后端出题） ----------------
class DrawReq(BaseModel):
    layer: int = 1
    force: Optional[str] = None
    book: Optional[str] = None
    difficulty: Optional[int] = None
    adaptive: bool = True


def _progress_map(db, uid):
    """读取进度并转成 core 期望的格式：due 秒→毫秒，graduated→布尔。"""
    rows = db.execute("SELECT * FROM progress WHERE user_id=?", (uid,)).fetchall()
    out = {}
    for r in rows:
        d = dict(r)
        d["due"] = d["due"] * 1000 if d["due"] else 0
        d["graduated"] = bool(d["graduated"])
        out[r["word"]] = d
    return out


def _new_today(db, uid, today0):
    """今日首次引入的新词数。"""
    return db.execute(
        "SELECT COUNT(*) c FROM progress WHERE user_id=? AND created>=?",
        (uid, today0)).fetchone()["c"]


@app.post("/quiz/draw")
def quiz_draw(req: DrawReq, user=Depends(current_user)):
    """后端组卷：题型阶梯驱动（先认后背，拼写压轴）；θ 控选词难度。"""
    now = int(time.time())
    today0 = now // 86400 * 86400
    with get_db() as db:
        prog = _progress_map(db, user["id"])
        new_today = _new_today(db, user["id"], today0)
    theta = user["theta"] if req.adaptive else None
    q = core.draw_question(req.layer, prog, req.force,
                          req.book, req.difficulty, theta, new_today)
    return q


# ---------------- 到期复习 ----------------
@app.get("/review/due")
def review_due(book: Optional[str] = None, user=Depends(current_user)):
    """今日到期队列：区分"未毕业(学习中)"与"已毕业(复习)"，及新词上限。"""
    now_ms = int(time.time() * 1000)
    today0 = int(time.time()) // 86400 * 86400
    with get_db() as db:
        prog = _progress_map(db, user["id"])
        new_today = _new_today(db, user["id"], today0)
    bookset = (set(o["w"] for o in core.words_by_book(book))
               if book else None)
    dl = [w for w in core.due_learning(prog, now_ms)
          if bookset is None or w in bookset]
    dg = [w for w in core.due_graduated(prog, now_ms)
          if bookset is None or w in bookset]
    return {"due_learning": dl, "due_graduated": dg,
            "learning_count": len(dl), "review_count": len(dg),
            "new_today": new_today,
            "theta": user["theta"],
            "new_cap": core.CONFIG["daily"]["newCap"],
            "target_rate": core.CONFIG["adaptive"]["targetRate"]}


class AnswerReq(BaseModel):
    word: str
    qtype: str
    answer: str
    time_limit: float
    remain: float
    combo: int = 0
    crit: bool = False
    card_mult: float = 1.0
    shown: Optional[str] = None


@app.post("/quiz/answer")
def quiz_answer(req: AnswerReq, user=Depends(current_user)):
    """后端结算（V4.2）：判定对错、伤害、θ 更新、SM-2 调度、错词分级。"""
    entry = core.WMAP.get(req.word)
    if not entry:
        raise HTTPException(404, "词条不存在")

    if req.qtype == "judge":
        truth = "yes" if req.shown == entry["m"] else "no"
        is_correct = req.answer == truth
    else:
        correct_val = entry["m"] if req.qtype == "en2cn" else entry["w"]
        is_correct = req.answer.strip().lower() == correct_val.strip().lower()

    fast = is_correct and core.is_fast(req.qtype, req.time_limit, req.remain)
    now_ms = int(time.time() * 1000)
    now = int(time.time())

    with get_db() as db:
        row = db.execute(
            "SELECT * FROM progress WHERE user_id=? AND word=?",
            (user["id"], req.word)).fetchone()
        if row:
            old_rec = {
                "state": row["state"], "step": row["step"],
                "graduated": bool(row["graduated"]), "stage": row["stage"],
                "lapses": row["lapses"], "streak": row["streak"],
                "wrong_count": row["wrong_count"],
                "due": row["due"] * 1000 if row["due"] else 0}
            state = row["state"]
            wrong_count = row["wrong_count"]
            created = row["created"] or 0
        else:
            old_rec = None
            state = "NEW"
            wrong_count = 0
            created = now

        if is_correct:
            dmg, detail = core.calc_damage(
                req.word, state, req.combo, req.qtype, req.time_limit,
                req.remain, wrong_count if wrong_count else 0,
                req.crit, req.card_mult)
        else:
            dmg, detail = 0, {}

        # 题型阶梯 / SM-2 调度 + θ 更新
        new_rec = core.schedule(old_rec, is_correct, fast, now_ms)
        new_theta = core.update_theta(user["theta"], is_correct, fast)
        interval_min = max(1, (new_rec["due"] - now_ms) // 60000)
        new_wrong = new_rec["wrong_count"]

        db.execute(
            "INSERT INTO progress(user_id,word,state,interval_min,wrong_count,"
            "last_review,due,stage,lapses,streak,step,graduated,created) "
            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) "
            "ON CONFLICT(user_id,word) DO UPDATE SET state=?,interval_min=?,"
            "wrong_count=?,last_review=?,due=?,stage=?,lapses=?,streak=?,"
            "step=?,graduated=?,created=?",
            (user["id"], req.word, new_rec["state"], interval_min, new_wrong,
             now, new_rec["due"] // 1000, new_rec["stage"],
             new_rec["lapses"], new_rec["streak"], new_rec["step"],
             int(new_rec["graduated"]), created,
             new_rec["state"], interval_min, new_wrong, now,
             new_rec["due"] // 1000, new_rec["stage"],
             new_rec["lapses"], new_rec["streak"], new_rec["step"],
             int(new_rec["graduated"]), created))
        db.execute("UPDATE users SET theta=? WHERE id=?",
                   (new_theta, user["id"]))
        db.execute(
            "INSERT INTO answers(user_id,word,qtype,correct,damage,ts) "
            "VALUES(?,?,?,?,?,?)",
            (user["id"], req.word, req.qtype, int(is_correct), dmg, now))

    return {"correct": is_correct, "fast": fast, "damage": dmg, "detail": detail,
            "correct_answer": entry["w"] if req.qtype == "spell" else entry["m"],
            "state": new_rec["state"], "wrong_count": new_wrong,
            "grade": core.grade_of(new_wrong) if new_wrong else 0,
            "theta": new_theta, "due_min": interval_min,
            "step": new_rec["step"], "graduated": new_rec["graduated"],
            "stage": new_rec["stage"]}


# ---------------- 错词本 ----------------
@app.get("/wrongbook")
def wrongbook(user=Depends(current_user)):
    """分级错词清单：顽固 / 易错 / 生疏。"""
    with get_db() as db:
        prog = _progress_map(db, user["id"])
    rows = core.wrong_book(prog)
    summary = {"total": len(rows),
               "hard": sum(1 for r in rows if r["grade"] == 3),
               "easy": sum(1 for r in rows if r["grade"] == 2),
               "new": sum(1 for r in rows if r["grade"] == 1)}
    return {"summary": summary, "words": rows}


class ReviewReq(BaseModel):
    word: str
    remembered: bool


@app.post("/wrongbook/review")
def wrongbook_review(req: ReviewReq, user=Depends(current_user)):
    """提交复习结果，服务端结算分级（答对移出 / 答错累加）。"""
    now = int(time.time())
    with get_db() as db:
        row = db.execute("SELECT * FROM progress WHERE user_id=? AND word=?",
                         (user["id"], req.word)).fetchone()
        wrong_count = row["wrong_count"] if row else 0
        if req.remembered:
            new_state, interval = core.next_memory(
                row["state"] if row else "LEARNING", True)
            wrong_count = 0
        else:
            wrong_count += 1
            new_state, interval = "LEARNING", core.INTERVALS["LEARNING"]
        db.execute(
            "INSERT INTO progress(user_id,word,state,interval_min,wrong_count,"
            "last_review,due) VALUES(?,?,?,?,?,?,?) "
            "ON CONFLICT(user_id,word) DO UPDATE SET state=?,interval_min=?,"
            "wrong_count=?,last_review=?,due=?",
            (user["id"], req.word, new_state, interval, wrong_count, now,
             now + interval * 60, new_state, interval, wrong_count, now,
             now + interval * 60))
    return {"word": req.word, "state": new_state,
            "wrong_count": wrong_count,
            "grade": core.grade_of(wrong_count) if wrong_count else 0}


# ---------------- 进度同步与统计 ----------------
class SyncReq(BaseModel):
    words: dict  # 客户端本地进度增量


@app.post("/progress/sync")
def progress_sync(req: SyncReq, user=Depends(current_user)):
    """离线优先增量合并：冲突按更高错误次数 / 更近时间戳。"""
    now = int(time.time())
    merged = {}
    with get_db() as db:
        for word, p in req.words.items():
            row = db.execute(
                "SELECT * FROM progress WHERE user_id=? AND word=?",
                (user["id"], word)).fetchone()
            local_wc = row["wrong_count"] if row else 0
            client_wc = p.get("wrong_count", 0)
            wc = max(local_wc, client_wc)
            state = p.get("state", row["state"] if row else "NEW")
            interval = p.get("interval_min", 10)
            db.execute(
                "INSERT INTO progress(user_id,word,state,interval_min,"
                "wrong_count,last_review,due) VALUES(?,?,?,?,?,?,?) "
                "ON CONFLICT(user_id,word) DO UPDATE SET state=?,"
                "interval_min=?,wrong_count=?,last_review=?,due=?",
                (user["id"], word, state, interval, wc, now,
                 now + interval * 60, state, interval, wc, now,
                 now + interval * 60))
            merged[word] = {"state": state, "wrong_count": wc}
    return {"merged": merged}


@app.get("/stats/daily")
def stats_daily(user=Depends(current_user)):
    today0 = int(time.time()) // 86400 * 86400
    with get_db() as db:
        rows = db.execute(
            "SELECT * FROM answers WHERE user_id=? AND ts>=?",
            (user["id"], today0)).fetchall()
        total = len(rows)
        correct = sum(r["correct"] for r in rows)
    return {"questions": total, "correct": correct,
            "accuracy": round(correct / total, 3) if total else 0,
            "daily_goal": 40}
