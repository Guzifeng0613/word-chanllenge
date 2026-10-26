package com.guzifeng.worddungeon.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 单词学习记录（对应 core.norm_rec：state/step/graduated/stage/lapses/streak/wrong_count/due）
 * 额外承载快刷字段：quick_pass、wrong_by（档位 × 题型错误计数）。
 * 内部时间统一用毫秒。
 */
public class WordRec {
    public String state = "NEW";
    public int step = 0;
    public boolean graduated = false;
    public int stage = 0;
    public int lapses = 0;
    public int streak = 0;
    public int wrongCount = 0;
    public long due = 0L;

    // 快刷字段
    public boolean quickPass = false;
    // wrong_by[level][qtype] = 次数；level: normal/hard/hell；qtype: judge/en2cn/cn2en/spell
    public Map<String, Map<String, Integer>> wrongBy = new HashMap<>();

    public WordRec() {}

    public static WordRec fromJson(JSONObject o) {
        WordRec r = new WordRec();
        if (o == null) return r;
        r.state = o.optString("state", "NEW");
        r.step = o.optInt("step", 0);
        r.graduated = o.optBoolean("graduated", false);
        r.stage = o.optInt("stage", 0);
        r.lapses = o.optInt("lapses", 0);
        r.streak = o.optInt("streak", 0);
        r.wrongCount = o.optInt("wrong_count", 0);
        r.due = o.optLong("due", 0L);
        r.quickPass = o.optBoolean("quick_pass", false);
        JSONObject wb = o.optJSONObject("wrong_by");
        if (wb != null) {
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
        }
        return r;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("state", state);
            o.put("step", step);
            o.put("graduated", graduated);
            o.put("stage", stage);
            o.put("lapses", lapses);
            o.put("streak", streak);
            o.put("wrong_count", wrongCount);
            o.put("due", due);
            o.put("quick_pass", quickPass);
            JSONObject wb = new JSONObject();
            for (String level : new String[]{"normal", "hard", "hell"}) {
                JSONObject row = new JSONObject();
                Map<String, Integer> m = wrongBy.get(level);
                for (String qt : new String[]{"judge", "en2cn", "cn2en", "spell"}) {
                    row.put(qt, m == null ? 0 : m.getOrDefault(qt, 0));
                }
                wb.put(level, row);
            }
            o.put("wrong_by", wb);
        } catch (JSONException e) {
            // 内部数据结构固定，不会发生
        }
        return o;
    }
}
