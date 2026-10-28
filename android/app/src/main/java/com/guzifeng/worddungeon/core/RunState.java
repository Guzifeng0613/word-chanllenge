package com.guzifeng.worddungeon.core;

import org.json.JSONObject;
import org.json.JSONArray;

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
    /** 本局出题次数（V12.1 单局去重用） */
    public Map<String, Integer> drawn = new HashMap<>();
    /** 本局已答对过的词（V12.1 冷却去重：答对词不再短期回归，除非候选池耗尽） */
    public Map<String, Boolean> correctOnce = new HashMap<>();
    /** V12.7.14 局内进阶队列：答对且未毕业的词排队，在同一局内出更高阶梯题（识→认→写→拼） */
    public java.util.List<String> advQueue = new java.util.ArrayList<>();
    /** V13.1 错词消化闭环：距上一次错词穿插已过的普通题数；达到 WRONG_REVIEW_GAP 且有错词待消化时穿插 1 个错词，随后归零。 */
    public int replayGap = 0;
    /** V13.4 一词多形：距上一次 form 题已过的普通题数；达到 FORM_REVIEW_GAP 且抽中概率时才偶发出 1 个，随后归零。 */
    public int formGap = 0;
    /** V13.4 一词多形：本局已考察过的「词|形名」（同一词的同一形本局不重复考）。 */
    public Map<String, Boolean> formSeen = new HashMap<>();
    /** V13.5 英英模式：距上一次 en2en 题已过的普通题数；达到 EN2EN_REVIEW_GAP 且抽中概率时才偶发出 1 个，随后归零。 */
    public int en2enGap = 0;

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
        JSONObject dr = o.optJSONObject("drawn");
        if (dr != null) {
            for (java.util.Iterator<String> it = dr.keys(); it.hasNext(); ) {
                String k = it.next();
                r.drawn.put(k, dr.optInt(k));
            }
        }
        JSONObject co = o.optJSONObject("correct_once");
        if (co != null) {
            for (java.util.Iterator<String> it = co.keys(); it.hasNext(); ) {
                String k = it.next();
                r.correctOnce.put(k, co.optBoolean(k));
            }
        }
        JSONArray aq = o.optJSONArray("adv_queue");
        if (aq != null) {
            try {
                for (int i = 0; i < aq.length(); i++) r.advQueue.add(aq.getString(i));
            } catch (Exception ignored) {}
        }
        r.replayGap = o.optInt("replay_gap", 0);
        r.formGap = o.optInt("form_gap", 0);
        r.en2enGap = o.optInt("en2en_gap", 0);
        JSONObject fs = o.optJSONObject("form_seen");
        if (fs != null) {
            for (java.util.Iterator<String> it = fs.keys(); it.hasNext(); ) {
                String k = it.next();
                r.formSeen.put(k, fs.optBoolean(k));
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
            JSONObject dr = new JSONObject();
            for (Map.Entry<String, Integer> e : drawn.entrySet()) dr.put(e.getKey(), e.getValue());
            o.put("drawn", dr);
            JSONObject co = new JSONObject();
            for (Map.Entry<String, Boolean> e : correctOnce.entrySet()) co.put(e.getKey(), e.getValue());
            o.put("correct_once", co);
            o.put("adv_queue", new JSONArray(advQueue));
            o.put("replay_gap", replayGap);
            o.put("form_gap", formGap);
            o.put("en2en_gap", en2enGap);
            JSONObject fs = new JSONObject();
            for (Map.Entry<String, Boolean> e : formSeen.entrySet()) fs.put(e.getKey(), e.getValue());
            o.put("form_seen", fs);
        } catch (Exception e) {
            // 内部结构固定
        }
        return o;
    }
}
