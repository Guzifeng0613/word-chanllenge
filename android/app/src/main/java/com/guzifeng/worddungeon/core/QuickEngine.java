package com.guzifeng.worddungeon.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 快速刷题引擎（对应 core.draw_quick 及快刷辅助）
 * 普通：高频优先、识别为主；困难：错词加权、进阶；地狱：拼写为主、拼写压轴。
 */
public class QuickEngine {
    private final String level;     // normal / hard / hell
    private final int idx;          // 0 / 1 / 2
    private final WordBank bank;
    private final Random rnd;

    public QuickEngine(String level) {
        this.level = level;
        this.idx = levelIndex(level);
        this.bank = WordBank.get();
        this.rnd = bank.rnd;
    }

    public static int levelIndex(String level) {
        if ("hard".equals(level)) return 1;
        if ("hell".equals(level)) return 2;
        return 0;
    }

    public int spellTimeAdd() {
        return GameConfig.QUICK_SPELL_TIME_ADD[idx];
    }

    // ---------------- 词频分 ----------------
    public static double freqScore(String word) {
        WordEntry e = WordBank.get().entry(word);
        int f = e == null ? 999999 : e.f;
        if (f >= 999999) return GameConfig.QUICK_NO_FREQ_SCORE;
        double ref = GameConfig.QUICK_FREQ_REF;
        return Math.max(0.0, 1.0 - Math.log10(Math.max(1, f)) / Math.log10(ref));
    }

    // ---------------- 错误分级 ----------------
    public static void normWrongBy(WordRec rec) {
        for (String level : GameConfig.QUICK_LEVELS) {
            Map<String, Integer> row = rec.wrongBy.get(level);
            if (row == null) {
                row = new java.util.HashMap<>();
                for (String qt : new String[]{"judge", "en2cn", "cn2en", "spell"}) row.put(qt, 0);
                rec.wrongBy.put(level, row);
            }
        }
    }

    public static boolean hasAnyError(WordRec rec) {
        normWrongBy(rec);
        for (Map<String, Integer> row : rec.wrongBy.values()) {
            for (int v : row.values()) if (v > 0) return true;
        }
        return false;
    }

    public static double errorBoost(WordRec rec) {
        normWrongBy(rec);
        double total = 0.0;
        for (int gi = 0; gi < 3; gi++) {
            Map<String, Integer> row = rec.wrongBy.get(GameConfig.QUICK_LEVELS[gi]);
            int qi = 0;
            for (String qt : new String[]{"judge", "en2cn", "cn2en", "spell"}) {
                total += row.getOrDefault(qt, 0) * GameConfig.QUICK_LEVEL_FACTOR[gi]
                        * GameConfig.QUICK_QTYPE_FACTOR[qi];
                qi++;
            }
        }
        return total * GameConfig.QUICK_ERROR_COEFF;
    }

    public static void markQuickWrong(WordRec rec, String level, String qtype) {
        normWrongBy(rec);
        Map<String, Integer> row = rec.wrongBy.get(level);
        row.put(qtype, row.getOrDefault(qtype, 0) + 1);
        rec.quickPass = false;
    }

    public static void markQuickPass(WordRec rec) {
        if (!hasAnyError(rec)) rec.quickPass = true;
    }

    /** V12.7.14 拼写门槛按档：normal/hard step≥3，hell step≥2（拼写压轴）；毕业后按复习题型。 */
    public boolean canSpell(WordRec rec, String word) {
        if (word.length() > 8) return false;
        if (rec == null) rec = new WordRec();
        if (rec.graduated) return "spell".equals(SrsEngine.typeForWord(rec));
        return rec.step >= (idx == 2 ? 2 : 3);
    }

    private double quickWeight(String word, WordRec rec) {
        double fs = freqScore(word);
        double base = GameConfig.QUICK_FLOOR[idx]
                + GameConfig.QUICK_BASE_FREQ[idx] * (Math.pow(Math.max(fs, 0.0), GameConfig.QUICK_FREQ_POW[idx]));
        if (rec == null) return Math.max(0.0, base);
        double eb = errorBoost(rec);
        double w = base * (1.0 + eb);
        if (!hasAnyError(rec) && rec.quickPass) {
            double suppress = idx == 2 ? GameConfig.QUICK_PASS_SUPPRESS_HELL
                    : idx == 1 ? GameConfig.QUICK_PASS_SUPPRESS_HARD : 1.0;
            w = base * suppress + GameConfig.QUICK_CAROUSEL;
        }
        return Math.max(0.0, w);
    }

    private static String weightedPick(List<String> items, List<Double> weights) {
        double total = 0.0;
        for (double w : weights) total += w;
        double r = WordBank.get().rnd.nextDouble() * total;
        for (int i = 0; i < items.size(); i++) {
            r -= weights.get(i);
            if (r <= 0) return items.get(i);
        }
        return items.get(items.size() - 1);
    }

    public Quiz draw(Map<String, WordRec> progress, String book, RunState runState, long now) {
        if (progress == null) progress = new java.util.HashMap<>();
        if (runState == null) runState = new RunState();

        List<WordEntry> pool = new ArrayList<>();
        for (WordEntry e : bank.words) {
            if (book != null && !e.inBook(book)) continue;
            pool.add(e);
        }

        // ── 路径 1：局内进阶队列（同一局内 识→认→写→拼 走完阶梯，题型多样）──
        double r = rnd.nextDouble();
        if (!runState.advQueue.isEmpty() && r < GameConfig.QUICK_ADV_PICK[idx]) {
            String w = runState.advQueue.remove(0);
            WordRec rec = progress.get(w);
            if (rec != null && bank.byWord.containsKey(w) && runState.cap(w)) {
                String qt = QuizEngine.guardSpell(runState, SrsEngine.typeForWord(rec), w);
                return build(qt, w, rec, runState, book);
            }
        }

        // ── 路径 2：新词（未接触，按词频加权；档越高新词越多）──
        if (rnd.nextDouble() < GameConfig.QUICK_NEW_PICK[idx]) {
            List<String> unseen = new ArrayList<>();
            List<Double> uw = new ArrayList<>();
            for (WordEntry e : pool) {
                String w = e.w;
                if (progress.containsKey(w) || !runState.cap(w)) continue;
                if (QuizEngine.cold(runState, w)) continue;
                double wt = quickWeight(w, null) * GameConfig.QUICK_NEW_BOOST[idx];
                if (wt > 0) { unseen.add(w); uw.add(wt); }
            }
            if (!unseen.isEmpty()) {
                String w = weightedPick(unseen, uw);
                return build("judge", w, null, runState, book);
            }
        }

        // ── 路径 3：全池加权（拼写候选 + 全池随机，含复习/已学词）──
        // 拼写候选（按档门槛；冷却滤除）
        List<String> spellWords = new ArrayList<>();
        List<Double> spellW = new ArrayList<>();
        for (WordEntry e : pool) {
            String w = e.w;
            if (!runState.cap(w) || !canSpell(progress.get(w), w)) continue;
            double wt = quickWeight(w, progress.get(w));
            if (wt > 0) { spellWords.add(w); spellW.add(wt); }
        }
        if (!spellWords.isEmpty()) {
            List<String> cw = new ArrayList<>(); List<Double> cW = new ArrayList<>();
            for (int i = 0; i < spellWords.size(); i++) {
                if (!QuizEngine.cold(runState, spellWords.get(i))) { cw.add(spellWords.get(i)); cW.add(spellW.get(i)); }
            }
            if (!cw.isEmpty()) { spellWords = cw; spellW = cW; }
        }

        String word;
        String qtype;
        if (!spellWords.isEmpty() && rnd.nextDouble() < GameConfig.QUICK_SPELL_BIAS[idx]) {
            word = weightedPick(spellWords, spellW);
            qtype = QuizEngine.guardSpell(runState, "spell", word);   // 先见后拼
        } else {
            List<String> words = new ArrayList<>();
            List<Double> ws = new ArrayList<>();
            for (WordEntry e : pool) {
                String w = e.w;
                if (!runState.cap(w)) continue;
                double wt = quickWeight(w, progress.get(w));
                if (wt > 0) { words.add(w); ws.add(wt); }
            }
            if (words.isEmpty()) {
                for (WordEntry e : pool) {
                    String w = e.w;
                    if (runState.cap(w)) { words.add(w); ws.add(1.0); }
                }
            }
            // V12.1：冷却滤除本局已答对词（词池未耗尽时）
            List<String> cw = new ArrayList<>(); List<Double> cW = new ArrayList<>();
            for (int i = 0; i < words.size(); i++) {
                if (!QuizEngine.cold(runState, words.get(i))) { cw.add(words.get(i)); cW.add(ws.get(i)); }
            }
            if (!cw.isEmpty()) { words = cw; ws = cW; }
            word = weightedPick(words, ws);
            qtype = SrsEngine.typeForWord(progress.get(word));
            if ("spell".equals(qtype) && !canSpell(progress.get(word), word)) qtype = "cn2en";
            if ("spell".equals(qtype) && word.length() > 8) qtype = "cn2en";
            qtype = QuizEngine.guardSpell(runState, qtype, word);   // 先见后拼
        }
        return build(qtype, word, progress.get(word), runState, book);
    }

    /** V12.7.14 公共构建：记录单局状态（错词重现/出题计数/先见后拼）+ buildQuiz + 快刷限时。 */
    private Quiz build(String qt, String w, WordRec rec, RunState rs, String book) {
        if (Boolean.TRUE.equals(rs.wrong.get(w))) {
            rs.seen.put(w, rs.seen.getOrDefault(w, 0) + 1);
        }
        rs.drawn.put(w, rs.drawn.getOrDefault(w, 0) + 1);
        if (!"spell".equals(qt)) rs.exposed.put(w, true);
        Quiz q = QuizEngine.buildQuiz(qt, w, rec, rs, false, this, book, false);
        q.quickLevel = level;
        q.timeLimit = "spell".equals(qt) ? q.timeLimit : GameConfig.QUICK_CHOICE_T;
        return q;
    }
}
