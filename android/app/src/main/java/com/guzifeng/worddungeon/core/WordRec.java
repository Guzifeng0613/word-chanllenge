package com.guzifeng.worddungeon.core;

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
}
