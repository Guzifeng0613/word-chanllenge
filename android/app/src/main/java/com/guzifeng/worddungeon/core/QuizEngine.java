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
        /** 当前敌人是否「易混词妖」（confuse）：干扰项优先同组 */
        public boolean confuse = false;
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
        // 远征（V12.0）：以 CAMPAIGN_TIER[layer-1] 为该层词难度下限；
        // 带手动/自适应难度取 max(tier, 下限)；"all"/无下限(tier==null) 直接用该层下限。
        if ("campaign".equals(a.mode)) {
            int floor = GameConfig.CAMPAIGN_TIER[layerIdx(a.layer)];
            tier = (tier == null) ? floor : Math.max(tier, floor);
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
        // 远征拼写权重（V12.0）：在基础题型上按层概率把题型升级为 spell（后期拼写为主）
        if (a.force == null && "campaign".equals(a.mode)) {
            double bias = GameConfig.CAMPAIGN_SPELL_BIAS[layerIdx(a.layer)];
            if (bias > 0 && rnd.nextDouble() < bias) qtype = "spell";
        }
        if ("spell".equals(qtype) && word.length() > 8) qtype = "cn2en";
        // 用户规则：单词先以非拼写题型出现过一次，才允许拼写（force=用户显式选型，尊重）
        if (a.force == null) qtype = guardSpell(runState, qtype, word);

        // 先见后拼记录：本题为非拼写题型 → 该词本局已“见过”，下次允许拼写
        if (!"spell".equals(qtype)) runState.exposed.put(word, true);

        Quiz quiz = buildQuiz(qtype, word, rec, runState, fromReview, null, a.book, a.confuse);
        // 远征限时（V12.0）：非拼写选择题用逐层收紧限时；拼写在基线之上加逐层额外秒。
        // 无尽/快刷不经过此覆盖，逻辑保持不变。
        if ("campaign".equals(a.mode)) {
            int li = layerIdx(a.layer);
            if ("spell".equals(qtype)) quiz.timeLimit += GameConfig.CAMPAIGN_SPELL_TIME_ADD[li];
            else quiz.timeLimit = GameConfig.CAMPAIGN_CHOICE_TIME[li];
        }
        return quiz;
    }

    /** 层下标 clamp：layer 钳到 [1,LAYER_COUNT] 后转 0 基下标，防越界。 */
    private static int layerIdx(int layer) {
        return Math.max(0, Math.min(GameConfig.LAYER_COUNT, layer)) - 1;
    }

    /** 拼写守卫：本局未以非拼写题型出现过的词，拼写题降级为选词（cn2en），先见一次 */
    public static String guardSpell(RunState rs, String qtype, String word) {
        if (!"spell".equals(qtype)) return qtype;
        if (rs != null && Boolean.TRUE.equals(rs.exposed.get(word))) return qtype;
        return "cn2en";
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
        return buildQuiz(qtype, word, rec, runState, fromReview, qengine, null, false);
    }

    /** 构建题目：book 限定词书范围（干扰项池=G.layerWords），confuseEnemy 时干扰项优先同组。 */
    public static Quiz buildQuiz(String qtype, String word, WordRec rec, RunState runState,
                                 boolean fromReview, QuickEngine qengine,
                                 String book, boolean confuseEnemy) {
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
                List<String> opts = bank.makeDistractors(word, 3, book, confuseEnemy);
                opts.add(e.m);
                Collections.shuffle(opts, bank.rnd);
                q.options = opts;
                q.correct = e.m;
                q.timeLimit = (int) GameConfig.NORMAL_T;
                break;
            }
            case "cn2en": {
                List<String> opts = bank.distractorWords(word, 3, book, confuseEnemy);
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
                if (isFirst || bank.rnd.nextDouble() < 0.5) {
                    q.judgeShown = e.m; q.correct = "yes";
                } else {
                    q.judgeShown = bank.judgeWrongMean(word, book);
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
