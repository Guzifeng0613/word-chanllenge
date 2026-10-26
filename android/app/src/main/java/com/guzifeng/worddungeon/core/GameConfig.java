package com.guzifeng.worddungeon.core;

/**
 * 游戏数值配置（对应 server/core.py 的 CONFIG，与 GDD 同一版本）
 * 全部为权威常量；平衡性调整只改这里。
 */
public final class GameConfig {
    private GameConfig() {}

    // 玩家
    public static final int MAX_HP = 100;
    public static final int BASE_DAMAGE = 10;

    // 速度（非拼写题）
    public static final double FAST_T = 3.0;
    public static final double NORMAL_T = 6.0;
    public static final double FAST_M = 1.5;
    public static final double NORMAL_M = 1.2;

    // 拼写：基础限时 + 每字母秒数；速度档（剩余比例% → 倍率）
    public static final int SPELL_BASE = 4;
    public static final int SPELL_PER_CHAR = 2;
    public static final double[][] SPELL_SPEED = {
            {60, 2.0}, {40, 1.6}, {25, 1.25}, {0, 1.0}};

    // 连击
    public static final double COMBO_STEP = 0.05;
    public static final double COMBO_CAP = 1.0;

    // 熟练度
    public static final double PROF_NEW = 1.0;
    public static final double PROF_LEARNING = 1.05;
    public static final double PROF_FAMILIAR = 1.2;
    public static final double PROF_MASTERED = 1.4;

    // 复仇
    public static final double REVENGE_BASE = 1.5;
    public static final double REVENGE_PER_MISS = 0.25;
    public static final double REVENGE_CAP = 2.0;

    // 复习之泉
    public static final int SPRING_REVIEW_COUNT = 3;
    public static final int SPRING_HEAL = 2;
    public static final int SPRING_REVIEW_HEAL = 2;

    // 自适应 θ
    public static final double THETA_INIT = 1.5;
    public static final double THETA_MIN = 1.0;
    public static final double THETA_MAX = 3.0;
    public static final double THETA_FAST_GAIN = 0.10;
    public static final double THETA_GAIN = 0.05;
    public static final double THETA_PENALTY = 0.10;
    public static final double ADAPT_MATCH = 0.70;
    public static final double ADAPT_STRETCH = 0.15;
    public static final double ADAPT_CONSOLIDATE = 0.15;
    public static final double TARGET_RATE = 0.85;

    // 每日新词上限
    public static final int NEW_CAP = 20;

    // 无尽
    public static final double ENDLESS_HP_STEP = 0.15;
    public static final double ENDLESS_ATK_STEP = 0.10;
    public static final int ENDLESS_TIER_EVERY = 3;
    public static final int ENDLESS_TIME_START = 4;
    public static final int ENDLESS_TIME_EVERY = 3;
    public static final int ENDLESS_TIME_MAX = 3;
    public static final int ENDLESS_CHAMPION_EVERY = 5;
    public static final double ENDLESS_CHAMPION_HP = 1.25;
    public static final int ENDLESS_CHAMPION_ATK = 3;

    // 快刷档位
    public static final String[] QUICK_LEVELS = {"normal", "hard", "hell"};
    public static final String QUICK_LEVEL_NAME_NORMAL = "普通";
    public static final String QUICK_LEVEL_NAME_HARD = "困难";
    public static final String QUICK_LEVEL_NAME_HELL = "地狱";
    public static final double[] QUICK_FREQ_POW = {1.0, 0.5, 0.2};
    public static final double[] QUICK_SPELL_BIAS = {0.0, 0.35, 0.75};
    public static final int[] QUICK_SPELL_TIME_ADD = {0, 1, 3};
    public static final double[] QUICK_BASE_FREQ = {1.0, 0.6, 0.3};
    public static final double[] QUICK_LEVEL_FACTOR = {1.0, 1.5, 2.0};
    public static final double[] QUICK_QTYPE_FACTOR = {1.0, 1.0, 1.3, 1.6}; // judge/en2cn/cn2en/spell
    public static final double QUICK_ERROR_COEFF = 1.0;
    public static final double QUICK_PASS_SUPPRESS_HARD = 0.08;
    public static final double QUICK_PASS_SUPPRESS_HELL = 0.05;
    public static final double QUICK_CAROUSEL = 0.03;
    public static final int QUICK_FREQ_REF = 10000;
    public static final double QUICK_NO_FREQ_SCORE = 0.05;
    public static final double QUICK_FLOOR = 0.05;
    public static final int[] QUICK_ENEMY_HP = {30, 45, 60};
    public static final int QUICK_CHOICE_T = 8;

    // SM-2 间隔梯（分钟）
    public static final int[] SRS_LADDER = {10, 1440, 4320, 10080, 23040, 43200};

    // 题型阶梯
    public static final String[] TYPE_LADDER = {"judge", "en2cn", "cn2en", "spell"};
    public static final int[] STEP_DELAY_MIN = {2, 3, 5};
    public static final String[] GRAD_REVIEW_TYPE = {"cn2en", "spell", "en2cn", "cn2en", "en2cn", "spell"};

    // 题型分布（旧，保留）
    public static final double POOL_EN2CN = 0.55;
    public static final double POOL_CN2EN = 0.15;
    public static final double POOL_SPELL = 0.10;
    public static final double POOL_JUDGE = 0.20;

    // 关卡（远征）
    public static final int LAYER_COUNT = 3;
    public static final int QUESTION_TIMEOUT = 12;

    public static double proficiencyOf(String state) {
        if (state == null) return PROF_NEW;
        switch (state) {
            case "LEARNING": return PROF_LEARNING;
            case "FAMILIAR": return PROF_FAMILIAR;
            case "MASTERED": return PROF_MASTERED;
            default: return PROF_NEW;
        }
    }
}
