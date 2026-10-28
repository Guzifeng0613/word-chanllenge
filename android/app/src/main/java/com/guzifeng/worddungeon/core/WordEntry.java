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
