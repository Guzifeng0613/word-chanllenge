package com.guzifeng.worddungeon.store;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.guzifeng.worddungeon.core.WordRec;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地持久化（对应 main.py 的 SQLite：users/progress/answers）
 * 单设备单用户（id=1）；progress 直接存毫秒 due。
 */
public class ProgressStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "word_dungeon.db";
    private static final int DB_VERSION = 2;
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
                "id INTEGER PRIMARY KEY, name TEXT, token TEXT UNIQUE, created INTEGER, theta REAL DEFAULT 1.5)");
        db.execSQL("CREATE TABLE IF NOT EXISTS progress(" +
                "user_id INTEGER, word TEXT, state TEXT DEFAULT 'NEW', " +
                "step INTEGER DEFAULT 0, graduated INTEGER DEFAULT 0, stage INTEGER DEFAULT 0, " +
                "lapses INTEGER DEFAULT 0, streak INTEGER DEFAULT 0, wrong_count INTEGER DEFAULT 0, " +
                "due_ms INTEGER DEFAULT 0, created INTEGER DEFAULT 0, last_review INTEGER DEFAULT 0, " +
                "PRIMARY KEY(user_id, word))");
        db.execSQL("CREATE TABLE IF NOT EXISTS answers(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, word TEXT, " +
                "qtype TEXT, correct INTEGER, damage INTEGER, ts INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // 表结构在 DB_VERSION=2 已含全部 SRS 字段；旧库直接补建
        onCreate(db);
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

    /** 校验 token 是否为本地用户。 */
    public boolean validToken(String token) {
        if (token == null) return false;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM users WHERE token=?", new String[]{token});
        boolean ok = c.moveToFirst();
        c.close();
        return ok;
    }

    /** 读取全部学习记录。 */
    public Map<String, WordRec> loadProgress() {
        Map<String, WordRec> out = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT word,state,step,graduated,stage,lapses,streak,wrong_count,due_ms " +
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
}
