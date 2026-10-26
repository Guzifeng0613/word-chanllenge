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
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 本地 HTTP 服务（对应 FastAPI main.py，路由对齐）
 * 127.0.0.1 本地服务，完全离线；前端通过 fetch 调用。
 */
public class LocalServer extends NanoHTTPD {

    private final Context ctx;
    private final ProgressStore store;
    private final EnemyModel enemy;
    private final QuizEngine quizEngine;

    public LocalServer(Context ctx, int port) {
        super("127.0.0.1", port);
        this.ctx = ctx;
        WordBank.init(ctx);
        this.store = ProgressStore.get(ctx);
        this.store.ensureLocalUser();
        this.enemy = new EnemyModel();
        this.quizEngine = new QuizEngine();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        try {
            // 公开路由
            if (Method.POST.equals(method) && "/auth/guest".equals(uri)) return guest();
            if (Method.GET.equals(method) && "/bank/meta".equals(uri)) return bankMeta();

            // 鉴权
            String token = token(session);
            if (!store.validToken(token)) return json(Response.Status.UNAUTHORIZED, err("无效 token"));

            if (Method.POST.equals(method) && "/quiz/draw".equals(uri))
                return draw(readJson(session));
            if (Method.POST.equals(method) && "/quiz/answer".equals(uri))
                return answer(readJson(session));
            if (Method.GET.equals(method) && "/review/due".equals(uri))
                return reviewDue(session.getParms().get("book"));
            if (Method.GET.equals(method) && "/wrongbook".equals(uri))
                return wrongbook();
            if (Method.POST.equals(method) && "/wrongbook/review".equals(uri))
                return wrongbookReview(readJson(session));
            if (Method.GET.equals(method) && "/stats/daily".equals(uri))
                return statsDaily();
            if (Method.GET.equals(method) && "/stats/book".equals(uri))
                return bookStats(session.getParms().get("book"));
            if (Method.POST.equals(method) && "/progress/sync".equals(uri))
                return progressSync(readJson(session));
            if (Method.POST.equals(method) && "/enemy/spawn".equals(uri))
                return enemySpawn(readJson(session));

            return json(Response.Status.NOT_FOUND, err("未知路由"));
        } catch (Exception e) {
            return json(Response.Status.INTERNAL_ERROR, err("服务异常: " + e.getMessage()));
        }
    }

    // ---------------- 基础工具 ----------------
    private static JSONObject readJson(IHTTPSession session) throws Exception {
        Map<String, String> body = new HashMap<>();
        session.parseBody(body);
        String data = body.get("postData");
        return data == null ? new JSONObject() : new JSONObject(data);
    }

    private static String token(IHTTPSession session) {
        String h = session.getHeaders().get("authorization");
        if (h != null && h.startsWith("Bearer ")) return h.substring(7);
        return null;
    }

    private static JSONObject err(String msg) {
        JSONObject o = new JSONObject();
        try { o.put("detail", msg); } catch (Exception ignore) {}
        return o;
    }

    private Response json(Response.Status status, JSONObject o) {
        Response r = newFixedLengthResponse(status, "application/json", o.toString());
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    // ---------------- 认证 ----------------
    private Response guest() {
        String token = store.ensureLocalUser();
        JSONObject o = new JSONObject();
        try {
            o.put("token", token);
            o.put("user_id", ProgressStore.LOCAL_USER_ID);
            o.put("name", "词灵使");
        } catch (Exception ignore) {}
        return json(Response.Status.OK, o);
    }

    // ---------------- 词库 ----------------
    private Response bankMeta() {
        JSONObject o = new JSONObject();
        try {
            JSONObject meta = WordBank.get().meta;
            o.put("books", meta.getJSONArray("books"));
            o.put("difficulties", meta.getJSONArray("difficulties"));
            o.put("counts", meta.getJSONObject("counts"));
        } catch (Exception ignore) {}
        return json(Response.Status.OK, o);
    }

    // ---------------- 出题 ----------------
    private Response draw(JSONObject b) {
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
            QuickEngine qe = new QuickEngine(quickLevel);
            q = qe.draw(progress, book, runState, System.currentTimeMillis());
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
        return json(Response.Status.OK, q.toJson());
    }

    // ---------------- 答题 ----------------
    private Response answer(JSONObject b) {
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
        if (entry == null) return json(Response.Status.NOT_FOUND, err("词条不存在"));

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
        try {
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
        } catch (Exception ignore) {}
        return json(Response.Status.OK, out);
    }

    // ---------------- 到期复习 ----------------
    private Response reviewDue(String book) {
        Map<String, WordRec> progress = store.loadProgress();
        long nowMs = System.currentTimeMillis();
        long nowSec = nowMs / 1000;
        List<String> dl = SrsEngine.dueLearning(progress, nowMs);
        List<String> dg = SrsEngine.dueGraduated(progress, nowMs);
        int newToday = store.newToday(nowSec / 86400 * 86400);
        JSONObject out = new JSONObject();
        try {
            out.put("due_learning", new JSONArray(dl));
            out.put("due_graduated", new JSONArray(dg));
            out.put("learning_count", dl.size());
            out.put("review_count", dg.size());
            out.put("new_today", newToday);
            out.put("theta", store.getTheta());
            out.put("new_cap", 20);
            out.put("target_rate", 0.85);
        } catch (Exception ignore) {}
        return json(Response.Status.OK, out);
    }

    // ---------------- 错词本 ----------------
    private Response wrongbook() {
        Map<String, WordRec> progress = store.loadProgress();
        List<SrsEngine.WrongRow> rows = SrsEngine.wrongBook(progress);
        JSONObject out = new JSONObject();
        try {
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
            out.put("summary", summary);
            out.put("words", arr);
        } catch (Exception ignore) {}
        return json(Response.Status.OK, out);
    }

    private Response wrongbookReview(JSONObject b) {
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
        try {
            out.put("word", word);
            out.put("state", rec.state);
            out.put("wrong_count", rec.wrongCount);
            out.put("grade", rec.wrongCount > 0 ? SrsEngine.gradeOf(rec.wrongCount) : 0);
        } catch (Exception ignore) {}
        return json(Response.Status.OK, out);
    }

    // ---------------- 统计 ----------------
    private Response statsDaily() {
        long nowSec = System.currentTimeMillis() / 1000;
        int[] s = store.dailyStats(nowSec / 86400 * 86400);
        JSONObject out = new JSONObject();
        try {
            out.put("questions", s[0]);
            out.put("correct", s[1]);
            out.put("accuracy", s[0] > 0 ? Math.round((double) s[1] / s[0] * 1000.0) / 1000.0 : 0);
            out.put("daily_goal", 40);
        } catch (Exception ignore) {}
        return json(Response.Status.OK, out);
    }

    // ---------------- 词书掌握统计 ----------------
    private Response bookStats(String book) {
        JSONObject out = new JSONObject();
        if (book == null) return json(Response.Status.BAD_REQUEST, err("缺少 book 参数"));
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
        try {
            out.put("book", book);
            out.put("total", total);
            out.put("mastered", mastered);
            out.put("seen", seen);
            out.put("pct", total > 0 ? Math.round((double) mastered / total * 1000.0) / 10.0 : 0);
        } catch (Exception ignore) {}
        return json(Response.Status.OK, out);
    }

    // ---------------- 进度同步（本地以更高错误次数合并） ----------------
    private Response progressSync(JSONObject b) {
        JSONObject words = b.optJSONObject("words");
        JSONObject merged = new JSONObject();
        if (words != null) {
            Map<String, WordRec> progress = store.loadProgress();
            long nowSec = System.currentTimeMillis() / 1000;
            for (java.util.Iterator<String> it = words.keys(); it.hasNext(); ) {
                String word = it.next();
                JSONObject p = words.optJSONObject(word);
                WordRec rec = progress.get(word);
                if (rec == null) rec = new WordRec();
                int clientWc = p == null ? 0 : p.optInt("wrong_count", 0);
                rec.wrongCount = Math.max(rec.wrongCount, clientWc);
                if (p != null && p.has("state")) rec.state = p.optString("state", rec.state);
                store.upsertRec(word, rec, nowSec);
                JSONObject m = new JSONObject();
                try {
                    m.put("state", rec.state);
                    m.put("wrong_count", rec.wrongCount);
                    merged.put(word, m);
                } catch (Exception ignore) {}
            }
        }
        JSONObject out = new JSONObject();
        try { out.put("merged", merged); } catch (Exception ignore) {}
        return json(Response.Status.OK, out);
    }

    // ---------------- 敌人生成 ----------------
    private Response enemySpawn(JSONObject b) {
        String mode = b.optString("mode", "campaign");
        String kind = b.optString("kind", "normal");
        int layer = b.optInt("layer", 1);
        String quickLevel = b.isNull("quick_level") ? null : b.optString("quick_level", null);
        JSONObject e;
        if ("quick".equals(mode) && quickLevel != null) e = enemy.spawnQuick(quickLevel);
        else e = enemy.spawn(mode, kind, layer);
        return json(Response.Status.OK, e);
    }
}
