package com.guzifeng.worddungeon.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 题目（对应后端 draw 返回的 q）
 * 序列化为前端 API 模式所需字段：qtype/word/pos/mean/review/step/graduated/run_state
 * 以及 timeLimit/options/correct/judgeShown。
 */
public class Quiz {
    public String qtype;
    public String word;
    public String pos;
    public String mean;
    public int t;
    public String[] b;
    public boolean review;
    public int step;
    public boolean graduated;
    public int stage;
    public int wrongCount;
    public boolean revEngaged;
    public RunState runState;

    public List<String> options;
    public String correct;
    public String judgeShown;
    public int timeLimit;
    public String quickLevel;

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("qtype", qtype);
            o.put("word", word);
            o.put("pos", pos);
            o.put("mean", mean);
            o.put("t", t);
            if (b != null) {
                JSONArray barr = new JSONArray();
                for (String s : b) barr.put(s);
                o.put("b", barr);
            }
            o.put("review", review);
            o.put("step", step);
            o.put("graduated", graduated);
            o.put("stage", stage);
            o.put("wrongCount", wrongCount);
            o.put("revEngaged", revEngaged);
            o.put("timeLimit", timeLimit);
            o.put("correct", correct);
            if (options != null) {
                JSONArray arr = new JSONArray();
                for (String s : options) arr.put(s);
                o.put("options", arr);
            }
            if (judgeShown != null) o.put("judgeShown", judgeShown);
            if (quickLevel != null) o.put("quickLevel", quickLevel);
            if (runState != null) o.put("run_state", runState.toJson());
        } catch (Exception e) {
            // 内部结构固定
        }
        return o;
    }
}
