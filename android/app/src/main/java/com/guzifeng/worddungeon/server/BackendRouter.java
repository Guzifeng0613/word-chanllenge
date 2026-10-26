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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 后端路由分发（纯逻辑，不依赖 NanoHTTPD）。
 * 供两条通道共用：① LocalServer（HTTP，浏览器/调试）② WordDungeonPlugin（App 内插件桥，正式通道）。
 * 返回 JSONObject；错误抛 RuntimeException（调用方转成 HTTP 状态或 reject）。
 */
public class BackendRouter {

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
        if ("/enemy/spawn".equals(route)) return enemySpawn(body == null ? new JSONObject() : body);
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
        }

        double newTheta = SrsEngine.updateTheta(store.getTheta(), isCorrect, fast);
        store.setTheta(newTheta);
        int intervalMin = (int) Math.max(1, (newRec.due - nowMs) / 60_000L);

        progress.put(word, newRec);
        store.upsertRec(word, newRec, nowSec);
        store.insertAnswer(word, qtype, isCorrect, dmg, nowSec);

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
        return out;
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

    private JSONObject statsDaily() throws Exception {
        long nowSec = System.currentTimeMillis() / 1000;
        int[] s = store.dailyStats(nowSec / 86400 * 86400);
        JSONObject out = new JSONObject();
        out.put("questions", s[0]);
        out.put("correct", s[1]);
        out.put("accuracy", s[0] > 0 ? Math.round((double) s[1] / s[0] * 1000.0) / 1000.0 : 0);
        out.put("daily_goal", 40);
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

    private JSONObject enemySpawn(JSONObject b) throws Exception {
        String mode = b.optString("mode", "campaign");
        String kind = b.optString("kind", "normal");
        int layer = b.optInt("layer", 1);
        String quickLevel = b.isNull("quick_level") ? null : b.optString("quick_level", null);
        if ("quick".equals(mode) && quickLevel != null) return enemy.spawnQuick(quickLevel);
        return enemy.spawn(mode, kind, layer);
    }
}
