package com.guzifeng.worddungeon.core;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 词库（对应 core.WB_BANK / WMAP / GROUPS）
 * 从 assets/data/wordbooks.json 加载全部词条，提供按词书/难度查询与干扰项生成。
 */
public class WordBank {
    private static WordBank instance;

    public final List<WordEntry> words = new ArrayList<>();
    public final Map<String, WordEntry> byWord = new HashMap<>();
    public final List<List<String>> groups = new ArrayList<>();
    public JSONObject meta = new JSONObject();

    public final Random rnd = new Random();

    private WordBank() {}

    public static synchronized WordBank get() {
        if (instance == null) instance = new WordBank();
        return instance;
    }

    public static WordBank init(Context ctx) {
        WordBank bank = get();
        if (bank.words.isEmpty()) {
            bank.load(ctx.getAssets());
        }
        return bank;
    }

    private static String readAsset(AssetManager am, String path) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = am.open(path);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        } catch (Exception e) {
            throw new RuntimeException("读取词库失败: " + path, e);
        }
        return sb.toString();
    }

    private void load(AssetManager am) {
        parse(readAsset(am, "data/wordbooks.json"), readAsset(am, "data/confuse-groups.json"));
    }

    /** 纯 JVM 测试入口：从文件系统目录加载（不依赖 Android AssetManager）。 */
    public static WordBank loadForTest(String dir) {
        WordBank bank = get();
        if (bank.words.isEmpty()) {
            try {
                byte[] wb = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(dir, "wordbooks.json"));
                byte[] gp = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(dir, "confuse-groups.json"));
                bank.parse(new String(wb, StandardCharsets.UTF_8), new String(gp, StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return bank;
    }

    void parse(String wordbooksJson, String groupsJson) {
        try {
            JSONObject root = new JSONObject(wordbooksJson);
            meta = root.optJSONObject("meta");
            JSONArray arr = root.optJSONArray("words");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                WordEntry e = new WordEntry();
                e.w = o.getString("w");
                e.p = o.optString("p", "");
                e.m = o.optString("m", "");
                e.t = o.optInt("t", 1);
                e.f = o.optInt("f", 999999);
                JSONArray bs = o.optJSONArray("b");
                if (bs != null) {
                    for (int j = 0; j < bs.length(); j++) e.b.add(bs.getString(j));
                }
                words.add(e);
                byWord.put(e.w, e);
            }
            // 易混组
            try {
                JSONArray g = new JSONArray(groupsJson);
                for (int i = 0; i < g.length(); i++) {
                    List<String> row = new ArrayList<>();
                    JSONArray r = g.getJSONArray(i);
                    for (int j = 0; j < r.length(); j++) row.add(r.getString(j));
                    groups.add(row);
                }
            } catch (Exception ignore) {
                // 易混组缺失不影响核心出题
            }
        } catch (Exception e) {
            throw new RuntimeException("解析词库失败", e);
        }
    }

    public WordEntry entry(String word) {
        return byWord.get(word);
    }

    public List<WordEntry> wordsByTier(int tier) {
        List<WordEntry> out = new ArrayList<>();
        for (WordEntry e : words) if (e.t == tier) out.add(e);
        return out;
    }

    public List<WordEntry> wordsByBook(String book) {
        return wordsByBook(book, 0);
    }

    public List<WordEntry> wordsByBook(String book, int difficulty) {
        List<WordEntry> out = new ArrayList<>();
        for (WordEntry e : words) {
            if (!e.inBook(book)) continue;
            if (difficulty > 0 && e.t != difficulty) continue;
            out.add(e);
        }
        return out;
    }

    public List<String> wordStringsByBook(String book) {
        List<String> out = new ArrayList<>();
        for (WordEntry e : words) if (e.inBook(book)) out.add(e.w);
        return out;
    }

    private List<String> confuseGroup(String word) {
        for (List<String> g : groups) if (g.contains(word)) return g;
        return null;
    }

    private List<String> samePos(String word) {
        WordEntry cur = byWord.get(word);
        String pos = cur == null ? null : cur.p;
        List<String> out = new ArrayList<>();
        if (pos == null || pos.isEmpty()) return out;
        for (WordEntry e : words) {
            if (e.w.equals(word)) continue;
            if (pos.equals(e.p)) out.add(e.w);
        }
        return out;
    }

    /** 英文干扰词（cn2en 用）：优先易混组，再补同词性词。 */
    public List<String> distractorWords(String word, int n) {
        List<String> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        List<String> g = confuseGroup(word);
        if (g != null) {
            for (String w : g) {
                if (!w.equals(word) && byWord.containsKey(w) && !seen.contains(w)) {
                    out.add(w); seen.add(w);
                }
            }
        }
        for (String w : samePos(word)) {
            if (!seen.contains(w)) { out.add(w); seen.add(w); }
            if (out.size() >= n) break;
        }
        Collections.shuffle(out, rnd);
        return out.subList(0, Math.min(n, out.size()));
    }

    /** 释义干扰项（en2cn 用）：优先易混组，再补同词性词，返回释义。 */
    public List<String> makeDistractors(String word, int n) {
        List<String> src = distractorWords(word, n);
        List<String> out = new ArrayList<>();
        for (String w : src) out.add(byWord.get(w).m);
        return out;
    }
}
