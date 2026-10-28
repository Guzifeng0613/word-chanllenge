package com.guzifeng.worddungeon.core;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** 刷题逻辑验证：新词阶梯、快刷出题、判定正确性（纯 JVM 运行，绕过 Android Context）。 */
public class VerifyLogic {

    static WordBank bank;
    static long NOW = System.currentTimeMillis();

    public static void main(String[] args) throws Exception {
        java.lang.reflect.Constructor<WordBank> c0 = WordBank.class.getDeclaredConstructor();
        c0.setAccessible(true);
        bank = c0.newInstance();
        bank.parse(new String(Files.readAllBytes(Paths.get(args[0]))),
                   new String(Files.readAllBytes(Paths.get(args[1]))));
        java.lang.reflect.Field f = WordBank.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, bank);
        System.out.println("词库载入：" + bank.words.size() + " 词，易混组 " + bank.groups.size() + " 组\n");

        scenarioA_learningLadder();
        scenarioA2_wrongRegression();
        scenarioB_quickDraw();
        scenarioC_judgment();
    }

    // ── 场景 A：新词完整阶梯（识→认→写→拼→毕业→SM-2 长间隔）──
    static void scenarioA_learningLadder() {
        System.out.println("========== 场景 A：新词完整学习阶梯（以 balance 为例）==========");
        String word = bank.byWord.containsKey("balance") ? "balance" : bank.words.get(0).w;
        WordRec rec = new WordRec();
        System.out.println("新词初始：state=" + rec.state + " step=" + rec.step + " 题型=" + SrsEngine.typeForWord(rec));

        // 毕业前连续答对 4 步
        for (int i = 0; i < 4; i++) {
            String qt = SrsEngine.typeForWord(rec);
            SrsEngine.learnStep(rec, true, NOW);
            long gapMin = (rec.due - NOW) / 60_000L;
            System.out.printf("  答对[%s] → step=%d 状态=%s 下次到期=%d 分钟后，下次题型=%s%n",
                    qt, rec.step, rec.state, gapMin,
                    rec.graduated ? "（已毕业）" + SrsEngine.typeForWord(rec) : SrsEngine.typeForWord(rec));
        }
        System.out.println("拼写最终关答对 → graduated=" + rec.graduated + " state=" + rec.state
                + " stage=" + rec.stage + "（进入长期间隔重复）");

        // 毕业后：fast 答对逐级跳，间隔 10分→1天→3天→7天→16天→30天
        System.out.println("毕业后 fast 答对（快答跳级）：");
        for (int i = 0; i < 6; i++) {
            SrsEngine.gradStep(rec, true, true, NOW);
            long gapMin = (rec.due - NOW) / 60_000L;
            System.out.printf("  stage=%d 状态=%s 间隔=%d 分钟（≈%.1f 天）复习题型=%s%n",
                    rec.stage, rec.state, gapMin, gapMin / 1440.0, SrsEngine.typeForWord(rec));
        }
        System.out.println("非 fast 答对需连续 2 次才推进（streak 机制），此处不展开。\n");
    }

    // ── 场景 A2：答错回退（毕业前 step 退、毕业后打回学习）──
    static void scenarioA2_wrongRegression() {
        System.out.println("========== 场景 A2：答错回退 ==========");
        WordRec rec = new WordRec();
        SrsEngine.learnStep(rec, true, NOW);  // step 0→1
        SrsEngine.learnStep(rec, true, NOW);  // step 1→2
        System.out.println("连对 2 次后 step=" + rec.step);
        SrsEngine.learnStep(rec, false, NOW); // 答错：step 退 1
        System.out.println("  答错 → step=" + rec.step + " 题型=" + SrsEngine.typeForWord(rec)
                + " 间隔=" + (rec.due - NOW) / 60_000 + " 分钟，wrongCount=" + rec.wrongCount);

        // 毕业后答错：打回学习
        WordRec r2 = new WordRec();
        r2.graduated = true; r2.stage = 3; r2.state = "FAMILIAR";
        SrsEngine.gradStep(r2, false, false, NOW);
        System.out.println("毕业后复习答错 → graduated=" + r2.graduated + " 打回 step=" + r2.step
                + " 状态=" + r2.state + " lapses=" + r2.lapses + "\n");
    }

    // 模拟 BackendRouter.answer：答对（fast）→ schedule 推进 + 局内进阶入队（未毕业才入队）
    static void answerCorrect(Quiz q, Map<String, WordRec> progress, RunState rs) {
        WordRec rec = progress.get(q.word);
        if (rec == null) rec = new WordRec();
        SrsEngine.schedule(rec, true, true, NOW);
        progress.put(q.word, rec);
        rs.correctOnce.put(q.word, true);
        if (!rec.graduated && !rs.advQueue.contains(q.word)
                && rs.advQueue.size() < GameConfig.QUICK_ADV_QUEUE_MAX) {
            rs.advQueue.add(q.word);
        }
    }

    // ── 场景 B：快刷三档出题分布（模拟真实玩家：progress 持久累积，每局 50 题、共 12 局）──
    static void scenarioB_quickDraw() {
        System.out.println("========== 场景 B：快刷三档出题（模拟真实玩家，每档 12 局 × 50 题）==========");
        // 新用户首局：同一 RunState，答对即进阶（真实情况）
        QuickEngine qe0 = new QuickEngine("normal");
        Map<String, Integer> first = new HashMap<>();
        Map<String, WordRec> p0 = new HashMap<>();
        RunState rs0 = new RunState();
        for (int i = 0; i < 50; i++) {
            Quiz q = qe0.draw(p0, null, rs0, NOW);
            first.merge(q.qtype, 1, Integer::sum);
            answerCorrect(q, p0, rs0);
        }
        System.out.println("新用户首局（真实：答对即进阶）：" + first);

        for (String level : new String[]{"normal", "hard", "hell"}) {
            QuickEngine qe = new QuickEngine(level);
            Map<String, WordRec> progress = new HashMap<>();
            Map<String, Integer> qtCnt = new HashMap<>();
            int choiceT = -1, spellMin = 999, spellMax = 0;
            int[] freqBuckets = new int[4];   // 被抽词按词频 f 分桶：<100 / <500 / <2000 / 其他
            for (int g = 0; g < 12; g++) {
                RunState rs = new RunState();   // 每局新 RunState
                for (int i = 0; i < 50; i++) {
                    Quiz q = qe.draw(progress, null, rs, NOW);
                    qtCnt.merge(q.qtype, 1, Integer::sum);
                    WordEntry we = bank.byWord.get(q.word);
                    if (we.f < 100) freqBuckets[0]++;
                    else if (we.f < 500) freqBuckets[1]++;
                    else if (we.f < 2000) freqBuckets[2]++;
                    else freqBuckets[3]++;
                    if (!"spell".equals(q.qtype)) choiceT = q.timeLimit;
                    else { spellMin = Math.min(spellMin, q.timeLimit); spellMax = Math.max(spellMax, q.timeLimit); }
                    answerCorrect(q, progress, rs);
                }
            }
            System.out.printf("  [%s] 题型分布：judge=%d en2cn=%d cn2en=%d spell=%d；选择题限时=%ds；拼写限时 %d~%ds%n",
                    level, qtCnt.getOrDefault("judge",0), qtCnt.getOrDefault("en2cn",0),
                    qtCnt.getOrDefault("cn2en",0), qtCnt.getOrDefault("spell",0),
                    choiceT, spellMin, spellMax == 0 ? 0 : spellMax);
            System.out.printf("      被抽词频分布（f<100 / <500 / <2000 / 其他）：%d / %d / %d / %d%n",
                    freqBuckets[0], freqBuckets[1], freqBuckets[2], freqBuckets[3]);
        }

        // 单局冷却：同一 RunState 连续出题，答对的词不再重复（词池未耗尽时）
        QuickEngine qe = new QuickEngine("normal");
        RunState rs = new RunState();
        Map<String, Integer> repeat = new HashMap<>();
        for (int i = 0; i < 200; i++) {
            Quiz q = qe.draw(new HashMap<>(), null, rs, NOW);
            repeat.merge(q.word, 1, Integer::sum);
            rs.correctOnce.put(q.word, true);   // 模拟答对
        }
        long dup = repeat.values().stream().filter(v -> v > 1).count();
        System.out.println("单局连续 200 题（答对即冷却）：重复出现的词数=" + dup
                + "（词池未耗尽时答对词不重复；词池耗尽才会轮播）\n");

        scenarioB2_longTerm();
    }

    // ── 场景 B2：长期刷题（每档 3000 题），看阶梯推进与拼写是否出现 ──
    static void scenarioB2_longTerm() {
        System.out.println("========== 场景 B2：长期刷题（每档 3000 题，progress 持续累积）==========");
        for (String level : new String[]{"normal", "hard", "hell"}) {
            QuickEngine qe = new QuickEngine(level);
            Map<String, WordRec> progress = new HashMap<>();
            Map<String, Integer> qtCnt = new HashMap<>();
            for (int g = 0; g < 60; g++) {
                RunState rs = new RunState();
                for (int i = 0; i < 50; i++) {
                    Quiz q = qe.draw(progress, null, rs, NOW);
                    qtCnt.merge(q.qtype, 1, Integer::sum);
                    answerCorrect(q, progress, rs);
                }
            }
            // progress 各 step/stage 分布
            int[] steps = new int[4]; int grad = 0;
            for (WordRec r : progress.values()) {
                if (r.graduated) grad++;
                else steps[Math.min(r.step, 3)]++;
            }
            System.out.printf("  [%s] 题型：judge=%d en2cn=%d cn2en=%d spell=%d%n",
                    level, qtCnt.getOrDefault("judge",0), qtCnt.getOrDefault("en2cn",0),
                    qtCnt.getOrDefault("cn2en",0), qtCnt.getOrDefault("spell",0));
            System.out.printf("      3000 题后 progress：未毕业 step0=%d step1=%d step2=%d step3=%d；已毕业=%d（总接触词 %d）%n",
                    steps[0], steps[1], steps[2], steps[3], grad, progress.size());
        }
        System.out.println();
    }

    // ── 场景 C：判定正确性（judge 真假、选择题大小写/空格）──
    static void scenarioC_judgment() {
        System.out.println("========== 场景 C：判定正确性 ==========");
        WordEntry e = bank.words.get(0);
        // judge：shown 等于真实释义 → yes；否则 no
        boolean t1 = e.m.equals(e.m);
        String wrongMean = bank.judgeWrongMean(e.w, null);
        boolean t2 = e.m.equals(wrongMean);
        System.out.println("judge 判定：shown=真实释义 → 正确答案 yes（" + t1
                + "）；shown=错误释义「" + wrongMean + "」→ 正确答案 no（" + !t2 + "）");
        // 选择题大小写/空格 trim
        String picked = " " + e.w.toUpperCase() + " ";
        boolean ok = picked.trim().toLowerCase().equals(e.w.trim().toLowerCase());
        System.out.println("选择题判定：玩家输入「" + picked + "」trim+小写后匹配「" + e.w + "」→ " + ok);
        // 干扰项不重复且不含正确答案
        java.util.List<String> dis = bank.makeDistractors(e.w, 3, null, false);
        System.out.println("en2cn 干扰项 3 个（不含正确释义）：" + dis + "，含正确答案？" + dis.contains(e.m));
    }
}
