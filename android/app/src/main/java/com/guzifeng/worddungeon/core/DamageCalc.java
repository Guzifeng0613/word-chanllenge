package com.guzifeng.worddungeon.core;

import org.json.JSONObject;

/**
 * 伤害结算（对应 core.speed_mult / is_fast / combo_mult / revenge_mult / calc_damage）
 */
public class DamageCalc {

    public static double speedMult(String qtype, double timeLimit, double remain) {
        if ("spell".equals(qtype)) {
            double pct = timeLimit > 0 ? remain / timeLimit * 100.0 : 0.0;
            for (double[] s : GameConfig.SPELL_SPEED) {
                if (pct >= s[0]) return s[1];
            }
            return 1.0;
        }
        // 选择/判断：elapsed<=3s→1.5；<=6s→1.2；否则 1.0（V7.0 speedMultFor）
        double elapsed = Math.max(0.0, timeLimit - remain);
        if (elapsed <= GameConfig.FAST_T) return GameConfig.FAST_M;
        if (elapsed <= GameConfig.NORMAL_T) return GameConfig.NORMAL_M;
        return 1.0;
    }

    public static boolean isFast(String qtype, double timeLimit, double remain) {
        if ("spell".equals(qtype)) {
            double pct = timeLimit > 0 ? remain / timeLimit * 100.0 : 0.0;
            return pct >= 60;
        }
        return remain >= timeLimit - GameConfig.FAST_T;
    }

    public static double comboMult(int combo) {
        // V7.0: min(1+0.05×combo, 2.0)，无中间截断
        return Math.min(1.0 + combo * GameConfig.COMBO_STEP, 1.0 + GameConfig.COMBO_CAP);
    }

    /** 复仇倍率：misses = 该词本局已错过次数（>=1）；未错过返回 0（不乘）。 */
    public static double revengeMult(int misses) {
        if (misses <= 0) return 0.0;
        double r = GameConfig.REVENGE_BASE + (misses - 1) * GameConfig.REVENGE_PER_MISS;
        return Math.min(r, GameConfig.REVENGE_CAP);
    }

    /** 结算结果：总伤害 + 分项明细。 */
    public static class Result {
        public final int total;
        public final JSONObject detail;
        Result(int total, JSONObject detail) { this.total = total; this.detail = detail; }
    }

    public static Result calc(String word, String state, int combo, String qtype,
                              double timeLimit, double remain, int runMisses,
                              boolean crit, double critMult, double cardMult, int baseDamage) {
        double base = baseDamage > 0 ? baseDamage : GameConfig.BASE_DAMAGE;
        double sm = speedMult(qtype, timeLimit, remain);
        double cm = comboMult(combo);
        double pm = GameConfig.proficiencyOf(state);
        double rm = revengeMult(runMisses);
        double dmg = base * sm * cm * pm * cardMult;
        if (rm != 0) dmg *= rm;
        if (crit) dmg *= critMult;
        int total = (int) Math.ceil(dmg);

        JSONObject detail = new JSONObject();
        try {
            detail.put("base", (int) base);
            detail.put("speed", Math.round(sm * 100.0) / 100.0);
            detail.put("combo", Math.round(cm * 100.0) / 100.0);
            detail.put("proficiency", pm);
            detail.put("revenge", rm == 0 ? 0 : Math.round(rm * 100.0) / 100.0);
            detail.put("crit", crit);
            detail.put("total", total);
        } catch (Exception e) {
            // 内部结构固定
        }
        return new Result(total, detail);
    }
}
