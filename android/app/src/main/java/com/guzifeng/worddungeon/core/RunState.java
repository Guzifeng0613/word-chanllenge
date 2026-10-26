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
    /** 本局已以非拼写题型出现过的词（先见过一次，才允许拼写） */
    public Map<String, Boolean> exposed = new HashMap<>();
    /** 该词本局已错过次数（V7.0 G.wrongCount，复仇倍率用） */
    public Map<String, Integer> wrongTimes = new HashMap<>();

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
        JSONObject ex = o.optJSONObject("exposed");
        if (ex != null) {
            for (java.util.Iterator<String> it = ex.keys(); it.hasNext(); ) {
                String k = it.next();
                r.exposed.put(k, ex.optBoolean(k));
            }
        }
        JSONObject wt = o.optJSONObject("wrong_times");
        if (wt != null) {
            for (java.util.Iterator<String> it = wt.keys(); it.hasNext(); ) {
                String k = it.next();
                r.wrongTimes.put(k, wt.optInt(k));
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
        wrongTimes.put(w, wrongTimes.getOrDefault(w, 0) + 1);
    }

    /** 该词本局已错过次数（复仇倍率用）。 */
    public int runMisses(String w) {
        return wrongTimes.getOrDefault(w, 0);
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
            JSONObject ex = new JSONObject();
            for (Map.Entry<String, Boolean> e : exposed.entrySet()) ex.put(e.getKey(), e.getValue());
            o.put("exposed", ex);
            JSONObject wt = new JSONObject();
            for (Map.Entry<String, Integer> e : wrongTimes.entrySet()) wt.put(e.getKey(), e.getValue());
            o.put("wrong_times", wt);
        } catch (Exception e) {
            // 内部结构固定
        }
        return o;
    }
}
