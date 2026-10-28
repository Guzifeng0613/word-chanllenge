package com.guzifeng.worddungeon.server;

import android.content.Context;

import com.guzifeng.worddungeon.core.CardCatalog;
import com.guzifeng.worddungeon.core.DamageCalc;
import com.guzifeng.worddungeon.core.EnemyModel;
import com.guzifeng.worddungeon.core.GameConfig;
import com.guzifeng.worddungeon.core.Quiz;
import com.guzifeng.worddungeon.core.QuizEngine;
import com.guzifeng.worddungeon.core.QuickEngine;
import com.guzifeng.worddungeon.core.RunState;
import com.guzifeng.worddungeon.core.SrsEngine;
import com.guzifeng.worddungeon.core.WordBank;
import com.guzifeng.worddungeon.core.WordEntry;
import com.guzifeng.worddungeon.core.WordRec;
import com.guzifeng.worddungeon.store.ProgressStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 后端路由分发（V10.1：对齐 V7.0 前端数据口径）
 * 新增：等级/XP/连续打卡/成就/每日目标/错词分级标签（wrong_by）/无尽纪录。
 */
public class BackendRouter {

    public static final int DAILY_QUESTIONS = 40;
    public static final int DAILY_NEW_WORDS = 15;
    public static final int XP_CORRECT = 2;
    public static final int XP_NEW_WORD = 5;
    public static final int XP_BASE = 120;

    private final Context ctx;
    private final ProgressStore store;
    private final EnemyModel enemy;
    private final QuizEngine quizEngine;

    public BackendRouter(Context ctx) {
        this.ctx = ctx;
        WordBank.init(ctx);
        this.store = ProgressStore.get(ctx);
        this.store.ensureLocalUser();
        this.enemy = new EnemyModel();
        this.quizEngine = new QuizEngine();
    }

    /** 分发：route 以 / 开头；method 仅 GET/POST。body 可为 null（GET 请求）。 */
    public JSONObject dispatch(String route, String method, JSONObject body) throws Exception {
        if ("/auth/guest".equals(route)) return guest();
        if ("/bank/meta".equals(route)) return bankMeta();
        if ("/quiz/draw".equals(route)) return draw(body == null ? new JSONObject() : body);
        if ("/quiz/answer".equals(route)) return answer(body == null ? new JSONObject() : body);
        if ("/review/due".equals(route)) return reviewDue(body == null ? null : body.optString("book", null));
        if ("/wrongbook".equals(route)) return wrongbook();
        if ("/wrongbook/review".equals(route))
            return wrongbookReview(body == null ? new JSONObject() : body);
        if ("/stats/daily".equals(route)) return statsDaily();
        if ("/stats/book".equals(route)) return bookStats(body == null ? null : body.optString("book", null));
        if ("/progress/sync".equals(route)) return progressSync(body == null ? new JSONObject() : body);
        if ("/progress/finish".equals(route))
            return progressFinish(body == null ? new JSONObject() : body);
        if ("/progress/reset".equals(route)) return progressReset();
        if ("/enemy/spawn".equals(route)) return enemySpawn(body == null ? new JSONObject() : body);
        if ("/reward/xp".equals(route)) return rewardXp(body == null ? new JSONObject() : body);
        if ("/card/draw".equals(route)) return cardDraw(body == null ? new JSONObject() : body);
        if ("/shop/buy".equals(route)) return shopBuy(body == null ? new JSONObject() : body);
        if ("/shop/souls".equals(route)) return shopSouls();
        if ("/altar/upgrade".equals(route)) return altarUpgrade(body == null ? new JSONObject() : body);
        throw new RuntimeException("未知路由: " + route);
    }

    private JSONObject guest() throws Exception {
        store.ensureLocalUser();
        JSONObject o = new JSONObject();
        o.put("token", store.ensureLocalUser());
        o.put("user_id", ProgressStore.LOCAL_USER_ID);
        o.put("name", "词灵使");
        return o;
    }

    private JSONObject bankMeta() throws Exception {
        JSONObject meta = WordBank.get().meta;
        JSONObject o = new JSONObject();
        o.put("books", meta.getJSONArray("books"));
        o.put("difficulties", meta.getJSONArray("difficulties"));
        o.put("counts", meta.getJSONObject("counts"));
        o.put("total", meta.optInt("total", 7388));
        o.put("source", meta.optString("source", "ECDICT"));
        return o;
    }

    private JSONObject draw(JSONObject b) throws Exception {
        int layer = b.optInt("layer", 1);
        String force = b.isNull("force") ? null : b.optString("force", null);
        String book = b.isNull("book") ? null : b.optString("book", null);
        Integer difficulty = (b.has("difficulty") && !b.isNull("difficulty"))
                ? b.optInt("difficulty") : null;
        boolean adaptive = b.optBoolean("adaptive", true);
        String mode = b.optString("mode", "campaign");
        String quickLevel = b.isNull("quick_level") ? null : b.optString("quick_level", null);
        boolean enemyConfuse = b.optBoolean("enemy_confuse", false);
        RunState runState = RunState.fromJson(b.optJSONObject("run_state"));

        Map<String, WordRec> progress = store.loadProgress();
        long nowSec = System.currentTimeMillis() / 1000;
        int newToday = store.newToday(nowSec / 86400 * 86400);

        Quiz q;
        if ("quick".equals(mode) && quickLevel != null) {
            q = new QuickEngine(quickLevel).draw(progress, book, runState, System.currentTimeMillis());
        } else {
            QuizEngine.DrawArgs a = new QuizEngine.DrawArgs();
            a.layer = layer;
            a.progress = progress;
            a.force = force;
            a.book = book;
            a.difficulty = difficulty;
            a.theta = adaptive ? store.getTheta() : null;
            a.newToday = newToday;
            a.runState = runState;
            a.mode = mode;
            a.confuse = enemyConfuse;
            // V13.5 英英模式：从 Settings 持久化开关读取（默认关）；关闭时永不产生 en2en 题
            a.en2en = ctx.getSharedPreferences("wd_ui", android.content.Context.MODE_PRIVATE)
                    .getBoolean("en2en_on", false);
            q = quizEngine.draw(a);
        }
        return q.toJson();
    }

    private JSONObject answer(JSONObject b) throws Exception {
        String word = b.optString("word");
        String qtype = b.optString("qtype");
        String answer = b.optString("answer", "");
        double timeLimit = b.optDouble("time_limit", 6);
        double remain = b.optDouble("remain", 0);
        int combo = b.optInt("combo", 0);
        boolean crit = b.optBoolean("crit", false);
        double critMult = b.optDouble("crit_mult", 2.0);
        double cardMult = b.optDouble("card_mult", 1.0);
        // 卡效果入参（R2-A）：暴击率（未显式指定 crit 时由后端掷骰）/汲词回血比例
        double critChance = b.optDouble("crit_chance", 0.0);
        double lifestealPct = b.optDouble("lifesteal_pct", 0.0);
        // 击杀即入账：本答是否为击杀 + 敌人类型（普通15/精英35/Boss60，冠军×1.5）
        boolean kill = b.optBoolean("kill", false);
        String killKind = b.optString("kill_kind", "normal");
        boolean champion = b.optBoolean("champion", false);
        String shown = b.isNull("shown") ? null : b.optString("shown", null);
        String mode = b.optString("mode", "campaign");
        String quickLevel = b.isNull("quick_level") ? null : b.optString("quick_level", null);
        RunState runState = RunState.fromJson(b.optJSONObject("run_state"));

        WordEntry entry = WordBank.get().entry(word);
        if (entry == null) throw new RuntimeException("词条不存在: " + word);

        // V13.4 form 题：本次考察的形名键（past/ing/third/plural/comparative/superlative），
        // 正确答案 = 该词词形表中对应形（服务端按 word+形名权威判定，不信前端）。
        String formKey = b.optString("form_key", "");

        boolean isCorrect;
        if ("judge".equals(qtype)) {
            String truth = entry.m.equals(shown) ? "yes" : "no";
            isCorrect = truth.equals(answer);
        } else if ("form".equals(qtype)) {
            String truth = entry.x == null ? "" : entry.x.optString(formKey, "");
            isCorrect = !truth.isEmpty()
                    && answer.trim().toLowerCase().equals(truth.trim().toLowerCase());
        } else {
            String correctVal = "en2cn".equals(qtype) ? entry.m : entry.w;
            isCorrect = answer.trim().toLowerCase().equals(correctVal.trim().toLowerCase());
        }

        boolean fast = isCorrect && DamageCalc.isFast(qtype, timeLimit, remain);
        long nowMs = System.currentTimeMillis();
        long nowSec = nowMs / 1000;

        if (!isCorrect) runState.markWrong(word);
        else runState.correctOnce.put(word, true);   // V12.1：本局答对记录（单局冷却去重）

        Map<String, WordRec> progress = store.loadProgress();
        WordRec oldRec = progress.get(word);
        String state = oldRec == null ? "NEW" : oldRec.state;
        // 复仇倍率用单局错过次数（V7.0 G.wrongCount[word]），非累计 wrong_count
        int runMisses = runState.runMisses(word);

        // V12.9 词魂祭坛：读永久祝福（词锋+基础伤害、贪婪+产出），账号级权威数据
        JSONObject meta = store.getMeta();
        JSONObject altar = meta.optJSONObject("altar");
        if (altar == null) altar = new JSONObject();
        int edgeLv = altar.optInt("edge", 0);
        int greedLv = altar.optInt("greed", 0);
        int saintLv = altar.optInt("saint", 0);   // V13.0 彩蛋圣辉：产出 +15%
        int baseDamage = GameConfig.BASE_DAMAGE + edgeLv;

        int dmg = 0;
        int healBack = 0;
        DamageCalc.Result dr = null;
        if (isCorrect) {
            if (!crit && critChance > 0 && WordBank.get().rnd.nextDouble() < critChance) crit = true;
            dr = DamageCalc.calc(word, state, combo, qtype, timeLimit, remain,
                    runMisses, crit, critMult, cardMult, baseDamage);
            dmg = dr.total;
            if (lifestealPct > 0) healBack = (int) Math.round(dmg * lifestealPct);
        }

        // 词魂局内制（V11.8）：击杀词魂不再写入持久钱包，改为返回局内增量 soul_gain
        int souls = b.optInt("souls", 0);
        int soulGain = 0;
        if (isCorrect && kill && !"quick".equals(mode)) {
            int reward = "boss".equals(killKind) ? 60 : "elite".equals(killKind) ? 35 : 15;
            if (champion) reward = Math.round(reward * 1.5f);
            // V12.9 击杀质量加成：拼写击杀 ×1.2；3 秒内 PERFECT 击杀 ×1.3（不叠加）
            double quality = "spell".equals(qtype) ? 1.2 : (fast ? 1.3 : 1.0);
            // V12.9 贪婪：战魂产出 +8%/级；V13.0 圣辉：额外 +15%
            reward = (int) Math.round(reward * quality
                    * (1.0 + 0.08 * greedLv + 0.15 * saintLv));
            soulGain = reward;
            souls += reward;
        }

        WordRec newRec = oldRec == null ? new WordRec() : oldRec;
        // V13.2 毕业跃迁检测：schedule 会原地修改 newRec（==oldRec），必须在调度前捕获旧毕业状态，
        // 才能区分「本次刚毕业」与「早已毕业」（仅返回 graduated 不足以区分）。
        boolean wasGraduated = oldRec != null && oldRec.graduated;
        SrsEngine.schedule(newRec, isCorrect, fast, nowMs);

        if ("quick".equals(mode) && quickLevel != null) {
            // V7.0：wrong_by 仅快刷记录；答对（任意档）若无任何错误记录则置 quick_pass
            if (!isCorrect) QuickEngine.markQuickWrong(newRec, quickLevel, qtype);
            else QuickEngine.markQuickPass(newRec);
            // V12.7.14 局内进阶：答对且未毕业，排入进阶队列（去重、上限），同一局内出更高阶梯题
            if (isCorrect && !newRec.graduated
                    && !runState.advQueue.contains(word)
                    && runState.advQueue.size() < GameConfig.QUICK_ADV_QUEUE_MAX) {
                runState.advQueue.add(word);
            }
        }

        double newTheta = SrsEngine.updateTheta(store.getTheta(), isCorrect, fast);
        store.setTheta(newTheta);
        int intervalMin = (int) Math.max(1, (newRec.due - nowMs) / 60_000L);

        progress.put(word, newRec);
        store.upsertRec(word, newRec, nowSec);
        store.insertAnswer(word, qtype, isCorrect, dmg, nowSec);

        // XP 与成就（meta 已在伤害计算前读取，含祭坛等级）
        boolean isNewWord = oldRec == null;
        int xpGain = isCorrect ? (XP_CORRECT + (isNewWord ? XP_NEW_WORD : 0)) : 0;
        if (isCorrect) {
            addXp(meta, xpGain);
        }
        checkAch(meta, combo, isNewWord, qtype, isCorrect);
        store.saveMeta(meta);

        JSONObject out = new JSONObject();
        out.put("correct", isCorrect);
        out.put("fast", fast);
        out.put("damage", dmg);
        out.put("detail", dr == null ? new JSONObject() : dr.detail);
        // V11.5 对错反馈一致性：judge 返回 "yes"/"no" 供前端高亮对/错按钮
        // V12.9.1 修复：cn2en（选英文单词）必须返回正确单词 entry.w 与选项值一致，
        //   否则前端 highlightOptions 的 equals 匹配失败，答对选项无法高亮为绿色
        String correctAnswerVal;
        if ("form".equals(qtype)) {
            // V13.4：返回正确变形词形，前端用于绿框高亮与「正确单词：xxx」反馈
            correctAnswerVal = entry.x == null ? "" : entry.x.optString(formKey, "");
        } else if ("spell".equals(qtype)) {
            correctAnswerVal = entry.w;
        } else if ("judge".equals(qtype)) {
            correctAnswerVal = entry.m.equals(shown) ? "yes" : "no";
        } else if ("cn2en".equals(qtype) || "en2en".equals(qtype)) {
            // V13.5 en2en：题干是英文释义，正确答案仍是该单词本身
            correctAnswerVal = entry.w;
        } else {
            correctAnswerVal = entry.m;
        }
        out.put("correct_answer", correctAnswerVal);
        // V12.4：判断题答错也要告诉正确释义——补充返回该词真实释义，前端展示"对/错：释义"
        if ("judge".equals(qtype)) out.put("correct_mean", entry.m);
        out.put("state", newRec.state);
        out.put("wrong_count", newRec.wrongCount);
        out.put("grade", newRec.wrongCount > 0 ? SrsEngine.gradeOf(newRec.wrongCount) : 0);
        // V13.1 错词消化闭环：本局错词在主循环中被重新答对（复仇/攻克）的权威信号，前端据此给「已攻克」反馈
        out.put("digested", isCorrect && Boolean.TRUE.equals(runState.wrong.get(word)));
        // V13.2 毕业跃迁：未毕业词在本次答对后走完4步阶梯（拼写压轴）首次毕业，前端据此触发轻量小庆祝
        out.put("just_graduated", isCorrect && !wasGraduated && newRec.graduated);
        out.put("theta", newTheta);
        out.put("due_min", intervalMin);
        out.put("step", newRec.step);
        out.put("graduated", newRec.graduated);
        out.put("stage", newRec.stage);
        out.put("run_state", runState.toJson());
        out.put("xp", meta.optInt("xp", 0));
        out.put("xp_gained", xpGain);
        out.put("level", meta.optInt("level", 1));
        out.put("crit", crit);
        out.put("heal_back", healBack);
        out.put("souls", souls);
        out.put("soul_gain", soulGain);
        out.put("new_ach", meta.optJSONArray("new_ach") == null ? new JSONArray() : meta.optJSONArray("new_ach"));
        meta.remove("new_ach");
        return out;
    }

    /** XP 累加与升级（xpNeeded = 120 * level）。 */
    private void addXp(JSONObject meta, int n) throws Exception {
        int xp = meta.optInt("xp", 0) + n;
        int level = meta.optInt("level", 1);
        while (xp >= XP_BASE * level) {
            xp -= XP_BASE * level;
            level++;
        }
        meta.put("xp", xp);
        meta.put("level", level);
    }

    /** 战利品 XP 奖励：翻牌抽到 XP 时持久化加经验（V11.3）。 */
    private JSONObject rewardXp(JSONObject b) throws Exception {
        int amount = b.optInt("amount", 0);
        JSONObject meta = store.getMeta();
        if (amount > 0) addXp(meta, amount);
        store.saveMeta(meta);
        JSONObject o = new JSONObject();
        o.put("xp", meta.optInt("xp", 0));
        o.put("level", meta.optInt("level", 1));
        return o;
    }

    /** 成就解锁：combo10 / master30 / master60。 */
    private void checkAch(JSONObject meta, int combo, boolean isNewWord, String qtype, boolean isCorrect) throws Exception {
        if (meta == null) return;
        if (combo >= 10) unlock(meta, "combo10");
        int mastered = store.masteredCount();
        if (mastered >= 30) unlock(meta, "master30");
        if (mastered >= 60) unlock(meta, "master60");
    }

    private boolean unlock(JSONObject meta, String id) throws Exception {
        JSONArray ach = meta.optJSONArray("ach") == null ? new JSONArray() : meta.optJSONArray("ach");
        for (int i = 0; i < ach.length(); i++) {
            if (id.equals(ach.optString(i))) return false;
        }
        ach.put(id);
        meta.put("ach", ach);
        JSONArray n = meta.optJSONArray("new_ach") == null ? new JSONArray() : meta.optJSONArray("new_ach");
        n.put(id);
        meta.put("new_ach", n);
        return true;
    }

    private JSONObject reviewDue(String book) throws Exception {
        Map<String, WordRec> progress = store.loadProgress();
        long nowMs = System.currentTimeMillis();
        long nowSec = nowMs / 1000;
        List<String> dl = SrsEngine.dueLearning(progress, nowMs);
        List<String> dg = SrsEngine.dueGraduated(progress, nowMs);
        int newToday = store.newToday(nowSec / 86400 * 86400);
        JSONObject out = new JSONObject();
        out.put("due_learning", new JSONArray(dl));
        out.put("due_graduated", new JSONArray(dg));
        out.put("learning_count", dl.size());
        out.put("review_count", dg.size());
        out.put("new_today", newToday);
        out.put("theta", store.getTheta());
        out.put("new_cap", 20);
        out.put("target_rate", 0.85);
        return out;
    }

    private JSONObject wrongbook() throws Exception {
        Map<String, WordRec> progress = store.loadProgress();
        List<SrsEngine.WrongRow> rows = SrsEngine.wrongBook(progress);
        int hard = 0, easy = 0, nw = 0;
        JSONArray arr = new JSONArray();
        for (SrsEngine.WrongRow r : rows) {
            JSONObject o = new JSONObject();
            o.put("word", r.word);
            o.put("pos", r.pos);
            o.put("mean", r.mean);
            o.put("wrong_count", r.wrongCount);
            o.put("grade", r.grade);
            JSONArray tags = new JSONArray();
            WordRec rec = progress.get(r.word);
            if (rec != null) {
                for (String level : new String[]{"normal", "hard", "hell"}) {
                    Map<String, Integer> row = rec.wrongBy.get(level);
                    if (row == null) continue;
                    for (String qt : new String[]{"judge", "en2cn", "cn2en", "spell"}) {
                        int n = row.getOrDefault(qt, 0);
                        if (n > 0) tags.put(level + "/" + qt + ":" + n);
                    }
                }
            }
            o.put("wrong_by", tags);
            arr.put(o);
            if (r.grade == 3) hard++;
            else if (r.grade == 2) easy++;
            else nw++;
        }
        JSONObject summary = new JSONObject();
        summary.put("total", rows.size());
        summary.put("hard", hard);
        summary.put("easy", easy);
        summary.put("new", nw);
        JSONObject out = new JSONObject();
        out.put("summary", summary);
        out.put("words", arr);
        return out;
    }

    private JSONObject wrongbookReview(JSONObject b) throws Exception {
        String word = b.optString("word");
        boolean remembered = b.optBoolean("remembered", false);
        Map<String, WordRec> progress = store.loadProgress();
        WordRec rec = progress.get(word);
        if (rec == null) rec = new WordRec();
        long nowSec = System.currentTimeMillis() / 1000;

        if (remembered) {
            int[] nm = SrsEngine.nextMemory(rec.state, true);
            rec.state = SrsEngine.stateName(nm[0]);
            rec.due = System.currentTimeMillis() + nm[1] * 60_000L;
            rec.wrongCount = 0;
        } else {
            rec.wrongCount++;
            rec.state = "LEARNING";
            rec.due = System.currentTimeMillis() + 1440 * 60_000L;
        }
        store.upsertRec(word, rec, nowSec);

        JSONObject out = new JSONObject();
        out.put("word", word);
        out.put("state", rec.state);
        out.put("wrong_count", rec.wrongCount);
        out.put("grade", rec.wrongCount > 0 ? SrsEngine.gradeOf(rec.wrongCount) : 0);
        return out;
    }

    /** 打卡（V7.0 markStreak）：仅完成一次远征才记；今天已打过则不重复累计。 */
    private void markStreak(JSONObject meta, long today0Sec) throws Exception {
        long last = meta.optLong("last_day", 0);
        if (last == today0Sec) return;
        long yest0Sec = today0Sec - 86400;
        int streak = last == yest0Sec ? meta.optInt("streak", 0) + 1 : 1;
        meta.put("streak", streak);
        meta.put("last_day", today0Sec);
        if (streak >= 3) unlock(meta, "streak3");
        if (streak >= 7) unlock(meta, "streak7");
        store.saveMeta(meta);
    }

    private JSONObject statsDaily() throws Exception {
        long nowSec = System.currentTimeMillis() / 1000;
        long today0Sec = nowSec / 86400 * 86400;
        int[] s = store.dailyStats(today0Sec);
        JSONObject meta = store.getMeta();
        int streak = meta.optInt("streak", 0);
        Map<String, WordRec> progress = store.loadProgress();
        long nowMs = System.currentTimeMillis();
        int dueLearning = SrsEngine.dueLearning(progress, nowMs).size();
        int dueGraduated = SrsEngine.dueGraduated(progress, nowMs).size();
        int newToday = store.newToday(today0Sec);

        JSONObject daily = new JSONObject();
        daily.put("questions", s[0]);
        daily.put("correct", s[1]);
        daily.put("accuracy", s[0] > 0 ? Math.round((double) s[1] / s[0] * 1000.0) / 10.0 : 0);
        daily.put("new_words", Math.min(newToday, DAILY_NEW_WORDS));
        daily.put("run", store.dailyRun(today0Sec));
        daily.put("questions_goal", DAILY_QUESTIONS);
        daily.put("new_words_goal", DAILY_NEW_WORDS);
        daily.put("run_goal", 1);

        JSONObject out = new JSONObject();
        int metaLevel = meta.optInt("level", 1);
        int metaStreak = meta.optInt("streak", 0);
        out.put("level", metaLevel);
        out.put("xp", meta.optInt("xp", 0));
        out.put("xp_needed", XP_BASE * metaLevel);
        out.put("streak", metaStreak);
        // V13.0：称号/火花阶段（0-based），前端据此派生名称与样式
        out.put("title_stage", GameConfig.titleStage(metaLevel));
        out.put("spark_stage", GameConfig.sparkStage(metaStreak));
        out.put("saint", meta.optJSONObject("altar") != null
                && meta.optJSONObject("altar").optInt("saint", 0) == 1);
        out.put("theta", store.getTheta());
        out.put("endless_best", meta.optInt("endless_best", 0));
        out.put("ach", meta.optJSONArray("ach") == null ? new JSONArray() : meta.optJSONArray("ach"));
        out.put("daily", daily);
        out.put("souls", meta.optInt("souls", 0));
        out.put("due_learning", dueLearning);
        out.put("due_graduated", dueGraduated);
        out.put("new_today", newToday);
        out.put("new_cap", DAILY_NEW_WORDS);
        out.put("target_rate", 0.85);
        out.put("mastered_total", store.masteredCount());
        out.put("best_correct", store.bestRunCorrect());   // P0-4：成就 correct50 进度
        out.put("essence", meta.optInt("essence", 0));
        out.put("altar", meta.optJSONObject("altar") == null ? new JSONObject() : meta.optJSONObject("altar"));
        return out;
    }

    /** 单局结束：每日远征次数、无尽纪录、Boss/通关/无尽成就。 */
    private JSONObject progressFinish(JSONObject b) throws Exception {
        String mode = b.optString("mode", "campaign");
        int layer = b.optInt("layer", 1);
        boolean win = b.optBoolean("win", false);
        int maxCombo = b.optInt("max_combo", 0);
        long nowSec = System.currentTimeMillis() / 1000;
        long today0Sec = nowSec / 86400 * 86400;

        JSONObject meta = store.getMeta();

        // 一局收集词典卡数（V7.0 cards5/cards10）
        int cards = b.optInt("cards", 0);
        if (cards >= 5) unlock(meta, "cards5");
        if (cards >= 10) unlock(meta, "cards10");

        // 累计掌握数成就（master30/60，answer 路径外再补一道幂等解锁）
        int mastered = store.masteredCount();
        if (mastered >= 30) unlock(meta, "master30");
        if (mastered >= 60) unlock(meta, "master60");

        // 词魂已在击杀时（/quiz/answer kill=true）即时入账 DB；此处只读回，不重复发放
        int souls = store.getMeta().optInt("souls", 0);

        if ("endless".equals(mode)) {
            int best = meta.optInt("endless_best", 0);
            if (layer > best) {
                meta.put("endless_best", layer);
                best = layer;
            }
            if (best >= 5) unlock(meta, "endless5");
            if (best >= 10) unlock(meta, "endless10");
        }
        // P0-4：打卡口径扩展为"完成任意模式一局"（远征/无尽/快刷，含中途退出，runs 表已记录）
        store.insertAnswer("__run__", "run", true, 0, nowSec);
        markStreak(meta, today0Sec);
        if ("campaign".equals(mode)) {
            unlock(meta, "first_win");
            if (layer >= 1) unlock(meta, "boss1");
            if (layer >= 2) unlock(meta, "boss2");
            if (layer >= 3) unlock(meta, "boss3");
        }
        if (maxCombo >= 10) unlock(meta, "combo10");
        int correct = b.optInt("correct", 0);
        if (correct >= 50) unlock(meta, "correct50");

        // V12.9 双轨词魂：局内战魂按比例凝为持久精华（一次性，杜绝双计）
        int runSouls = b.optInt("souls", 0);
        int essenceGain;
        if ("quick".equals(mode)) {
            int questions = b.optInt("questions", 0);
            essenceGain = Math.min(15, questions / 10);
        } else if ("endless".equals(mode)) {
            essenceGain = (int) Math.round(runSouls * 0.25);
        } else {
            essenceGain = win ? (int) Math.round(runSouls * 0.30) : (int) Math.round(runSouls * 0.25);
        }
        int essence = meta.optInt("essence", 0) + essenceGain;
        meta.put("essence", essence);
        // V13.0 彩蛋「圣者觉醒」：称号 4 阶（Lv10）+ 火花 4 阶（连续 30 天）首次双满时触发
        boolean saintTriggered = false;
        JSONObject altar = meta.optJSONObject("altar");
        if (altar == null) altar = new JSONObject();
        if (altar.optInt("saint", 0) == 0
                && GameConfig.titleStage(meta.optInt("level", 1)) >= 3
                && GameConfig.sparkStage(meta.optInt("streak", 0)) >= 3) {
            altar.put("saint", 1);
            meta.put("altar", altar);
            unlock(meta, "saint_awakened");
            saintTriggered = true;
        }
        store.saveMeta(meta);

        // V12.8 回顾系统：每局战绩落 runs 表（无论胜负都记）
        int questions = b.optInt("questions", 0);
        int wrong = b.optInt("wrong", 0);
        int kills = b.optInt("kills", 0);
        store.insertRun(mode, win, layer, questions, correct, wrong, maxCombo, kills, runSouls, nowSec);

        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("endless_best", meta.optInt("endless_best", 0));
        out.put("level", meta.optInt("level", 1));
        out.put("xp", meta.optInt("xp", 0));
        out.put("streak", meta.optInt("streak", 0));
        out.put("souls", souls);
        out.put("essence", essence);
        out.put("essence_gained", essenceGain);
        out.put("saint_triggered", saintTriggered);
        out.put("ach", meta.optJSONArray("ach") == null ? new JSONArray() : meta.optJSONArray("ach"));
        return out;
    }

    private JSONObject bookStats(String book) throws Exception {
        if (book == null) throw new RuntimeException("缺少 book 参数");
        Map<String, WordRec> progress = store.loadProgress();
        int total = 0, mastered = 0, seen = 0;
        for (WordEntry e : WordBank.get().words) {
            if (!e.inBook(book)) continue;
            total++;
            WordRec r = progress.get(e.w);
            if (r != null) {
                seen++;
                if ("MASTERED".equals(r.state)) mastered++;
            }
        }
        JSONObject out = new JSONObject();
        out.put("book", book);
        out.put("total", total);
        out.put("mastered", mastered);
        out.put("seen", seen);
        out.put("pct", total > 0 ? Math.round((double) mastered / total * 1000.0) / 10.0 : 0);
        return out;
    }

    private JSONObject progressSync(JSONObject b) throws Exception {
        JSONObject words = b.optJSONObject("words");
        JSONObject merged = new JSONObject();
        if (words != null) {
            Map<String, WordRec> progress = store.loadProgress();
            long nowSec = System.currentTimeMillis() / 1000;
            for (Iterator<String> it = words.keys(); it.hasNext(); ) {
                String word = it.next();
                JSONObject p = words.optJSONObject(word);
                WordRec rec = progress.get(word);
                if (rec == null) rec = new WordRec();
                int clientWc = p == null ? 0 : p.optInt("wrong_count", 0);
                rec.wrongCount = Math.max(rec.wrongCount, clientWc);
                if (p != null && p.has("state")) rec.state = p.optString("state", rec.state);
                store.upsertRec(word, rec, nowSec);
                JSONObject m = new JSONObject();
                m.put("state", rec.state);
                m.put("wrong_count", rec.wrongCount);
                merged.put(word, m);
            }
        }
        JSONObject out = new JSONObject();
        out.put("merged", merged);
        return out;
    }

    /** 清空所有学习进度（设置页入口）。 */
    private JSONObject progressReset() throws Exception {
        store.resetAll();
        JSONObject out = new JSONObject();
        out.put("ok", true);
        return out;
    }

    private JSONObject enemySpawn(JSONObject b) throws Exception {
        String mode = b.optString("mode", "campaign");
        int layer = b.optInt("layer", 1);
        String quickLevel = b.isNull("quick_level") ? null : b.optString("quick_level", null);
        JSONObject o;
        if ("quick".equals(mode) && quickLevel != null) {
            o = enemy.spawnQuick(quickLevel);
        } else if ("campaign".equals(mode)) {
            // V12.0：前端只下发 enc（本层第几场，0 起）；后端按 MINIONS/CLIMBER 裁决普通/守门者
            int enc = b.optInt("enc", 0);
            int lc = Math.max(1, Math.min(layer, GameConfig.LAYER_COUNT));
            int li = lc - 1;
            int minions = GameConfig.CAMPAIGN_MINIONS[li];
            String climber = GameConfig.CAMPAIGN_CLIMBER[li];
            int total = minions + ("none".equals(climber) ? 0 : 1);
            int encIdx = Math.max(0, Math.min(enc, total - 1));
            String kind = encIdx < minions ? "normal" : climber;
            o = enemy.spawn("campaign", kind, lc);
            o.put("enc_index", encIdx);
            o.put("layer_enc_total", total);
            o.put("is_layer_final", encIdx == total - 1);
            o.put("layer_name", GameConfig.CAMPAIGN_LAYER_NAME[li]);
        } else {
            // 无尽：沿用前端 kind 入参（normal/elite/boss），不按 enc 裁决
            String kind = b.optString("kind", "normal");
            o = enemy.spawn("endless", kind, layer);
        }
        // 本场微目标（V7.0 BATTLE_GOALS 随机抽一）：streak4=连击4 / hit5=答对5 / flawless=无胜
        String[] goals = {"streak4", "hit5", "flawless"};
        o.put("goal", goals[WordBank.get().rnd.nextInt(goals.length)]);
        return o;
    }

    /** 词典卡三槽抽取（V7.0 drawChoice，pity/epicPity 由前端 run 状态传入，返回新计数）。 */
    private JSONObject cardDraw(JSONObject b) throws Exception {
        String kind = b.optString("kind", "normal");
        int layer = b.optInt("layer", 1);
        boolean endless = "endless".equals(b.optString("mode", ""));
        int pity = b.optInt("pity", 0);
        int epicPity = b.optInt("epic_pity", 0);
        JSONArray owned = b.optJSONArray("cards");
        List<String> ownedIds = new java.util.ArrayList<>();
        if (owned != null) for (int i = 0; i < owned.length(); i++) ownedIds.add(owned.optString(i));
        // V12.9 先知：祭坛 seer 等级 → 翻牌额外槽位
        JSONObject altarNow = store.getMeta().optJSONObject("altar");
        int seerNow = altarNow == null ? 0 : altarNow.optInt("seer", 0);
        JSONObject out = CardCatalog.draw(kind, layer, endless, pity, epicPity, ownedIds, seerNow, WordBank.get().rnd);
        out.put("catalog", CardCatalog.catalog());
        return out;
    }

    /** 读回词魂余额（冷启/进商店/抽卡前对账）。 */
    private JSONObject shopSouls() throws Exception {
        JSONObject o = new JSONObject();
        o.put("souls", store.getMeta().optInt("souls", 0));
        return o;
    }

    /** V12.9 祭坛升级：校验精华、扣精华、对应祝福 +1，持久化 meta。 */
    private JSONObject altarUpgrade(JSONObject b) throws Exception {
        String id = b.optString("id", "");
        JSONObject meta = store.getMeta();
        JSONObject altar = meta.optJSONObject("altar");
        if (altar == null) altar = new JSONObject();
        int cur = altar.optInt(id, 0);
        int max = CardCatalog.altarMax(id);
        int cost = CardCatalog.altarCost(id, cur);
        JSONObject out = new JSONObject();
        if (max <= 0 || cost < 0) {
            out.put("ok", false);
            out.put("reason", "maxed");
            return out;
        }
        int essence = meta.optInt("essence", 0);
        if (essence < cost) {
            out.put("ok", false);
            out.put("reason", "essence_not_enough");
            out.put("essence", essence);
            return out;
        }
        essence -= cost;
        altar.put(id, cur + 1);
        meta.put("essence", essence);
        meta.put("altar", altar);
        store.saveMeta(meta);
        out.put("ok", true);
        out.put("id", id);
        out.put("level", cur + 1);
        out.put("essence", essence);
        out.put("altar", altar);
        return out;
    }

    /** 商店购买：扣词魂并返回余额；血药/祝福为道具，卡按 rarity 发卡数据。价格支持前端随层数上浮（price 参数优先，缺省查表）。 */
    private JSONObject shopBuy(JSONObject b) throws Exception {
        String item = b.optString("item", "");
        int rarity = b.optInt("rarity", 0);
        int price;
        switch (item) {
            case "potion": price = CardCatalog.PRICE_POTION; break;
            case "bless":  price = CardCatalog.PRICE_BLESS; break;
            case "sacrifice": price = CardCatalog.PRICE_SACRIFICE; break;
            case "card":
                price = rarity == 2 ? CardCatalog.PRICE_CARD_EPIC
                        : rarity == 1 ? CardCatalog.PRICE_CARD_RARE : CardCatalog.PRICE_CARD_COMMON;
                break;
            case "reroll": price = CardCatalog.REROLL_COST; break;
            default: throw new RuntimeException("未知商品: " + item);
        }
        if (b.has("price")) price = b.optInt("price", price);   // V12.8.3 商店价格随层数上浮，前端计算并传回
        // 局内制：余额由前端请求携带，后端只做校验与扣减，不持久化
        int souls = b.optInt("souls", 0);
        if (souls < price) {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("reason", "souls_not_enough");
            o.put("souls", souls);
            return o;
        }
        souls -= price;
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("item", item);
        o.put("price", price);
        o.put("souls", souls);
        if ("sacrifice".equals(item)) o.put("damage", CardCatalog.SACRIFICE_DAMAGE);
        if ("card".equals(item)) {
            // 商店直购卡：按稀有度从目录抽一张返回卡数据
            CardCatalog.Card c = CardCatalog.byId(randomCardId(rarity));
            if (c != null) o.put("card", c.toJson());
        }
        return o;
    }

    private String randomCardId(int rarity) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < CardCatalog.catalog().length(); i++) {
            JSONObject co = CardCatalog.catalog().optJSONObject(i);
            if (co != null && co.optInt("rarity") == rarity) ids.add(co.optString("id"));
        }
        return ids.isEmpty() ? "" : ids.get(WordBank.get().rnd.nextInt(ids.size()));
    }
}
