package com.guzifeng.worddungeon.core;

/**
 * 游戏数值配置（对应 server/core.py 的 CONFIG，与 GDD 同一版本）
 * 全部为权威常量；平衡性调整只改这里。
 */
public final class GameConfig {
    private GameConfig() {}

    // 玩家
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

    // 心流档位（P0-1）：≥5 基础心流 / ≥8 高涨 / ≥12 沸腾
    public static final int FLOW_BASE_COMBO = 5;
    public static final int FLOW_HIGH_COMBO = 8;
    public static final int FLOW_MAX_COMBO = 12;
    public static final int FLOW_SOUL_BASE = 1;       // ≥5 每答对词魂 +1
    public static final int FLOW_SOUL_HIGH = 2;       // ≥8 每答对词魂 +2
    public static final int FLOW_SOUL_MAX = 3;         // ≥12 每答对词魂 +3
    public static final double FLOW_MAX_CRIT = 0.05;   // ≥12 暴击率 +5%

    // 凝神复仇（P0-2）：连错2次后下一题答对，伤害额外 +25%（复用复仇紫乘区）
    public static final double FOCUS_REVENGE_BONUS = 0.25;

    // 熟练度
    public static final double PROF_NEW = 1.0;
    public static final double PROF_LEARNING = 1.05;
    public static final double PROF_FAMILIAR = 1.2;
    public static final double PROF_MASTERED = 1.4;

    // 复仇
    public static final double REVENGE_BASE = 1.5;
    public static final double REVENGE_PER_MISS = 0.25;
    public static final double REVENGE_CAP = 2.0;

    // 自适应 θ
    public static final double THETA_MIN = 1.0;
    public static final double THETA_MAX = 3.0;
    public static final double THETA_FAST_GAIN = 0.10;
    public static final double THETA_GAIN = 0.05;
    public static final double THETA_PENALTY = 0.10;
    public static final double ADAPT_MATCH = 0.70;
    public static final double ADAPT_STRETCH = 0.15;

    // 每日新词上限
    public static final int NEW_CAP = 150;

    // 无尽
    public static final double ENDLESS_HP_STEP = 0.15;
    public static final double ENDLESS_ATK_STEP = 0.10;
    public static final int ENDLESS_TIER_EVERY = 3;
    public static final int ENDLESS_CHAMPION_EVERY = 5;
    public static final double ENDLESS_CHAMPION_HP = 1.25;
    public static final int ENDLESS_CHAMPION_ATK = 3;

    // 快刷档位
    public static final String[] QUICK_LEVELS = {"normal", "hard", "hell"};
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
    public static final double[] QUICK_FLOOR = {0.05, 0.02, 0.01};   // V12.7.14 保底按档收紧，强化高频优先
    public static final double[] QUICK_ADV_PICK = {0.30, 0.35, 0.40};   // V12.7.14 局内进阶队列抽取概率
    public static final double[] QUICK_NEW_PICK = {0.50, 0.55, 0.55};   // V12.7.14 新词路径概率（档越高新词越多）
    public static final double[] QUICK_NEW_BOOST = {1.30, 1.40, 1.50};  // V12.7.14 全池新词权重加成
    public static final int QUICK_ADV_QUEUE_MAX = 12;
    public static final int[] QUICK_ENEMY_HP = {30, 45, 60};
    public static final int QUICK_CHOICE_T = 8;

    // SM-2 间隔梯（分钟）
    public static final int[] SRS_LADDER = {10, 1440, 4320, 10080, 23040, 43200};

    // 题型阶梯（题型由 step/stage 决定；V7.0 的 C.question.pool 权重池为死代码，不实现）
    public static final String[] TYPE_LADDER = {"judge", "en2cn", "cn2en", "spell"};
    public static final int[] STEP_DELAY_MIN = {2, 3, 5};
    public static final String[] GRAD_REVIEW_TYPE = {"cn2en", "spell", "en2cn", "cn2en", "en2cn", "spell"};

    // 关卡（远征）——V12.0：3 层改 5 层，每层难度阶梯递增。
    // 下列数组均按下标 layer-1 索引（layer 从 1 起），所有取值必须单调不减/递增。
    public static final int LAYER_COUNT = 5;
    /** 每层词难度档下限（单调不减）：{热身,精英,词难断层,精英殿卫,深渊王座} */
    public static final int[] CAMPAIGN_TIER = {1, 2, 3, 3, 3};
    /** 每层守门者之前的普通小怪数量 */
    public static final int[] CAMPAIGN_MINIONS = {2, 2, 3, 2, 1};
    /** 层末守门者类型：none / elite / boss */
    public static final String[] CAMPAIGN_CLIMBER = {"none", "elite", "none", "elite", "boss"};
    /** 非拼写选择题限时（秒），逐层收紧 */
    public static final int[] CAMPAIGN_CHOICE_TIME = {8, 7, 6, 5, 5};
    /** 在基础题型之上，本题被升级为拼写题的概率（后期拼写为主） */
    public static final double[] CAMPAIGN_SPELL_BIAS = {0.0, 0.10, 0.25, 0.40, 0.60};
    /** 拼写题额外秒数（在 SPELL_BASE+PER_CHAR*len 之上） */
    public static final int[] CAMPAIGN_SPELL_TIME_ADD = {0, 0, 0, 1, 2};
    /** 每层播报名 */
    public static final String[] CAMPAIGN_LAYER_NAME = {"热身回廊", "精英哨站", "词难断层", "精英殿卫", "深渊王座"};

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
