package com.guzifeng.worddungeon.core;

/**
 * 游戏数值配置（对应 server/core.py 的 CONFIG，与 GDD 同一版本）
 * 全部为权威常量；平衡性调整只改这里。
 */
public final class GameConfig {
    private GameConfig() {}

    // 玩家
    public static final int BASE_DAMAGE = 10;

    // ── V13.0 玩家身份：称号 4 阶（按玩家等级）、火花 4 阶（按连续打卡）──
    /** 称号各阶所需玩家等级（0-based 对应名称）：词灵使/词灵行者/词灵领主/词灵圣者 */
    public static final int[] TITLE_LV = {1, 3, 6, 10};
    public static final String[] TITLE_NAMES = {"词灵使", "词灵行者", "词灵领主", "词灵圣者"};
    /** 火花各阶所需连续打卡天数（0-based）：灰烬/星火/烈火/圣焰 */
    public static final int[] SPARK_STREAK = {0, 3, 7, 30};
    public static final String[] SPARK_NAMES = {"灰烬", "星火", "烈火", "圣焰"};

    /** 称号阶段（0-based）：末阶 Lv10 门槛跨度为前阶 2.5 倍，中两阶前移易拿 */
    public static int titleStage(int level) {
        int s = 0;
        for (int i = 0; i < TITLE_LV.length; i++) if (level >= TITLE_LV[i]) s = i;
        return s;
    }

    /** 火花阶段（0-based）：连续打卡 3/7/30 天逐级点亮 */
    public static int sparkStage(int streak) {
        int s = 0;
        for (int i = 0; i < SPARK_STREAK.length; i++) if (streak >= SPARK_STREAK[i]) s = i;
        return s;
    }

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

    // ── V13.1 错词消化闭环（仅追加，不改动既有数值）──
    /** 主循环错词穿插比例上限：每连续 WRONG_REVIEW_GAP 个普通题之后，至多穿插 1 个本局错词（即每 GAP+1 题 ≤1 个错词），新词仍按既有节奏出现。 */
    public static final int WRONG_REVIEW_GAP = 4;
    /** 顽固度阈值：累计 wrongCount >= WRONG_STUBBORN（即 gradeOf>=2）为「顽固」，仅 1 次为「偶发」；顽固词在错词重现队列中排序更靠前。 */
    public static final int WRONG_STUBBORN = 2;

    // ── V13.4 一词多形·词形变化家族（仅追加；只考察已毕业词的复习题，不新增打断槽位）──
    /** form 题最小间隔：每连续 FORM_REVIEW_GAP 个普通题之后，才允许偶发 1 个 form 题（替换一道已到期的毕业词复习题）。 */
    public static final int FORM_REVIEW_GAP = 9;
    /** 达到间隔后仍要掷骰才升级为 form 题：约一半的候选复习题被升级，另一半保持原复习题型。 */
    public static final double FORM_QUESTION_PROB = 0.5;
    // ── V13.5 英英模式（默认关；开启后才生效，关闭零残留）──
    /** en2en 最小间隔：每连续 EN2EN_REVIEW_GAP 个普通题之后，才偶发 1 道英英题（替换一道已学词复习题）。 */
    public static final int EN2EN_REVIEW_GAP = 8;
    /** 达到间隔后仍要掷骰才升级为 en2en：约一半的候选复习题被升级，避免一上来全是英英题。 */
    public static final double EN2EN_PROB = 0.4;
    /** en2en 题干较长，基础限时略宽于普通选择题（秒）。V13.25: 9→13，+4s。 */
    public static final int EN2EN_TIME = 13;
    // ── V13.10 熟词生义（看中文生义选单词）：比 en2en 更轻量 ──
    /** sense 最小间隔：每连续 SENSE_GAP 个普通题之后才偶发 1 道熟词生义题。 */
    public static final int SENSE_GAP = 10;
    /** 达到间隔后仍要掷骰才升级：约 0.3 概率。 */
    public static final double SENSE_PROB = 0.3;

    /** 形名 → 中文标签（展示用；pastp 不单独出题，仅入库）。 */
    public static String formLabel(String key) {
        if (key == null) return "变形";
        switch (key) {
            case "plural":      return "复数";
            case "past":        return "过去式";
            case "ing":         return "现在分词";
            case "third":       return "第三人称单数";
            case "comparative": return "比较级";
            case "superlative": return "最高级";
            default:            return "变形";
        }
    }

    /** 按词性返回该词可考察的形名候选（与 build_vocab.py POS_ALLOW 的考察子集一致：名词考复数、动词考过去式/-ing/三单、形/副考比较级/最高级）。 */
    public static java.util.List<String> formKeysForPos(String pos) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (pos == null) return out;
        String p = pos.toLowerCase();
        if (p.startsWith("n")) {
            out.add("plural");
        } else if (p.startsWith("v")) {
            out.add("past"); out.add("ing"); out.add("third");
        } else if (p.startsWith("adj") || p.startsWith("adv")) {
            out.add("comparative"); out.add("superlative");
        }
        return out;
    }

    // ── V13.8 不规则形优先：运行时规则判定，零词库体积 ──
    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }
    /** 辅音+ y 结尾（y 前是辅音）。 */
    private static boolean endsConsY(String w) {
        return w.endsWith("y") && w.length() >= 2 && !isVowel(w.charAt(w.length() - 2));
    }
    /** 重读闭音节近似：长度≥3，末三位 C-V-C，且末尾不是 w/x/y（不双写）。 */
    private static boolean isCvc(String w) {
        if (w.length() < 3) return false;
        char a = w.charAt(w.length() - 3), b = w.charAt(w.length() - 2), c = w.charAt(w.length() - 1);
        if (c == 'w' || c == 'x' || c == 'y') return false;
        return !isVowel(a) && isVowel(b) && !isVowel(c);
    }
    private static boolean endsSibilant(String w) {
        return w.endsWith("s") || w.endsWith("x") || w.endsWith("z") || w.endsWith("ch") || w.endsWith("sh");
    }

    /**
     * 判定变形是否「不规则」（不能由标准规则推出）。
     * 保守口径：拿不准就判不规则（避免把 went 误判成规则）；自指/空值不算不规则。
     * 返回 true = 不规则（应优先考）。
     */
    public static boolean isIrregular(String base, String key, String form) {
        if (base == null || form == null) return false;
        String b = base.toLowerCase().trim();
        String f = form.toLowerCase().trim();
        if (b.isEmpty() || f.isEmpty() || b.equals(f)) return false;
        switch (key == null ? "" : key) {
            case "plural":
            case "third": {
                // +s；嘶音结尾 +es；辅音+y → ies
                if (f.equals(b + "s")) return false;
                if (endsSibilant(b) && f.equals(b + "es")) return false;
                if (b.endsWith("o") && f.equals(b + "es")) return false;       // go→goes
                if (endsConsY(b) && f.equals(b.substring(0, b.length() - 1) + "ies")) return false;
                return true;
            }
            case "past":
            case "pastp": {
                if (f.equals(b + "ed")) return false;
                if (b.endsWith("e") && f.equals(b + "d")) return false;
                if (endsConsY(b) && f.equals(b.substring(0, b.length() - 1) + "ied")) return false;
                if (isCvc(b) && f.equals(b + b.charAt(b.length() - 1) + "ed")) return false;
                return true;
            }
            case "ing": {
                if (f.equals(b + "ing")) return false;
                if (b.endsWith("e") && f.equals(b.substring(0, b.length() - 1) + "ing")) return false; // make→making
                if (isCvc(b) && f.equals(b + b.charAt(b.length() - 1) + "ing")) return false;           // run→running
                return true;
            }
            case "comparative":
            case "superlative": {
                String suf = "superlative".equals(key) ? "est" : "er";
                if (f.equals(b + suf)) return false;
                if (b.endsWith("e") && f.equals(b.substring(0, b.length() - 1) + suf)) return false;   // nice→nicer
                if (endsConsY(b) && f.equals(b.substring(0, b.length() - 1) + "i" + suf)) return false;  // happy→happier
                if (isCvc(b) && f.equals(b + b.charAt(b.length() - 1) + suf)) return false;               // big→bigger
                return true;
            }
            default:
                return false;
        }
    }
}
