package com.guzifeng.worddungeon.store;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.guzifeng.worddungeon.core.WordRec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本地持久化（V10.1 对齐 V7.0 前端数据口径）
 * users 表扩展：xp/level/streak/last_day/endless_best/ach（成就 JSON 数组）
 * progress 表扩展：wrong_by（快刷档×题型 错误计数 JSON）
 */
public class ProgressStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "word_dungeon.db";
    private static final int DB_VERSION = 7;
    public static final long LOCAL_USER_ID = 1L;

    private static ProgressStore instance;

    public static synchronized ProgressStore get(Context ctx) {
        if (instance == null) instance = new ProgressStore(ctx.getApplicationContext());
        return instance;
    }

    private ProgressStore(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS users(" +
                "id INTEGER PRIMARY KEY, name TEXT, token TEXT UNIQUE, created INTEGER, theta REAL DEFAULT 1.5, " +
                "xp INTEGER DEFAULT 0, level INTEGER DEFAULT 1, streak INTEGER DEFAULT 0, " +
                "last_day INTEGER DEFAULT 0, endless_best INTEGER DEFAULT 0, ach TEXT DEFAULT '[]', " +
                "souls INTEGER DEFAULT 0, extra TEXT DEFAULT '{}')");
        db.execSQL("CREATE TABLE IF NOT EXISTS progress(" +
                "user_id INTEGER, word TEXT, state TEXT DEFAULT 'NEW', " +
                "step INTEGER DEFAULT 0, graduated INTEGER DEFAULT 0, stage INTEGER DEFAULT 0, " +
                "lapses INTEGER DEFAULT 0, streak INTEGER DEFAULT 0, wrong_count INTEGER DEFAULT 0, " +
                "due_ms INTEGER DEFAULT 0, created INTEGER DEFAULT 0, last_review INTEGER DEFAULT 0, " +
                "wrong_by TEXT DEFAULT '{}', quick_pass INTEGER DEFAULT 0, " +
                "PRIMARY KEY(user_id, word))");
        db.execSQL("CREATE TABLE IF NOT EXISTS answers(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, word TEXT, " +
                "qtype TEXT, correct INTEGER, damage INTEGER, ts INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS runs(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, mode TEXT, " +
                "win INTEGER, layer INTEGER, questions INTEGER, correct INTEGER, " +
                "wrong INTEGER, max_combo INTEGER, kills INTEGER, souls INTEGER, ts INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            onCreate(db);
        }
        if (oldV < 3) {
            try {
                db.execSQL("ALTER TABLE users ADD COLUMN xp INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE users ADD COLUMN level INTEGER DEFAULT 1");
                db.execSQL("ALTER TABLE users ADD COLUMN streak INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE users ADD COLUMN last_day INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE users ADD COLUMN endless_best INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE users ADD COLUMN ach TEXT DEFAULT '[]'");
                db.execSQL("ALTER TABLE progress ADD COLUMN wrong_by TEXT DEFAULT '{}'");
            } catch (Throwable ignore) {
                // 列已存在时忽略
            }
        }
        if (oldV < 4) {
            // V11.7：持久化快刷 quick_pass（错词轮播 passSuppress 依赖）
            try {
                db.execSQL("ALTER TABLE progress ADD COLUMN quick_pass INTEGER DEFAULT 0");
            } catch (Throwable ignore) {
                // 列已存在时忽略
            }
        }
        if (oldV < 5) {
            // V11.7 R2：词魂 souls 钱包持久化（ACC-40）
            try {
                db.execSQL("ALTER TABLE users ADD COLUMN souls INTEGER DEFAULT 0");
            } catch (Throwable ignore) {
                // 列已存在时忽略
            }
        }
        if (oldV < 6) {
            // V12.8：回顾系统——每局战绩表
            db.execSQL("CREATE TABLE IF NOT EXISTS runs(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, mode TEXT, " +
                    "win INTEGER, layer INTEGER, questions INTEGER, correct INTEGER, " +
                    "wrong INTEGER, max_combo INTEGER, kills INTEGER, souls INTEGER, ts INTEGER)");
        }
        if (oldV < 7) {
            // V13.0：extra JSON 列——持久化精华 essence / 祭坛 altar（含彩蛋圣辉 saint）
            try {
                db.execSQL("ALTER TABLE users ADD COLUMN extra TEXT DEFAULT '{}'");
            } catch (Throwable ignore) {
                // 列已存在时忽略
            }
        }
    }

    /** 确保本地用户存在，返回 token。 */
    public String ensureLocalUser() {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT token FROM users WHERE id=?", new String[]{String.valueOf(LOCAL_USER_ID)});
        String token = null;
        if (c.moveToFirst()) token = c.getString(0);
        c.close();
        if (token == null) {
            token = randomToken();
            ContentValues v = new ContentValues();
            v.put("id", LOCAL_USER_ID);
            v.put("name", "词灵使");
            v.put("token", token);
            v.put("created", System.currentTimeMillis() / 1000);
            v.put("theta", 1.5);
            v.put("xp", 0);
            v.put("level", 1);
            v.put("ach", "[]");
            db.insert("users", null, v);
        }
        return token;
    }

    private static String randomToken() {
        StringBuilder sb = new StringBuilder();
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < 40; i++) {
            int n = r.nextInt(16);
            sb.append(Integer.toHexString(n));
        }
        return sb.toString();
    }

    public double getTheta() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT theta FROM users WHERE id=?", new String[]{String.valueOf(LOCAL_USER_ID)});
        double t = 1.5;
        if (c.moveToFirst()) t = c.getDouble(0);
        c.close();
        return t;
    }

    public void setTheta(double theta) {
        ContentValues v = new ContentValues();
        v.put("theta", theta);
        getWritableDatabase().update("users", v, "id=?", new String[]{String.valueOf(LOCAL_USER_ID)});
    }

    /** 学习元数据（等级/XP/打卡/成就/无尽纪录）。 */
    public JSONObject getMeta() {
        JSONObject o = new JSONObject();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT level,xp,streak,last_day,endless_best,ach,souls,extra FROM users WHERE id=?",
                new String[]{String.valueOf(LOCAL_USER_ID)});
        if (c.moveToFirst()) {
            try {
                o.put("level", c.getInt(0));
                o.put("xp", c.getInt(1));
                o.put("streak", c.getInt(2));
                o.put("last_day", c.getLong(3));
                o.put("endless_best", c.getInt(4));
                o.put("ach", new JSONArray(c.getString(5)));
                o.put("souls", c.getInt(6));
                // V13.0：extra JSON 承载精华/祭坛（含彩蛋圣辉），重启不丢
                String extra = c.getString(7);
                if (extra != null && extra.trim().length() > 2) {
                    JSONObject ex = new JSONObject(extra);
                    if (ex.has("essence")) o.put("essence", ex.optInt("essence", 0));
                    JSONObject al = ex.optJSONObject("altar");
                    if (al != null) o.put("altar", al);
                }
            } catch (Exception ignore) {
            }
        } else {
            try {
                o.put("level", 1).put("xp", 0).put("streak", 0).put("last_day", 0)
                        .put("endless_best", 0).put("ach", new JSONArray()).put("souls", 0);
            } catch (Exception ignore) {
            }
        }
        c.close();
        return o;
    }

    public void saveMeta(JSONObject meta) {
        ContentValues v = new ContentValues();
        v.put("level", meta.optInt("level", 1));
        v.put("xp", meta.optInt("xp", 0));
        v.put("streak", meta.optInt("streak", 0));
        v.put("last_day", meta.optLong("last_day", 0));
        v.put("endless_best", meta.optInt("endless_best", 0));
        v.put("ach", meta.optJSONArray("ach") == null ? "[]" : meta.optJSONArray("ach").toString());
        v.put("souls", meta.optInt("souls", 0));
        // V13.0：精华/祭坛写入 extra JSON
        try {
            JSONObject extra = new JSONObject();
            extra.put("essence", meta.optInt("essence", 0));
            JSONObject al = meta.optJSONObject("altar");
            extra.put("altar", al == null ? new JSONObject() : al);
            v.put("extra", extra.toString());
        } catch (Throwable ignore) {
        }
        getWritableDatabase().update("users", v, "id=?", new String[]{String.valueOf(LOCAL_USER_ID)});
    }

    /** 读取全部学习记录。 */
    public Map<String, WordRec> loadProgress() {
        Map<String, WordRec> out = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT word,state,step,graduated,stage,lapses,streak,wrong_count,due_ms,wrong_by,quick_pass " +
                "FROM progress WHERE user_id=?", new String[]{String.valueOf(LOCAL_USER_ID)});
        while (c.moveToNext()) {
            WordRec r = new WordRec();
            r.state = c.getString(1);
            r.step = c.getInt(2);
            r.graduated = c.getInt(3) == 1;
            r.stage = c.getInt(4);
            r.lapses = c.getInt(5);
            r.streak = c.getInt(6);
            r.wrongCount = c.getInt(7);
            r.due = c.getLong(8);
            r.quickPass = c.getInt(10) == 1;
            try {
                JSONObject wb = new JSONObject(c.getString(9));
                for (String level : new String[]{"normal", "hard", "hell"}) {
                    JSONObject lvl = wb.optJSONObject(level);
                    if (lvl != null) {
                        Map<String, Integer> row = new HashMap<>();
                        for (String qt : new String[]{"judge", "en2cn", "cn2en", "spell"}) {
                            row.put(qt, lvl.optInt(qt, 0));
                        }
                        r.wrongBy.put(level, row);
                    }
                }
            } catch (Exception ignore) {
            }
            out.put(c.getString(0), r);
        }
        c.close();
        return out;
    }

    /** 写入/更新一条记录。 */
    public void upsertRec(String word, WordRec r, long nowSec) {
        SQLiteDatabase db = getWritableDatabase();
        boolean exists;
        Cursor c = db.rawQuery("SELECT 1 FROM progress WHERE user_id=? AND word=?",
                new String[]{String.valueOf(LOCAL_USER_ID), word});
        exists = c.moveToFirst();
        c.close();
        ContentValues v = new ContentValues();
        v.put("state", r.state);
        v.put("step", r.step);
        v.put("graduated", r.graduated ? 1 : 0);
        v.put("stage", r.stage);
        v.put("lapses", r.lapses);
        v.put("streak", r.streak);
        v.put("wrong_count", r.wrongCount);
        v.put("due_ms", r.due);
        v.put("last_review", nowSec);
        v.put("quick_pass", r.quickPass ? 1 : 0);
        try {
            JSONObject wb = new JSONObject();
            for (String level : new String[]{"normal", "hard", "hell"}) {
                JSONObject row = new JSONObject();
                Map<String, Integer> m = r.wrongBy.get(level);
                for (String qt : new String[]{"judge", "en2cn", "cn2en", "spell"}) {
                    row.put(qt, m == null ? 0 : m.getOrDefault(qt, 0));
                }
                wb.put(level, row);
            }
            v.put("wrong_by", wb.toString());
        } catch (Exception ignore) {
        }
        if (exists) {
            db.update("progress", v, "user_id=? AND word=?",
                    new String[]{String.valueOf(LOCAL_USER_ID), word});
        } else {
            v.put("user_id", LOCAL_USER_ID);
            v.put("word", word);
            v.put("created", nowSec);
            db.insert("progress", null, v);
        }
    }

    public void insertAnswer(String word, String qtype, boolean correct, int damage, long nowSec) {
        ContentValues v = new ContentValues();
        v.put("user_id", LOCAL_USER_ID);
        v.put("word", word);
        v.put("qtype", qtype);
        v.put("correct", correct ? 1 : 0);
        v.put("damage", damage);
        v.put("ts", nowSec);
        getWritableDatabase().insert("answers", null, v);
    }

    /** 今日首次引入的新词数。 */
    public int newToday(long today0Sec) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM progress WHERE user_id=? AND created>=?",
                new String[]{String.valueOf(LOCAL_USER_ID), String.valueOf(today0Sec)});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    /** 今日统计：答题数、正确数。 */
    public int[] dailyStats(long today0Sec) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*), SUM(correct) FROM answers WHERE user_id=? AND ts>=?",
                new String[]{String.valueOf(LOCAL_USER_ID), String.valueOf(today0Sec)});
        int total = 0, correct = 0;
        if (c.moveToFirst()) {
            total = c.getInt(0);
            correct = c.isNull(1) ? 0 : c.getInt(1);
        }
        c.close();
        return new int[]{total, correct};
    }

    /** 清空全部学习进度与元数据。 */
    public void resetAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM progress WHERE user_id=?", new String[]{String.valueOf(LOCAL_USER_ID)});
        db.execSQL("DELETE FROM answers WHERE user_id=?", new String[]{String.valueOf(LOCAL_USER_ID)});
        db.execSQL("DELETE FROM runs WHERE user_id=?", new String[]{String.valueOf(LOCAL_USER_ID)});
        ContentValues v = new ContentValues();
        v.put("xp", 0);
        v.put("level", 1);
        v.put("streak", 0);
        v.put("last_day", 0);
        v.put("endless_best", 0);
        v.put("ach", "[]");
        v.put("theta", 1.5);
        v.put("souls", 0);
        db.update("users", v, "id=?", new String[]{String.valueOf(LOCAL_USER_ID)});
    }

    /** 累计掌握（MASTERED）词数。 */
    public int masteredCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM progress WHERE user_id=? AND state='MASTERED'",
                new String[]{String.valueOf(LOCAL_USER_ID)});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    /** 今日远征次数（answers 中 qtype='run' 且 correct=1 的行计数，由 finish 写入）。 */
    public int dailyRun(long today0Sec) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM answers WHERE user_id=? AND ts>=? AND word='__run__' AND correct=1",
                new String[]{String.valueOf(LOCAL_USER_ID), String.valueOf(today0Sec)});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    /** P0-4：历史单局最高答对题数（成就 correct50 进度，纯读 runs 表）。 */
    public int bestRunCorrect() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COALESCE(MAX(correct),0) FROM runs WHERE user_id=?",
                new String[]{String.valueOf(LOCAL_USER_ID)});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    // ════════════════════════════════════════════════════════════════
    // 回顾系统（V12.8）：每局战绩写入 + 周/月/年聚合
    // ════════════════════════════════════════════════════════════════

    /** 单局明细（最近战绩列表用）。 */
    public static class RunRow {
        public String mode;
        public boolean win;
        public int layer, questions, correct, maxCombo;
        public long ts;
    }

    /** 总览数据。 */
    public static class RunOverview {
        public int totalRuns, totalQuestions, totalCorrect, deepestLayer, maxCombo, totalSouls;
    }

    /** 趋势序列（折线/柱状共用）。 */
    public static class RunSeries {
        public String[] labels;
        public int[] runs, questions, correct, wins;
    }

    /** 写入一局战绩。 */
    public void insertRun(String mode, boolean win, int layer, int questions,
                          int correct, int wrong, int maxCombo, int kills,
                          int souls, long ts) {
        ContentValues v = new ContentValues();
        v.put("user_id", LOCAL_USER_ID);
        v.put("mode", mode == null ? "campaign" : mode);
        v.put("win", win ? 1 : 0);
        v.put("layer", layer);
        v.put("questions", questions);
        v.put("correct", correct);
        v.put("wrong", wrong);
        v.put("max_combo", maxCombo);
        v.put("kills", kills);
        v.put("souls", souls);
        v.put("ts", ts);
        getWritableDatabase().insert("runs", null, v);
    }

    /** 总览：总闯关/总答题/总正确/最深层/最高连击/总词魂。 */
    public RunOverview runOverview() {
        RunOverview o = new RunOverview();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*), COALESCE(SUM(questions),0), COALESCE(SUM(correct),0), " +
                "COALESCE(MAX(layer),0), COALESCE(MAX(max_combo),0), COALESCE(SUM(souls),0) " +
                "FROM runs WHERE user_id=?", new String[]{String.valueOf(LOCAL_USER_ID)});
        if (c.moveToFirst()) {
            o.totalRuns = c.getInt(0);
            o.totalQuestions = c.getInt(1);
            o.totalCorrect = c.getInt(2);
            o.deepestLayer = c.getInt(3);
            o.maxCombo = c.getInt(4);
            o.totalSouls = c.getInt(5);
        }
        c.close();
        return o;
    }

    /** 时间桶聚合：[runs, questions, correct, wins]。 */
    private int[] runAgg(SQLiteDatabase db, long from, long to) {
        Cursor c = db.rawQuery("SELECT COUNT(*), COALESCE(SUM(questions),0), COALESCE(SUM(correct),0), " +
                "COALESCE(SUM(win),0) FROM runs WHERE user_id=? AND ts>=? AND ts<?",
                new String[]{String.valueOf(LOCAL_USER_ID), String.valueOf(from), String.valueOf(to)});
        int[] a = new int[4];
        if (c.moveToFirst()) {
            a[0] = c.getInt(0);
            a[1] = c.getInt(1);
            a[2] = c.getInt(2);
            a[3] = c.getInt(3);
        }
        c.close();
        return a;
    }

    private static final String[] WEEK_NAMES = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    /**
     * 趋势序列。
     * @param period 0=周（近7天按天） 1=月（近4周按周） 2=年（12个月按月）
     */
    public RunSeries runSeries(int period) {
        SQLiteDatabase db = getReadableDatabase();
        long nowSec = System.currentTimeMillis() / 1000;
        long today0 = nowSec / 86400 * 86400;
        int n = period == 0 ? 7 : period == 1 ? 4 : 12;
        RunSeries s = new RunSeries();
        s.labels = new String[n];
        s.runs = new int[n];
        s.questions = new int[n];
        s.correct = new int[n];
        s.wins = new int[n];

        for (int i = 0; i < n; i++) {
            long from, to;
            if (period == 0) {
                // 近 7 天：i=0 是 6 天前，i=6 是今天
                from = today0 - (6 - i) * 86400L;
                to = from + 86400L;
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(from * 1000L);
                s.labels[i] = WEEK_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1];
            } else if (period == 1) {
                // 近 4 个 7 天桶，i=0 最旧
                long base = today0 + 86400L;   // 明天 0 点
                to = base - (3 - i) * 604800L;
                from = to - 604800L;
                s.labels[i] = "第" + (i + 1) + "周";
            } else {
                // 今年 12 个月
                Calendar cal = Calendar.getInstance();
                int year = cal.get(Calendar.YEAR);
                Calendar f = Calendar.getInstance();
                f.clear();
                f.set(year, i, 1, 0, 0, 0);
                from = f.getTimeInMillis() / 1000L;
                Calendar t = Calendar.getInstance();
                t.clear();
                t.set(year, i + 1, 1, 0, 0, 0);
                to = t.getTimeInMillis() / 1000L;
                s.labels[i] = (i + 1) + "月";
            }
            int[] a = runAgg(db, from, to);
            s.runs[i] = a[0];
            s.questions[i] = a[1];
            s.correct[i] = a[2];
            s.wins[i] = a[3];
        }
        return s;
    }

    /** 模式占比：[campaign, endless, quick] 局数。 */
    public int[] modeShare() {
        int[] share = new int[3];
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT mode, COUNT(*) FROM runs WHERE user_id=? GROUP BY mode",
                new String[]{String.valueOf(LOCAL_USER_ID)});
        while (c.moveToNext()) {
            String m = c.getString(0);
            int idx = "endless".equals(m) ? 1 : "quick".equals(m) ? 2 : 0;
            share[idx] = c.getInt(1);
        }
        c.close();
        return share;
    }

    /** 最近 n 局战绩（按时间倒序）。 */
    public List<RunRow> recentRuns(int n) {
        List<RunRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT mode,win,layer,questions,correct,max_combo,ts FROM runs " +
                "WHERE user_id=? ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(LOCAL_USER_ID), String.valueOf(n)});
        while (c.moveToNext()) {
            RunRow r = new RunRow();
            r.mode = c.getString(0);
            r.win = c.getInt(1) == 1;
            r.layer = c.getInt(2);
            r.questions = c.getInt(3);
            r.correct = c.getInt(4);
            r.maxCombo = c.getInt(5);
            r.ts = c.getLong(6);
            list.add(r);
        }
        c.close();
        return list;
    }
}
