package com.guzifeng.worddungeon.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 出题引擎（对应 core.draw_question：闯关 / 无尽）
 * 优先级：到期未毕业（错词/中段）→ 到期已毕业（SM-2）→ 新词；
 * θ/手动难度选词难度档，题型由 step/stage 决定，拼写压轴；单局错词 1 次重现封顶。
 */
public class QuizEngine {

    /** 出题参数（对应 draw_question 的入参） */
    public static class DrawArgs {
        public int layer = 1;
        public Map<String, WordRec> progress;
        public String force = null;
        public String book = null;
        public Integer difficulty = null;
        public Double theta = null;
        public int newToday = 0;
        public Long now = null;
        public RunState runState = null;
        public String mode = "campaign";
    }

    private final WordBank bank;
    private final Random rnd;

    public QuizEngine() {
        this.bank = WordBank.get();
        this.rnd = bank.rnd;
    }

    public Quiz draw(DrawArgs a) {
        Map<String, WordRec> progress = a.progress == null ? new java.util.HashMap<>() : a.progress;
        long now = a.now != null ? a.now : System.currentTimeMillis();
        RunState runState = a.runState != null ? a.runState : new RunState();

        // 词书范围
        List<String> bookWords = a.book != null
                ? bank.wordStringsByBook(a.book)
                : new ArrayList<String>() {{ for (WordEntry e : bank.words) add(e.w); }};
        Set<String> bookSet = new HashSet<>(bookWords);

        // 难度档
        Integer tier = null;
        if (a.force != null) {
            tier = null;
        } else if (a.theta != null) {
            tier = SrsEngine.adaptiveTier(a.theta).tier;
        } else if (a.difficulty != null) {
            tier = a.difficulty;
        } else {
            tier = a.book != null ? null : Math.min(a.layer, 3);
        }
        // 无尽：难度随深度抬升（下限）
        if ("endless".equals(a.mode)) {
            int floor = endlessTierFloor(a.layer);
            tier = tier == null ? floor : Math.max(tier, floor);
        }

        // 到期队列
        List<String> dl = new ArrayList<>();
        for (String w : SrsEngine.dueLearning(progress, now))
            if (bookSet.contains(w) && runState.cap(w)) dl.add(w);
        List<String> dg = new ArrayList<>();
        for (String w : SrsEngine.dueGraduated(progress, now))
            if (bookSet.contains(w) && runState.cap(w)) dg.add(w);
        List<String> due = new ArrayList<>();
        due.addAll(dl); due.addAll(dg);

        boolean canNew = a.newToday < GameConfig.NEW_CAP;
        boolean tryNew = canNew && (due.isEmpty() || rnd.nextDouble() < 0.30);

        String word = null;
        boolean fromReview = false;

        if (tryNew) {
            List<String> unseen = collectUnseen(bookWords, progress, tier);
            if (unseen.isEmpty()) unseen = collectUnseen(bookWords, progress, null);
            if (!unseen.isEmpty()) word = unseen.get(rnd.nextInt(unseen.size()));
        }
        if (word == null) {
            if (!due.isEmpty()) {
                word = !dl.isEmpty() ? dl.get(rnd.nextInt(dl.size()))
                        : dg.get(rnd.nextInt(dg.size()));
                fromReview = true;
            } else {
                // 无到期：优先推进最接近毕业的学习词
                List<String> learning = new ArrayList<>();
                for (String w : progress.keySet()) {
                    WordRec r = progress.get(w);
                    if (!r.graduated && bookSet.contains(w) && runState.cap(w)) learning.add(w);
                }
                Collections.sort(learning, new Comparator<String>() {
                    public int compare(String x, String y) {
                        return Integer.compare(progress.get(y).step, progress.get(x).step);
                    }
                });
                if (!learning.isEmpty()) {
                    word = learning.get(0);
                } else if (canNew) {
                    List<String> unseen = collectUnseen(bookWords, progress, tier);
                    if (unseen.isEmpty()) unseen = collectUnseen(bookWords, progress, null);
                    if (!unseen.isEmpty()) word = unseen.get(rnd.nextInt(unseen.size()));
                }
                if (word == null) {
                    List<String> pool = new ArrayList<>();
                    for (String w : bookWords) {
                        if (runState.cap(w) && (tier == null || bank.entry(w).t == tier)) pool.add(w);
                    }
                    List<String> base = pool.isEmpty() ? bookWords : pool;
                    word = base.get(rnd.nextInt(base.size()));
                }
            }
        }

        // 重现计数
        if (Boolean.TRUE.equals(runState.wrong.get(word))) {
            runState.seen.put(word, runState.seen.getOrDefault(word, 0) + 1);
        }

        // 题型：force 覆盖；否则按阶梯；超长词不拼写
        WordRec rec = progress.get(word);
        String qtype = a.force != null ? a.force : SrsEngine.typeForWord(rec);
        if ("spell".equals(qtype) && word.length() > 8) qtype = "cn2en";

        return buildQuiz(qtype, word, rec, runState, fromReview, null);
    }

    private List<String> collectUnseen(List<String> bookWords, Map<String, WordRec> progress, Integer tier) {
        List<String> out = new ArrayList<>();
        for (String w : bookWords) {
            if (progress.containsKey(w)) continue;
            if (tier != null && bank.entry(w).t != tier) continue;
            out.add(w);
        }
        return out;
    }

    /** 构建题目（快刷与闯关共用，按题型填充 options/correct/timeLimit）。 */
    public static Quiz buildQuiz(String qtype, String word, WordRec rec, RunState runState,
                                 boolean fromReview, QuickEngine qengine) {
        WordBank bank = WordBank.get();
        WordEntry e = bank.entry(word);
        Quiz q = new Quiz();
        q.qtype = qtype;
        q.word = word;
        q.pos = e.p;
        q.mean = e.m;
        q.t = e.t;
        q.b = e.b.toArray(new String[0]);
        q.review = fromReview;
        q.step = rec == null ? 0 : rec.step;
        q.graduated = rec != null && rec.graduated;
        q.stage = rec == null ? 0 : rec.stage;
        q.wrongCount = rec == null ? 0 : rec.wrongCount;
        q.revEngaged = runState != null && Boolean.TRUE.equals(runState.wrong.get(word));
        q.runState = runState;

        switch (qtype) {
            case "en2cn": {
                List<String> opts = bank.makeDistractors(word, 3);
                opts.add(e.m);
                Collections.shuffle(opts, bank.rnd);
                q.options = opts;
                q.correct = e.m;
                q.timeLimit = (int) GameConfig.NORMAL_T;
                break;
            }
            case "cn2en": {
                List<String> opts = bank.distractorWords(word, 3);
                opts.add(word);
                Collections.shuffle(opts, bank.rnd);
                q.options = opts;
                q.correct = word;
                q.timeLimit = (int) GameConfig.NORMAL_T;
                break;
            }
            case "spell": {
                int limit = GameConfig.SPELL_BASE + word.length() * GameConfig.SPELL_PER_CHAR;
                if (qengine != null) limit += qengine.spellTimeAdd();
                q.correct = word;
                q.timeLimit = limit;
                break;
            }
            default: { // judge
                boolean isFirst = rec == null || (!rec.graduated && rec.step == 0);
                if (isFirst) {
                    q.judgeShown = e.m; q.correct = "yes";
                } else if (bank.rnd.nextDouble() < 0.5) {
                    q.judgeShown = e.m; q.correct = "yes";
                } else {
                    List<String> d = bank.makeDistractors(word, 1);
                    q.judgeShown = d.isEmpty() ? e.m : d.get(0);
                    q.correct = "no";
                }
                q.timeLimit = (int) GameConfig.NORMAL_T;
            }
        }
        return q;
    }

    // 无尽深度缩放
    public static int endlessTierFloor(int depth) {
        return Math.min(3, 1 + (depth - 1) / GameConfig.ENDLESS_TIER_EVERY);
    }
}
