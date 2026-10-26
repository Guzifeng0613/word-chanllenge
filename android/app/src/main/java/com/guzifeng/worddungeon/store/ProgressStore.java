package com.guzifeng.worddungeon.store;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.guzifeng.worddungeon.core.WordRec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 本地持久化（V10.1 对齐 V7.0 前端数据口径）
 * users 表扩展：xp/level/streak/last_day/endless_best/ach（成就 JSON 数组）
 * progress 表扩展：wrong_by（快刷档×题型 错误计数 JSON）
 */
public class ProgressStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "word_dungeon.db";
    private static final int DB_VERSION = 3;
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
                "last_day INTEGER DEFAULT 0, endless_best INTEGER DEFAULT 0, ach TEXT DEFAULT '[]')");
        db.execSQL("CREATE TABLE IF NOT EXISTS progress(" +
                "user_id INTEGER, word TEXT, state TEXT DEFAULT 'NEW', " +
                "step INTEGER DEFAULT 0, graduated INTEGER DEFAULT 0, stage INTEGER DEFAULT 0, " +
                "lapses INTEGER DEFAULT 0, streak INTEGER DEFAULT 0, wrong_count INTEGER DEFAULT 0, " +
                "due_ms INTEGER DEFAULT 0, created INTEGER DEFAULT 0, last_review INTEGER DEFAULT 0, " +
                "wrong_by TEXT DEFAULT '{}', " +
                "PRIMARY KEY(user_id, word))");
        db.execSQL("CREATE TABLE IF NOT EXISTS answers(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, word TEXT, " +
                "qtype TEXT, correct INTEGER, damage INTEGER, ts INTEGER)");
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
        Cursor c = db.rawQuery("SELECT level,xp,streak,last_day,endless_best,ach FROM users WHERE id=?",
                new String[]{String.valueOf(LOCAL_USER_ID)});
        if (c.moveToFirst()) {
            try {
                o.put("level", c.getInt(0));
                o.put("xp", c.getInt(1));
                o.put("streak", c.getInt(2));
                o.put("last_day", c.getLong(3));
                o.put("endless_best", c.getInt(4));
                o.put("ach", new JSONArray(c.getString(5)));
            } catch (Exception ignore) {
            }
        } else {
            try {
                o.put("level", 1).put("xp", 0).put("streak", 0).put("last_day", 0)
                        .put("endless_best", 0).put("ach", new JSONArray());
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
        getWritableDatabase().update("users", v, "id=?", new String[]{String.valueOf(LOCAL_USER_ID)});
    }

    /** 读取全部学习记录。 */
    public Map<String, WordRec> loadProgress() {
        Map<String, WordRec> out = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT word,state,step,graduated,stage,lapses,streak,wrong_count,due_ms,wrong_by " +
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
        ContentValues v = new ContentValues();
        v.put("xp", 0);
        v.put("level", 1);
        v.put("streak", 0);
        v.put("last_day", 0);
        v.put("endless_best", 0);
        v.put("ach", "[]");
        v.put("theta", 1.5);
        db.update("users", v, "id=?", new String[]{String.valueOf(LOCAL_USER_ID)});
    }

    /** 今日是否已打卡（按自然日）。 */
    public boolean todayActive(long today0Sec) {
        JSONObject m = getMeta();
        long last = m.optLong("last_day", 0);
        return last == today0Sec;
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

    /** 今日新词上限内的计数（answers 中 word='__new__'）。 */
    public int dailyNewToday(long today0Sec) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM answers WHERE user_id=? AND ts>=? AND word='__new__' AND correct=1",
                new String[]{String.valueOf(LOCAL_USER_ID), String.valueOf(today0Sec)});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }
}
