package com.guzifeng.worddungeon.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 记忆与自适应引擎（对应 core：θ 自适应、SM-2 间隔重复、题型阶梯、到期队列）
 */
public class SrsEngine {

    /** 自适应抽题结果（难度档 + 桶名） */
    public static class TierPick {
        public final int tier;
        public final String bucket;
        TierPick(int tier, String bucket) { this.tier = tier; this.bucket = bucket; }
    }

    // ---------------- 错词分级 ----------------
    public static int gradeOf(int wrongCount) {
        if (wrongCount >= 3) return 3;
        if (wrongCount == 2) return 2;
        return 1;
    }

    // ---------------- θ 自适应 ----------------
    public static double updateTheta(double theta, boolean correct, boolean fast) {
        if (correct) theta += fast ? GameConfig.THETA_FAST_GAIN : GameConfig.THETA_GAIN;
        else theta -= GameConfig.THETA_PENALTY;
        if (theta < GameConfig.THETA_MIN) theta = GameConfig.THETA_MIN;
        if (theta > GameConfig.THETA_MAX) theta = GameConfig.THETA_MAX;
        return Math.round(theta * 1000.0) / 1000.0;
    }

    public static TierPick adaptiveTier(double theta) {
        int base = (int) Math.max(1, Math.min(3, Math.round(theta)));
        double r = WordBank.get().rnd.nextDouble();
        if (r < GameConfig.ADAPT_MATCH) return new TierPick(base, "match");
        if (r < GameConfig.ADAPT_MATCH + GameConfig.ADAPT_STRETCH)
            return new TierPick(Math.min(3, base + 1), "stretch");
        return new TierPick(Math.max(1, base - 1), "consolidate");
    }

    // ---------------- 题型阶梯 ----------------
    public static String typeForWord(WordRec rec) {
        if (rec == null) rec = new WordRec();
        if (rec.graduated) return GameConfig.GRAD_REVIEW_TYPE[rec.stage];
        return GameConfig.TYPE_LADDER[rec.step];
    }

    public static WordRec learnStep(WordRec rec, boolean correct, long now) {
        int s = rec.step;
        if (!correct) {
            rec.wrongCount++;
            rec.state = "LEARNING";
            rec.step = Math.max(0, s - 1);
            rec.due = now + (long) GameConfig.STEP_DELAY_MIN[rec.step] * 60_000L;
            return rec;
        }
        if (s < 3) {
            rec.step = s + 1;
            rec.state = "LEARNING";
            rec.due = now + (long) GameConfig.STEP_DELAY_MIN[s] * 60_000L;
        } else {
            // 拼写（最终关卡）答对 → 毕业，进入长期间隔重复
            rec.graduated = true;
            rec.state = "FAMILIAR";
            rec.stage = 0;
            rec.streak = 0;
            rec.due = now + (long) GameConfig.SRS_LADDER[0] * 60_000L;
        }
        return rec;
    }

    public static WordRec gradStep(WordRec rec, boolean correct, boolean fast, long now) {
        int top = GameConfig.SRS_LADDER.length - 1;
        if (!correct) {
            rec.wrongCount++;
            rec.lapses++;
            rec.stage = 0;
            rec.streak = 0;
            rec.graduated = false;
            rec.state = "LEARNING";
            rec.step = rec.wrongCount >= 2 ? 1 : 2;
            rec.due = now + (long) GameConfig.STEP_DELAY_MIN[rec.step] * 60_000L;
            return rec;
        }
        if (fast) {
            rec.stage = Math.min(rec.stage + 1, top);
            rec.streak = 0;
        } else {
            rec.streak++;
            if (rec.streak >= 2) {
                rec.stage = Math.min(rec.stage + 1, top);
                rec.streak = 0;
            }
        }
        int st = rec.stage;
        rec.state = st == top ? "MASTERED" : "FAMILIAR";
        rec.due = now + (long) GameConfig.SRS_LADDER[st] * 60_000L;
        return rec;
    }

    /** 统一调度：毕业前走阶梯，毕业后走 SM-2。 */
    public static WordRec schedule(WordRec rec, boolean correct, boolean fast, long now) {
        if (rec.graduated) return gradStep(rec, correct, fast, now);
        return learnStep(rec, correct, now);
    }

    // ---------------- 到期队列 ----------------
    public static List<String> dueLearning(Map<String, WordRec> progress, long now) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, WordRec> e : progress.entrySet()) {
            WordRec r = e.getValue();
            if (!r.graduated && r.due > 0 && r.due <= now) out.add(e.getKey());
        }
        return out;
    }

    public static List<String> dueGraduated(Map<String, WordRec> progress, long now) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, WordRec> e : progress.entrySet()) {
            WordRec r = e.getValue();
            if (r.graduated && r.due > 0 && r.due <= now) out.add(e.getKey());
        }
        return out;
    }

    // ---------------- 错词本（分级清单） ----------------
    public static class WrongRow {
        public String word, pos, mean;
        public int wrongCount, grade;
        WrongRow(String word, String pos, String mean, int wrongCount, int grade) {
            this.word = word; this.pos = pos; this.mean = mean;
            this.wrongCount = wrongCount; this.grade = grade;
        }
    }

    public static List<WrongRow> wrongBook(Map<String, WordRec> progress) {
        WordBank bank = WordBank.get();
        List<WrongRow> rows = new ArrayList<>();
        for (Map.Entry<String, WordRec> e : progress.entrySet()) {
            String word = e.getKey();
            WordRec r = e.getValue();
            if (r.wrongCount > 0 || "LEARNING".equals(r.state)) {
                WordEntry en = bank.entry(word);
                String pos = en == null ? "" : en.p;
                String mean = en == null ? "" : en.m;
                rows.add(new WrongRow(word, pos, mean, r.wrongCount,
                        r.wrongCount > 0 ? gradeOf(r.wrongCount) : 1));
            }
        }
        rows.sort((a, b) -> a.grade != b.grade ? Integer.compare(b.grade, a.grade)
                : Integer.compare(b.wrongCount, a.wrongCount));
        return rows;
    }

    // ---------------- 错词本复习状态推进 ----------------
    /** 错词本「记住了」：状态推进一阶（NEW→LEARNING→FAMILIAR→MASTERED），已到顶则保持。 */
    public static String advanceState(String state) {
        String[] order = {"NEW", "LEARNING", "FAMILIAR", "MASTERED"};
        int i = 0;
        for (int k = 0; k < order.length; k++) {
            if (order[k].equals(state)) { i = k; break; }
        }
        return order[Math.min(i + 1, order.length - 1)];
    }
}
