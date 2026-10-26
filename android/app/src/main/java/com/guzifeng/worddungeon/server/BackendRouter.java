package com.guzifeng.worddungeon.server;

import android.content.Context;

import com.guzifeng.worddungeon.core.DamageCalc;
import com.guzifeng.worddungeon.core.EnemyModel;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后端路由分发（V10.1：对齐 V7.0 前端数据口径）
 * 新增：等级/XP/连续打卡/成就/每日目标/错词分级标签（wrong_by）/无尽纪录。
 */
public class BackendRouter {

    public static final int DAILY_QUESTIONS = 40;
    public static final int DAILY_NEW_WORDS = 15;
    public static final int XP_CORRECT = 2;
    public static final int XP_NEW_WORD = 5;
    public static final int XP_MASTER = 10;
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
        String shown = b.isNull("shown") ? null : b.optString("shown", null);
        String mode = b.optString("mode", "campaign");
        String quickLevel = b.isNull("quick_level") ? null : b.optString("quick_level", null);
        RunState runState = RunState.fromJson(b.optJSONObject("run_state"));

        WordEntry entry = WordBank.get().entry(word);
        if (entry == null) throw new RuntimeException("词条不存在: " + word);

        boolean isCorrect;
        if ("judge".equals(qtype)) {
            String truth = entry.m.equals(shown) ? "yes" : "no";
            isCorrect = truth.equals(answer);
        } else {
            String correctVal = "en2cn".equals(qtype) ? entry.m : entry.w;
            isCorrect = answer.trim().toLowerCase().equals(correctVal.trim().toLowerCase());
        }

        boolean fast = isCorrect && DamageCalc.isFast(qtype, timeLimit, remain);
        long nowMs = System.currentTimeMillis();
        long nowSec = nowMs / 1000;

        if (!isCorrect) runState.markWrong(word);

        Map<String, WordRec> progress = store.loadProgress();
        WordRec oldRec = progress.get(word);
        String state = oldRec == null ? "NEW" : oldRec.state;
        int wrongCount = oldRec == null ? 0 : oldRec.wrongCount;

        int dmg = 0;
        DamageCalc.Result dr = null;
        if (isCorrect) {
            dr = DamageCalc.calc(word, state, combo, qtype, timeLimit, remain,
                    wrongCount, crit, critMult, cardMult);
            dmg = dr.total;
        }

        WordRec newRec = oldRec == null ? new WordRec() : oldRec;
        SrsEngine.schedule(newRec, isCorrect, fast, nowMs);

        if ("quick".equals(mode) && quickLevel != null) {
            if (!isCorrect) QuickEngine.markQuickWrong(newRec, quickLevel, qtype);
            else if ("normal".equals(quickLevel)) QuickEngine.markQuickPass(newRec);
        } else if (!isCorrect) {
            // 远征/无尽模式的错过计入 normal 档，供错词本“错过地点”标签展示
            Map<String, Integer> row = newRec.wrongBy.get("normal");
            if (row == null) {
                row = new HashMap<>();
                newRec.wrongBy.put("normal", row);
            }
            row.put(qtype, row.getOrDefault(qtype, 0) + 1);
        }

        double newTheta = SrsEngine.updateTheta(store.getTheta(), isCorrect, fast);
        store.setTheta(newTheta);
        int intervalMin = (int) Math.max(1, (newRec.due - nowMs) / 60_000L);

        progress.put(word, newRec);
        store.upsertRec(word, newRec, nowSec);
        store.insertAnswer(word, qtype, isCorrect, dmg, nowSec);

        // XP 与成就
        JSONObject meta = store.getMeta();
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
        out.put("correct_answer", "spell".equals(qtype) ? entry.w : entry.m);
        out.put("state", newRec.state);
        out.put("wrong_count", newRec.wrongCount);
        out.put("grade", newRec.wrongCount > 0 ? SrsEngine.gradeOf(newRec.wrongCount) : 0);
        out.put("theta", newTheta);
        out.put("due_min", intervalMin);
        out.put("step", newRec.step);
        out.put("graduated", newRec.graduated);
        out.put("stage", newRec.stage);
        out.put("run_state", runState.toJson());
        out.put("xp", meta.optInt("xp", 0));
        out.put("xp_gained", xpGain);
        out.put("level", meta.optInt("level", 1));
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

    /** 每日打卡：今日首启时按昨天是否活跃续/断 streak。 */
    private void ensureDaily(JSONObject meta, long today0Sec) throws Exception {
        long last = meta.optLong("last_day", 0);
        if (last == today0Sec) return;
        long yest0Sec = today0Sec - 86400;
        int streak = meta.optInt("streak", 0);
        if (last == yest0Sec) streak++;
        else streak = 1;
        meta.put("streak", streak);
        meta.put("last_day", today0Sec);
        store.saveMeta(meta);
    }

    private JSONObject statsDaily() throws Exception {
        long nowSec = System.currentTimeMillis() / 1000;
        long today0Sec = nowSec / 86400 * 86400;
        int[] s = store.dailyStats(today0Sec);
        JSONObject meta = store.getMeta();
        ensureDaily(meta, today0Sec);
        int streak = meta.optInt("streak", 0);
        if (streak >= 3) unlock(meta, "streak3");
        if (streak >= 7) unlock(meta, "streak7");
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
        out.put("level", meta.optInt("level", 1));
        out.put("xp", meta.optInt("xp", 0));
        out.put("xp_needed", XP_BASE * meta.optInt("level", 1));
        out.put("streak", meta.optInt("streak", 0));
        out.put("theta", store.getTheta());
        out.put("endless_best", meta.optInt("endless_best", 0));
        out.put("ach", meta.optJSONArray("ach") == null ? new JSONArray() : meta.optJSONArray("ach"));
        out.put("daily", daily);
        out.put("due_learning", dueLearning);
        out.put("due_graduated", dueGraduated);
        out.put("new_today", newToday);
        out.put("new_cap", DAILY_NEW_WORDS);
        out.put("target_rate", 0.85);
        out.put("mastered_total", store.masteredCount());
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
        ensureDaily(meta, today0Sec);

        // 每日远征目标
        store.insertAnswer("__run__", "run", true, 0, nowSec);

        if ("endless".equals(mode)) {
            int best = meta.optInt("endless_best", 0);
            if (layer > best) {
                meta.put("endless_best", layer);
                best = layer;
            }
            if (best >= 5) unlock(meta, "endless5");
            if (best >= 10) unlock(meta, "endless10");
        }
        if (win) {
            if ("campaign".equals(mode)) {
                unlock(meta, "first_win");
                if (layer >= 1) unlock(meta, "boss1");
                if (layer >= 2) unlock(meta, "boss2");
                if (layer >= 3) unlock(meta, "boss3");
            }
        }
        if (maxCombo >= 10) unlock(meta, "combo10");
        int correct = b.optInt("correct", 0);
        if (correct >= 50) unlock(meta, "correct50");
        store.saveMeta(meta);

        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("endless_best", meta.optInt("endless_best", 0));
        out.put("level", meta.optInt("level", 1));
        out.put("xp", meta.optInt("xp", 0));
        out.put("streak", meta.optInt("streak", 0));
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
        String kind = b.optString("kind", "normal");
        int layer = b.optInt("layer", 1);
        String quickLevel = b.isNull("quick_level") ? null : b.optString("quick_level", null);
        if ("quick".equals(mode) && quickLevel != null) return enemy.spawnQuick(quickLevel);
        return enemy.spawn(mode, kind, layer);
    }
}
