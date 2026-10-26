package com.guzifeng.worddungeon.core;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 单局状态（对应 run_state：wrong / seen）
 * wrong：本局答错的词；seen：该词答错后已重现的次数（上限 1 次重现）。
 */
public class RunState {
    public Map<String, Boolean> wrong = new HashMap<>();
    public Map<String, Integer> seen = new HashMap<>();

    public RunState() {}

    public static RunState fromJson(JSONObject o) {
        RunState r = new RunState();
        if (o == null) return r;
        JSONObject w = o.optJSONObject("wrong");
        if (w != null) {
            for (java.util.Iterator<String> it = w.keys(); it.hasNext(); ) {
                String k = it.next();
                r.wrong.put(k, w.optBoolean(k));
            }
        }
        JSONObject s = o.optJSONObject("seen");
        if (s != null) {
            for (java.util.Iterator<String> it = s.keys(); it.hasNext(); ) {
                String k = it.next();
                r.seen.put(k, s.optInt(k));
            }
        }
        return r;
    }

    /** 单局重现上限：已答错、且已用掉唯一一次重现（seen>=1）→ 本局不再抽。 */
    public boolean cap(String w) {
        if (!Boolean.TRUE.equals(wrong.get(w))) return true;
        return seen.getOrDefault(w, 0) < 1;
    }

    public void markWrong(String w) {
        if (!wrong.containsKey(w)) {
            wrong.put(w, true);
            seen.put(w, seen.getOrDefault(w, 0));
        }
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            JSONObject w = new JSONObject();
            for (Map.Entry<String, Boolean> e : wrong.entrySet()) w.put(e.getKey(), e.getValue());
            JSONObject s = new JSONObject();
            for (Map.Entry<String, Integer> e : seen.entrySet()) s.put(e.getKey(), e.getValue());
            o.put("wrong", w);
            o.put("seen", s);
        } catch (Exception e) {
            // 内部结构固定
        }
        return o;
    }
}
