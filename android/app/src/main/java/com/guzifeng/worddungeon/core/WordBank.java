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

    /**
     * 出题候选池（V7.0 buildQuestion/drawQuickLocal）：词书范围内、排除本词、
     * 默认排除同组易混词；shuffle 后的顺序。
     */
    private List<String> layerPool(String word, String book) {
        List<String> g = confuseGroup(word);
        List<String> pool = new ArrayList<>();
        for (WordEntry e : words) {
            if (e.w.equals(word)) continue;
            if (book != null && !e.inBook(book)) continue;
            if (g != null && g.contains(e.w)) continue;
            pool.add(e.w);
        }
        Collections.shuffle(pool, rnd);
        return pool;
    }

    /**
     * 英文干扰词（cn2en 用）：confuse 敌人优先取同组，再从词书池补齐 n 个不重复词。
     */
    public List<String> distractorWords(String word, int n, String book, boolean confuseEnemy) {
        List<String> out = new ArrayList<>();
        if (confuseEnemy) {
            List<String> g = confuseGroup(word);
            if (g != null) {
                for (String w : g) {
                    if (!w.equals(word) && byWord.containsKey(w) && !out.contains(w)) out.add(w);
                }
            }
        }
        for (String x : layerPool(word, book)) {
            if (out.size() >= n) break;
            if (!out.contains(x)) out.add(x);
        }
        return out.subList(0, Math.min(n, out.size()));
    }

    /** 释义干扰项（en2cn 用）：同语义去重，返回释义文本。 */
    public List<String> makeDistractors(String word, int n, String book, boolean confuseEnemy) {
        List<String> out = new ArrayList<>();
        if (confuseEnemy) {
            List<String> g = confuseGroup(word);
            if (g != null) {
                for (String w : g) {
                    if (!w.equals(word) && byWord.containsKey(w)) {
                        String m = byWord.get(w).m;
                        if (!out.contains(m)) out.add(m);
                    }
                }
            }
        }
        for (String x : layerPool(word, book)) {
            if (out.size() >= n) break;
            String m = byWord.get(x).m;
            if (!out.contains(m)) out.add(m);
        }
        return out.subList(0, Math.min(n, out.size()));
    }

    /** judge 错题的错误释义：优先同组兄弟词，否则词书池随机（排除同组）。 */
    public String judgeWrongMean(String word, String book) {
        List<String> g = confuseGroup(word);
        if (g != null) {
            for (String w : g) {
                if (!w.equals(word) && byWord.containsKey(w)) return byWord.get(w).m;
            }
        }
        List<String> pool = layerPool(word, book);
        if (pool.isEmpty()) return byWord.get(word).m;
        return byWord.get(pool.get(rnd.nextInt(pool.size()))).m;
    }
}
