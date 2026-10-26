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
        if (remain >= timeLimit - GameConfig.FAST_T) return GameConfig.FAST_M;
        return GameConfig.NORMAL_M;
    }

    public static boolean isFast(String qtype, double timeLimit, double remain) {
        if ("spell".equals(qtype)) {
            double pct = timeLimit > 0 ? remain / timeLimit * 100.0 : 0.0;
            return pct >= 60;
        }
        return remain >= timeLimit - GameConfig.FAST_T;
    }

    public static double comboMult(int combo) {
        return 1.0 + Math.min(combo, 10) * GameConfig.COMBO_STEP;
    }

    public static double revengeMult(int wrongCount) {
        if (wrongCount <= 0) return 0.0;
        double r = GameConfig.REVENGE_BASE + (wrongCount - 1) * GameConfig.REVENGE_PER_MISS;
        return Math.min(r, GameConfig.REVENGE_CAP);
    }

    /** 结算结果：总伤害 + 分项明细。 */
    public static class Result {
        public final int total;
        public final JSONObject detail;
        Result(int total, JSONObject detail) { this.total = total; this.detail = detail; }
    }

    public static Result calc(String word, String state, int combo, String qtype,
                              double timeLimit, double remain, int wrongCount,
                              boolean crit, double critMult, double cardMult) {
        double base = GameConfig.BASE_DAMAGE;
        double sm = speedMult(qtype, timeLimit, remain);
        double cm = comboMult(combo);
        double pm = GameConfig.proficiencyOf(state);
        double rm = revengeMult(wrongCount);
        double dmg = base * sm * cm * pm * cardMult;
        if (rm != 0) dmg *= rm;
        if (crit) dmg *= critMult;
        int total = (int) Math.round(dmg);

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
