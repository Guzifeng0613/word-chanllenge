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
    /** V13.1：本题是否为「错词消化闭环」按比例穿插重现的本局错词（答对即触发「已攻克」反馈） */
    public boolean wrongReplay;
    public RunState runState;

    public List<String> options;
    public String correct;
    public String judgeShown;
    public int timeLimit;
    public String quickLevel;
    /** V13.4 一词多形：本次考察的形名键（past/ing/third/plural/comparative/superlative）与中文展示标签。 */
    public String formKey;
    public String formLabel;
    /** V13.5 英英释义题干（en2en 题型展示）。 */
    public String d2;
    /** V13.10 熟词生义题干（sense 题型展示：一个非首词性的中文义项）。 */
    public String senseMean = "";

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
            o.put("wrongReplay", wrongReplay);
            o.put("timeLimit", timeLimit);
            o.put("correct", correct);
            if (options != null) {
                JSONArray arr = new JSONArray();
                for (String s : options) arr.put(s);
                o.put("options", arr);
            }
            if (judgeShown != null) o.put("judgeShown", judgeShown);
            if (quickLevel != null) o.put("quickLevel", quickLevel);
            if (formKey != null) o.put("formKey", formKey);
            if (formLabel != null) o.put("formLabel", formLabel);
            if (d2 != null && !d2.isEmpty()) o.put("d2", d2);
            if (senseMean != null && !senseMean.isEmpty()) o.put("senseMean", senseMean);
            if (runState != null) o.put("run_state", runState.toJson());
        } catch (Exception e) {
            // 内部结构固定
        }
        return o;
    }
}
