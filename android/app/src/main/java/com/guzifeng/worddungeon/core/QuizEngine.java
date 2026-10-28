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
        /** V13.5 英英模式开关（来自 Settings 持久化）：开启后才可能偶发 en2en 题。 */
        public boolean en2en = false;
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

        // 到期队列（V12.1：冷过滤队列与含冷却队列分开构建）
        // dl/dg：本局未答对过的到期词（非冷却）；dlAll/dgAll：含冷却的到期词，仅最终兜底启用。
        List<String> dl = new ArrayList<>();
        List<String> dg = new ArrayList<>();
        List<String> dlAll = new ArrayList<>();
        List<String> dgAll = new ArrayList<>();
        for (String w : SrsEngine.dueLearning(progress, now)) {
            if (!bookSet.contains(w) || !runState.cap(w)) continue;
            dlAll.add(w);
            if (!cold(runState, w)) dl.add(w);
        }
        for (String w : SrsEngine.dueGraduated(progress, now)) {
            if (!bookSet.contains(w) || !runState.cap(w)) continue;
            dgAll.add(w);
            if (!cold(runState, w)) dg.add(w);
        }
        List<String> due = new ArrayList<>();
        due.addAll(dl); due.addAll(dg);

        // V13.1 错词消化闭环：本局错词待消化池（无需手动进错词本）。
        // 仍受单局重现上限 cap(w) 约束（seen>=1 者排除，避免同一词刷屏）；
        // 顽固度在数据层参与排序/调度：gradeOf(wrongCount) 高者优先（连错>=2 顽固 > 偶发1次），同档按 wrongCount desc。
        List<String> replay = new ArrayList<>();
        for (String w : runState.wrong.keySet()) {
            if (!bookSet.contains(w)) continue;
            if (!runState.cap(w)) continue;
            replay.add(w);
        }
        Collections.sort(replay, new Comparator<String>() {
            public int compare(String x, String y) {
                WordRec rx = progress.get(x);
                WordRec ry = progress.get(y);
                int wx = rx == null ? 0 : rx.wrongCount;
                int wy = ry == null ? 0 : ry.wrongCount;
                // 顽固度数据层分级：wrongCount >= WRONG_STUBBORN（连错>=2）为顽固，排在偶发（仅错1次）之前；
                // 同档再按 wrongCount 降序。gradeOf 口径与之一致（1→1, 2→2, ≥3→3）。
                boolean stubbornX = wx >= GameConfig.WRONG_STUBBORN;
                boolean stubbornY = wy >= GameConfig.WRONG_STUBBORN;
                if (stubbornX != stubbornY) return stubbornX ? -1 : 1;
                return Integer.compare(wy, wx);
            }
        });

        boolean canNew = a.newToday < GameConfig.NEW_CAP;
        boolean tryNew = canNew && (due.isEmpty() || rnd.nextDouble() < 0.35);

        String word = null;
        boolean fromReview = false;
        boolean wrongReplay = false;

        // V13.1：距上一次错词穿插已过 WRONG_REVIEW_GAP 个普通题时，硬性穿插 1 个本局错词（顽固优先）。
        // 比例上限=每 GAP+1 题至多 1 个错词；其余题目仍走既有新词/到期逻辑，不挤占新词节奏。
        if (runState.replayGap >= GameConfig.WRONG_REVIEW_GAP && !replay.isEmpty()) {
            word = replay.get(0);
            fromReview = true;
            wrongReplay = true;
        }

        if (tryNew && !wrongReplay) {
            List<String> unseen = collectUnseen(bookWords, progress, tier);
            if (unseen.isEmpty()) unseen = collectUnseen(bookWords, progress, null);
            // V13.6：新词候选按核心度加权（oxford→collins），不再纯随机
            if (!unseen.isEmpty()) word = pickCoreWeighted(unseen);
        }
        if (word == null && !due.isEmpty()) {
            word = !dl.isEmpty() ? dl.get(rnd.nextInt(dl.size()))
                    : dg.get(rnd.nextInt(dg.size()));
            fromReview = true;
        }
        if (word == null) {
            // 无到期：推进学习中（冷过滤）的词，step 最高同阶组内随机
            List<String> learning = new ArrayList<>();
            for (String w : progress.keySet()) {
                WordRec r = progress.get(w);
                if (!r.graduated && bookSet.contains(w) && runState.cap(w) && !cold(runState, w)) learning.add(w);
            }
            Collections.sort(learning, new Comparator<String>() {
                public int compare(String x, String y) {
                    return Integer.compare(progress.get(y).step, progress.get(x).step);
                }
            });
            if (!learning.isEmpty()) {
                int top = progress.get(learning.get(0)).step;
                List<String> topGroup = new ArrayList<>();
                for (String w : learning) {
                    if (progress.get(w).step == top) topGroup.add(w); else break;
                }
                word = topGroup.get(rnd.nextInt(topGroup.size()));
            }
        }
        if (word == null && canNew) {
            // 新词兜底（前面 tryNew 未出词）
            List<String> unseen = collectUnseen(bookWords, progress, tier);
            if (unseen.isEmpty()) unseen = collectUnseen(bookWords, progress, null);
            if (!unseen.isEmpty()) word = pickCoreWeighted(unseen);
        }
        if (word == null) {
            // 最终兜底（新词额度满 + 非冷却到期/学习词都空）：
            // ① 含冷却的到期词；② 已学词全部（含冷却）；③ 全词书（保证不崩）。
            List<String> pool = new ArrayList<>();
            pool.addAll(dlAll); pool.addAll(dgAll);
            if (pool.isEmpty()) {
                for (String w : progress.keySet()) {
                    if (bookSet.contains(w) && runState.cap(w)) pool.add(w);
                }
            }
            if (pool.isEmpty()) pool = new ArrayList<>(bookWords);
            word = pool.get(rnd.nextInt(pool.size()));
        }

        // 重现计数
        if (Boolean.TRUE.equals(runState.wrong.get(word))) {
            runState.seen.put(word, runState.seen.getOrDefault(word, 0) + 1);
        }
        // V12.1：本局出题计数（单局去重依据）
        runState.drawn.put(word, runState.drawn.getOrDefault(word, 0) + 1);
        // V13.1：穿插比例计数——本次穿插了错词则归零，否则普通题 +1（达到 GAP 后下一次才允许再插）
        runState.replayGap = wrongReplay ? 0 : runState.replayGap + 1;

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

        // V13.4 一词多形：仅当本词已毕业、且不是本局错词消化穿插时，按间隔+概率偶发升级为 form 题。
        // 注意：只替换这道「本来就要出的毕业词复习题」的题型，不占用新词/错词槽位；一次只考一个形。
        String formKey = null;
        if (a.force == null && !wrongReplay && rec != null && rec.graduated) {
            formKey = pickFormKey(word, runState);
        }
        if (formKey != null) {
            runState.formGap = 0;
            runState.formSeen.put(word + "|" + formKey, true);
        } else {
            runState.formGap++;
        }
        String baseQtype = qtype;          // buildQuiz 仍按原复习题型搭骨架
        if (formKey != null) qtype = "form";

        // V13.5 英英模式（默认关）：开关开启后，已学词复习题按间隔+概率偶发升级为 en2en（看英文释义选单词）。
        // 与 form 互斥（form 已命中则不升级）；不占新词/错词槽；关闭开关后 a.en2en=false，本分支永不进入。
        boolean toEn2en = false;
        if (formKey == null && a.en2en && a.force == null && !wrongReplay && rec != null) {
            WordEntry e0 = bank.entry(word);
            if (e0 != null && !e0.d2.isEmpty()
                    && runState.en2enGap >= GameConfig.EN2EN_REVIEW_GAP
                    && rnd.nextDouble() < GameConfig.EN2EN_PROB) {
                toEn2en = true;
            }
        }
        if (toEn2en) runState.en2enGap = 0; else runState.en2enGap++;
        if (toEn2en) qtype = "en2en";

        // 先见后拼记录：本题为非拼写题型 → 该词本局已“见过”，下次允许拼写
        if (!"spell".equals(baseQtype)) runState.exposed.put(word, true);

        Quiz quiz = buildQuiz(baseQtype, word, rec, runState, fromReview, null, a.book, a.confuse);
        quiz.wrongReplay = wrongReplay;   // V13.1：标记本题为错词消化穿插题，答对触发「已攻克」反馈
        // V13.4：覆盖为 form 题——题干=原型+形名标签，选项=正确变形+全库同形名真实变形干扰项
        if (formKey != null) {
            WordEntry e = bank.entry(word);
            String correct = e == null || e.x == null ? "" : e.x.optString(formKey, "");
            List<String> opts = bank.formDistractors(formKey, correct, 3);
            opts.add(correct);
            Collections.shuffle(opts, bank.rnd);
            quiz.qtype = "form";
            quiz.formKey = formKey;
            quiz.formLabel = GameConfig.formLabel(formKey);
            quiz.options = opts;
            quiz.correct = correct;
            quiz.timeLimit = (int) GameConfig.NORMAL_T;
        }
        // V13.5：覆盖为 en2en 题——题干=英文释义 d2，选项=正确单词+3 个英文干扰词（复用 cn2en 干扰机制）
        if (toEn2en) {
            WordEntry e = bank.entry(word);
            List<String> opts2 = new ArrayList<>(bank.distractorWords(word, 3, a.book, a.confuse));
            opts2.add(word);
            Collections.shuffle(opts2, bank.rnd);
            quiz.qtype = "en2en";
            quiz.d2 = e == null ? "" : e.d2;
            quiz.options = opts2;
            quiz.correct = word;
            quiz.timeLimit = GameConfig.EN2EN_TIME;
        }
        // 远征限时（V12.0）：非拼写选择题用逐层收紧限时；拼写在基线之上加逐层额外秒。
        // 无尽/快刷不经过此覆盖，逻辑保持不变。
        if ("campaign".equals(a.mode)) {
            int li = layerIdx(a.layer);
            if ("spell".equals(qtype)) quiz.timeLimit += GameConfig.CAMPAIGN_SPELL_TIME_ADD[li];
            else quiz.timeLimit = GameConfig.CAMPAIGN_CHOICE_TIME[li];
        }
        return quiz;
    }

    /** 层下标 clamp：layer 钳到 [1,LAYER_COUNT] 后转 0 基下标，防越界（下界与 BackendRouter/EnemyModel 对齐）。 */
    private static int layerIdx(int layer) {
        return Math.max(1, Math.min(GameConfig.LAYER_COUNT, layer)) - 1;
    }

    /** 拼写守卫：本局未以非拼写题型出现过的词，拼写题降级为选词（cn2en），先见一次 */
    public static String guardSpell(RunState rs, String qtype, String word) {
        if (!"spell".equals(qtype)) return qtype;
        if (rs != null && Boolean.TRUE.equals(rs.exposed.get(word))) return qtype;
        return "cn2en";
    }

    /**
     * V13.4 一词多形：为本词挑一个可考察的形名（plural/past/ing/third/comparative/superlative）。
     * 门控：① 间隔已满（formGap>=FORM_REVIEW_GAP）；② 概率掷骰；③ 该词性确有此形数据；
     * ④ 本局已考过的形不再重复。返回 null 表示本次不升级。
     */
    private String pickFormKey(String word, RunState runState) {
        WordEntry e = bank.entry(word);
        if (e == null || e.x == null) return null;
        if (runState.formGap < GameConfig.FORM_REVIEW_GAP) return null;
        if (rnd.nextDouble() >= GameConfig.FORM_QUESTION_PROB) return null;
        List<String> candidates = new ArrayList<>();
        for (String key : GameConfig.formKeysForPos(e.p)) {
            String val = e.x.optString(key, "");
            if (val.isEmpty()) continue;
            if (Boolean.TRUE.equals(runState.formSeen.get(word + "|" + key))) continue;
            candidates.add(key);
        }
        if (candidates.isEmpty()) return null;
        // V13.8：不规则形优先——把有限的 form 题名额留给教学价值高的不规则形；
        // 有不规则候选时 85% 选不规则、15% 选规则；只有规则形时仍照常考（book→books 仍可考，只是占比下降）。
        List<String> irregular = new ArrayList<>();
        List<String> regular = new ArrayList<>();
        for (String key : candidates) {
            String val = e.x.optString(key, "");
            if (GameConfig.isIrregular(word, key, val)) irregular.add(key);
            else regular.add(key);
        }
        List<String> pool;
        if (!irregular.isEmpty() && rnd.nextDouble() < 0.85) pool = irregular;
        else if (!regular.isEmpty()) pool = regular;
        else pool = irregular;
        return pool.get(rnd.nextInt(pool.size()));
    }

    /** V12.1 单局冷却：本局已答对过、且非本局答错词的候选不再短期回归（错词仍按 cap 重现）。 */
    public static boolean cold(RunState rs, String w) {
        if (rs == null) return false;
        if (!Boolean.TRUE.equals(rs.correctOnce.get(w))) return false;
        return !Boolean.TRUE.equals(rs.wrong.get(w));
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

    /**
     * V13.6 核心词优先：在新词候选池里按核心度加权随机抽 1 个。
     * 口径（层级与 θ/词频不冲突）：θ/手动难度先决定 tier 过滤（第一道）→
     * 同 tier 候选内，oxford=1 权重最高(100)，否则按柯林斯星级 5→1 递减(60/45/30/18/10)，
     * 无标记最低(3)；同权重内仍由随机打散，不是死板固定顺序。
     */
    public static int coreWeight(WordEntry e) {
        if (e == null) return 3;
        if (e.o == 1) return 100;
        switch (e.c) {
            case 5: return 60;
            case 4: return 45;
            case 3: return 30;
            case 2: return 18;
            case 1: return 10;
            default: return 3;
        }
    }

    private String pickCoreWeighted(List<String> unseen) {
        double[] ws = new double[unseen.size()];
        double total = 0;
        for (int i = 0; i < unseen.size(); i++) {
            ws[i] = coreWeight(bank.entry(unseen.get(i)));
            total += ws[i];
        }
        double r = rnd.nextDouble() * total;
        for (int i = 0; i < unseen.size(); i++) {
            r -= ws[i];
            if (r <= 0) return unseen.get(i);
        }
        return unseen.get(unseen.size() - 1);
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
                // V12.4：判断题必须有错误选项——新词 60% 正确/40% 错误、复习 45% 正确/55% 错误，
                // 不再让新词 100% 出"正确"，避免判断题永远是对的（用户需要真理解释义才能答对）。
                boolean isFirst = rec == null || (!rec.graduated && rec.step == 0);
                double correctBias = isFirst ? 0.60 : 0.45;
                if (bank.rnd.nextDouble() < correctBias) {
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
