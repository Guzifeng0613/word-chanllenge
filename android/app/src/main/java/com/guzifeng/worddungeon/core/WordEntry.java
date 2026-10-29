package com.guzifeng.worddungeon.core;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 词条模型（对应 wordbooks.json 中 words[i]：{w,p,m,t,b,f,x?}）
 * w 单词；p 词性；m 常用释义；t 难度档 1/2/3；b 所属词书；f 词频排名（1=最高频）。
 * x 一词多形（V13.4）：{plural/past/pastp/ing/comparative/superlative/third}，缺失的形无键。
 */
public class WordEntry {
    public String w;
    public String p;
    public String m;
    public int t;
    public List<String> b = new ArrayList<>();
    public int f;
    /** 词形变化表（V13.4）；无变形数据时为 null。键：plural/past/pastp/ing/comparative/superlative/third */
    public JSONObject x;
    /** 英英释义（V13.5，精简 1 个核心义项）；无则空串。 */
    public String d2 = "";
    /** V13.6 核心词标记：o=牛津3000(1=是)；c=柯林斯星级1-5(越大越常用)。0/缺失=无标记。 */
    public int o = 0;
    public int c = 0;
    /** V13.10 熟词生义：{词性短码: [义项…]}，只收非首词性的生义；无则 null。 */
    public JSONObject s;

    public WordEntry() {}

    public WordEntry(String w, String p, String m, int t, List<String> b, int f) {
        this.w = w;
        this.p = p == null ? "" : p;
        this.m = m;
        this.t = t;
        if (b != null) this.b = b;
        this.f = f;
    }

    public boolean inBook(String book) {
        return b.contains(book);
    }
}
