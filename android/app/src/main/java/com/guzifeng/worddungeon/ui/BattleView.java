package com.guzifeng.worddungeon.ui;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;
import com.guzifeng.worddungeon.core.RunState;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 战斗页（全原生）：敌人 / 血量 / 四题型答题 / 计时 / 打击动效 / 复仇 / 层数结算。
 * 直接内存调用 BackendRouter.dispatch，零网络。
 */
public class BattleView extends FrameLayout {

    private final MainActivity act;
    private final Handler main = new Handler(Looper.getMainLooper());

    // 战斗状态
    private String mode;
    private String book;
    private String difficulty;
    private RunState runState = new RunState();
    private int layer = 1;
    private int hp = 100;
    private int combo = 0;
    private int wrongInRun = 0;
    private JSONObject enemy;
    private JSONObject quiz;
    private boolean answering = false;
    private boolean defeated = false;

    // UI
    private TextView topTxt, enemyName, enemyEmoji, playerEmoji, playerName;
    private HpBar enemyHp, playerHp;
    private TextView posTag, qBody, timerTxt;
    private FrameLayout answerArea;
    private LinearLayout timerTrack;
    private TextView feedbackLayer;
    private EditText spellInput;
    private TextView confirmBtn;
    private LinearLayout spellRow;

    private static final Map<String, String> EMOJI = new HashMap<>();
    static {
        EMOJI.put("wordfiend", "👹"); EMOJI.put("confuser", "😈"); EMOJI.put("speller", "🧟");
        EMOJI.put("ghost", "👻"); EMOJI.put("polysem", "🎭");
        EMOJI.put("boss1", "👑"); EMOJI.put("boss2", "🌫"); EMOJI.put("boss3", "🔥");
    }

    public BattleView(Context c) {
        super(c);
        act = (MainActivity) c;
        setBackgroundColor(Ui.BG0);
        build();
    }

    private void build() {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(18), dp(16), dp(16));
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 顶栏
        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        topTxt = Ui.text(getContext(), "远征 · 第1层", 14, Ui.MUT, true);
        TextView quit = Ui.text(getContext(), "✕ 退出", 13, Ui.RED, true);
        quit.setPadding(dp(8), dp(6), dp(8), dp(6));
        quit.setOnClickListener(v -> onBack());
        top.addView(topTxt, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(quit);
        col.addView(top);
        col.addView(spacer(10));

        // 敌区
        LinearLayout ebox = new LinearLayout(getContext());
        ebox.setOrientation(LinearLayout.VERTICAL);
        ebox.setGravity(Gravity.CENTER);
        ebox.setPadding(dp(12), dp(12), dp(12), dp(12));
        ebox.setBackground(Ui.card(16, Ui.CARD, 1, Ui.LINE));
        enemyEmoji = Ui.text(getContext(), "👹", 52, Ui.TXT, false);
        ebox.addView(enemyEmoji);
        enemyName = Ui.text(getContext(), "词魔", 15, Ui.TXT, true);
        ebox.addView(enemyName);
        enemyHp = new HpBar(getContext(), Ui.RED, Ui.CARD2);
        ebox.addView(enemyHp, new LinearLayout.LayoutParams(-1, dp(10)));
        col.addView(ebox);
        col.addView(spacer(8));

        // 玩家区
        LinearLayout pbox = new LinearLayout(getContext());
        pbox.setOrientation(LinearLayout.HORIZONTAL);
        pbox.setGravity(Gravity.CENTER_VERTICAL);
        pbox.setPadding(dp(10), dp(8), dp(10), dp(8));
        pbox.setBackground(Ui.card(12, Ui.CARD2, 0, 0));
        playerEmoji = Ui.text(getContext(), "🧙", 26, Ui.TXT, false);
        playerName = Ui.text(getContext(), "词灵使", 14, Ui.CYAN, true);
        playerHp = new HpBar(getContext(), Ui.GREEN, Ui.CARD);
        pbox.addView(playerEmoji);
        pbox.addView(Ui.spacer(getContext(), 4));
        pbox.addView(playerName);
        pbox.addView(Ui.spacer(getContext(), 6));
        pbox.addView(playerHp, new LinearLayout.LayoutParams(0, dp(8), 1f));
        col.addView(pbox);
        col.addView(spacer(12));

        // 题目卡
        LinearLayout qbox = new LinearLayout(getContext());
        qbox.setOrientation(LinearLayout.VERTICAL);
        qbox.setPadding(dp(14), dp(12), dp(14), dp(12));
        qbox.setBackground(Ui.card(14, Ui.BG1, 1, Ui.LINE));
        posTag = Ui.text(getContext(), "", 12, Ui.GOLD, true);
        qbox.addView(posTag);
        qBody = Ui.text(getContext(), "", 22, Ui.TXT, true);
        qBody.setGravity(Gravity.CENTER);
        qbox.addView(qBody);
        // 计时条
        timerTrack = new LinearLayout(getContext());
        timerTrack.setOrientation(LinearLayout.HORIZONTAL);
        timerTrack.setBackground(Ui.card(999, Ui.CARD2, 0, 0));
        timerTxt = Ui.text(getContext(), "", 11, Ui.MUT, false);
        timerTxt.setGravity(Gravity.CENTER);
        timerTrack.addView(timerTxt, new LinearLayout.LayoutParams(0, -1, 1f));
        qbox.addView(timerTrack, new LinearLayout.LayoutParams(-1, dp(14)));
        col.addView(qbox, new LinearLayout.LayoutParams(-1, -2, 1f));
        col.addView(spacer(10));

        // 答题区
        answerArea = new FrameLayout(getContext());
        answerArea.setBackground(Ui.card(14, Ui.CARD2, 1, Ui.LINE));
        col.addView(answerArea, new LinearLayout.LayoutParams(-1, dp(150)));
        col.addView(spacer(8));

        // 反馈层（飘字/闪字）
        feedbackLayer = new TextView(getContext());
        feedbackLayer.setTextSize(20);
        feedbackLayer.setTextColor(Ui.GOLD);
        feedbackLayer.setGravity(Gravity.CENTER);
        feedbackLayer.setVisibility(GONE);
        addView(feedbackLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    // ── 战斗流程 ─────────────────────────────

    public void startRun() {
        mode = act.mode;
        book = act.book;
        difficulty = act.difficulty;
        runState = new RunState();
        layer = 1;
        hp = 100;
        combo = 0;
        wrongInRun = 0;
        defeated = false;
        spawnEnemy();
        nextQuiz();
    }

    private void spawnEnemy() {
        try {
            JSONObject body = new JSONObject();
            body.put("mode", mode);
            String kind = "quick".equals(mode) ? "quick" : (mode.equals("endless") ? "normal" : layerKind());
            body.put("kind", kind);
            body.put("layer", layer);
            JSONObject e = "quick".equals(mode)
                    ? act.router().dispatch("/enemy/spawn", "POST", bodyWithQuickLevel(body))
                    : act.router().dispatch("/enemy/spawn", "POST", body);
            enemy = e;
            enemyName.setText(e.optString("name", "词魔"));
            enemyEmoji.setText(EMOJI.getOrDefault(e.optJSONObject("tpl").optString("id", ""), "👹"));
            enemyHp.setRatio(1f);
            if (e.optBoolean("champion", false)) enemyName.setTextColor(Ui.GOLD);
            topTxt.setText(modeLabel() + " · 第" + layer + "层");
        } catch (Throwable e) {
            showFeed("敌人召唤失败", Ui.RED);
        }
    }

    private JSONObject bodyWithQuickLevel(JSONObject body) throws Exception {
        body.put("quick_level", "normal");
        return body;
    }

    private String layerKind() {
        if ("endless".equals(mode)) return "normal";
        if (layer == 3) return "boss";
        if (layer == 2) return "elite";
        return "normal";
    }

    private String modeLabel() {
        if ("endless".equals(mode)) return "无尽";
        if ("quick".equals(mode)) return "快刷";
        return "远征";
    }

    private void nextQuiz() {
        answering = true;
        quiz = null;
        try {
            JSONObject body = new JSONObject();
            body.put("layer", layer);
            body.put("mode", mode);
            if (!"all".equals(book)) body.put("book", book);
            body.put("adaptive", "adaptive".equals(difficulty));
            if (!"adaptive".equals(difficulty)) body.put("difficulty", diffLevel());
            body.put("run_state", runState.toJson());
            if ("quick".equals(mode)) body.put("quick_level", "normal");
            quiz = act.router().dispatch("/quiz/draw", "POST", body);
            renderQuiz();
        } catch (Throwable e) {
            showFeed("出题失败：" + e.getMessage(), Ui.RED);
        }
    }

    private int diffLevel() {
        switch (difficulty) {
            case "easy": return 1;
            case "hard": return 2;
            case "hell": return 3;
            default: return 2;
        }
    }

    private void renderQuiz() {
        if (quiz == null) return;
        String qtype = quiz.optString("qtype", "en2cn");
        String word = quiz.optString("word", "");
        String pos = quiz.optString("pos", "");
        String mean = quiz.optString("mean", "");
        posTag.setText(pos.isEmpty() ? "" : pos.toUpperCase() + (quiz.optBoolean("review", false) ? " · 复习" : ""));
        posTag.setVisibility(pos.isEmpty() ? GONE : VISIBLE);
        answerArea.removeAllViews();
        spellRow = null;

        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(10), dp(8), dp(10), dp(8));
        answerArea.addView(col, new LayoutParams(-1, -1));

        switch (qtype) {
            case "judge": {
                qBody.setText(word + "\n" + mean);
                qBody.setTextSize(19);
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView yes = Ui.button(getContext(), "✓ 正确", Ui.GREEN, Ui.BG0, false);
                TextView no = Ui.button(getContext(), "✗ 错误", Ui.RED, Ui.BG0, false);
                yes.setOnClickListener(v -> submitJudge("yes"));
                no.setOnClickListener(v -> submitJudge("no"));
                row.addView(yes, new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(spacer(8));
                row.addView(no, new LinearLayout.LayoutParams(0, -2, 1f));
                col.addView(row);
                break;
            }
            case "en2cn": {
                qBody.setText(word);
                qBody.setTextSize(24);
                addOptions(col, quiz.optJSONArray("options"), true);
                break;
            }
            case "cn2en": {
                qBody.setText(mean);
                qBody.setTextSize(19);
                addOptions(col, quiz.optJSONArray("options"), false);
                break;
            }
            case "spell": {
                qBody.setText(pos + " · " + mean);
                qBody.setTextSize(18);
                spellRow = new LinearLayout(getContext());
                spellRow.setOrientation(LinearLayout.HORIZONTAL);
                spellRow.setGravity(Gravity.CENTER_VERTICAL);
                spellInput = new EditText(getContext());
                spellInput.setSingleLine(true);
                spellInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                spellInput.setTextColor(Ui.TXT);
                spellInput.setHintTextColor(Ui.MUT);
                spellInput.setHint("输入英文拼写，按回车确认");
                spellInput.setBackground(Ui.card(10, Ui.BG0, 1, Ui.LINE));
                spellInput.setPadding(dp(10), dp(8), dp(10), dp(8));
                spellInput.setTextSize(18);
                spellInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
                spellInput.setOnEditorActionListener((v, id, ev) -> {
                    if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_ACTION_GO
                            || id == EditorInfo.IME_ACTION_SEND || id == EditorInfo.IME_ACTION_SEARCH
                            || id == EditorInfo.IME_ACTION_NEXT || id == EditorInfo.IME_ACTION_UNSPECIFIED) {
                        submitSpell();
                        return true;
                    }
                    return false;
                });
                spellRow.addView(spellInput, new LinearLayout.LayoutParams(0, -2, 1f));
                col.addView(spellRow);
                break;
            }
        }
        startTimer();
    }

    private void addOptions(LinearLayout col, JSONArray options, boolean en2cn) {
        if (options == null) return;
        try {
            for (int i = 0; i < options.length(); i += 2) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int j = i; j < Math.min(i + 2, options.length()); j++) {
                    final String opt = options.getString(j);
                    TextView b = Ui.button(getContext(), opt, Ui.BG0, Ui.TXT, false);
                    b.setOnClickListener(v -> submitOption(opt));
                    row.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
                    if (j < Math.min(i + 2, options.length()) - 1) row.addView(spacer(8));
                }
                col.addView(row);
                if (i + 2 < options.length()) col.addView(spacer(6));
            }
        } catch (Exception e) { /* ignore */ }
    }

    private void submitJudge(String ans) { submit(ans); }
    private void submitOption(String ans) { submit(ans); }
    private void submitSpell() {
        String t = spellInput == null ? "" : spellInput.getText().toString().trim();
        submit(t);
        if (spellInput != null) spellInput.setText("");
    }

    private void submit(String answer) {
        if (!answering || quiz == null) return;
        answering = false;
        stopTimer();
        try {
            boolean crit = combo >= 5 && combo % 5 == 0;
            double critMult = crit ? 1.5 : 1.0;
            JSONObject body = new JSONObject();
            body.put("word", quiz.optString("word"));
            body.put("qtype", quiz.optString("qtype"));
            body.put("answer", answer);
            body.put("time_limit", quiz.optDouble("timeLimit", 6));
            body.put("remain", remainSec);
            body.put("combo", combo);
            body.put("crit", crit);
            body.put("crit_mult", critMult);
            body.put("card_mult", 1.0);
            body.put("shown", quiz.isNull("judgeShown") ? JSONObject.NULL : quiz.optString("judgeShown"));
            body.put("mode", mode);
            if ("quick".equals(mode)) body.put("quick_level", "normal");
            body.put("run_state", runState.toJson());
            JSONObject res = act.router().dispatch("/quiz/answer", "POST", body);
            // 回传 run_state（含错词重现）
            JSONObject rs = res.optJSONObject("run_state");
            if (rs != null) runState = RunState.fromJson(rs);
            boolean ok = res.optBoolean("correct", false);
            int damage = res.optInt("damage", 0);
            int wrongCnt = res.optInt("wrong_count", 0);
            String correctAnswer = res.optString("correct_answer", "");

            if (ok) {
                combo++;
                // 复仇提示
                if (wrongCnt > 0) {
                    showFeed("⚡ 复仇！伤害 ×" + (wrongCnt >= 2 ? 2.0 : 1.5) + " +" + damage, Ui.PURPLE);
                } else if (crit) {
                    showFeed("💥 暴击！ +" + damage, Ui.GOLD);
                } else {
                    showFeed("+" + damage, Ui.GOLD);
                }
                hitEnemy(damage);
            } else {
                combo = 0;
                wrongInRun++;
                hitPlayer(enemy.optInt("atk", 10));
                if ("spell".equals(quiz.optString("qtype"))) {
                    showFeed("正确答案：" + correctAnswer, Ui.RED);
                } else {
                    showFeed("✗ 正确答案：" + correctAnswer, Ui.RED);
                }
                pauseThenNext();
            }
        } catch (Throwable e) {
            showFeed("结算失败：" + e.getMessage(), Ui.RED);
            answering = true;
        }
    }

    // ── 敌人/玩家受击 ─────────────────────────

    private void hitEnemy(int damage) {
        try {
            int left = Math.max(0, enemy.optInt("hp", 0) - damage);
            enemy.put("hp", left);
            enemyHp.setRatio((float) left / enemy.optInt("maxHp", 100));
            // 攻击动效：敌人抖动 + 震屏
            shake(enemyEmoji, 300);
            shakePlayer(true);
            enemyHp.setColor(left <= 0 ? Ui.GOLD : Ui.RED);
            if (left <= 0) {
                onKill();
            } else {
                main.postDelayed(this::nextQuiz, 350);
            }
        } catch (Throwable ignore) {
            main.postDelayed(this::nextQuiz, 350);
        }
    }

    private void hitPlayer(int atk) {
        hp = Math.max(0, hp - atk);
        playerHp.setRatio((float) hp / 100f);
        flashRed(playerName);
        shakePlayer(false);
    }

    private void onKill() {
        if ("endless".equals(mode)) {
            layer++;
            main.postDelayed(() -> {
                showFeed("⚔ 击杀！进入第 " + layer + " 层", Ui.GOLD);
                spawnEnemy();
                main.postDelayed(this::nextQuiz, 600);
            }, 350);
            return;
        }
        if ("campaign".equals(mode) && layer < 3) {
            layer++;
            main.postDelayed(() -> {
                showFeed("⚔ 击杀！第 " + layer + " 层", Ui.GOLD);
                spawnEnemy();
                main.postDelayed(this::nextQuiz, 600);
            }, 350);
            return;
        }
        // 通关/快刷胜利
        defeated = false;
        act.main().postDelayed(() -> {
            act.result().onFinish(true, mode, layer, wrongInRun, combo);
            act.show(MainActivity.Screen.RESULT);
        }, 800);
    }

    private void pauseThenNext() {
        if (hp <= 0) {
            main.postDelayed(() -> {
                act.result().onFinish(false, mode, layer, wrongInRun, combo);
                act.show(MainActivity.Screen.RESULT);
            }, 900);
            return;
        }
        main.postDelayed(this::nextQuiz, 1100);
    }

    public void onBack() {
        act.show(MainActivity.Screen.HOME);
    }

    // ── 计时 ─────────────────────────────

    private double timeLimit = 6;
    private double remainSec = 0;
    private Runnable timerTick;
    private boolean timerOn = false;

    private void startTimer() {
        if (quiz == null) return;
        timeLimit = quiz.optDouble("timeLimit", 6);
        remainSec = timeLimit;
        timerOn = true;
        timerTxt.setText(String.format("%.1f", remainSec));
        stopTimer();
        timerTick = new Runnable() {
            @Override
            public void run() {
                if (!timerOn) return;
                remainSec -= 0.1;
                if (remainSec <= 0) {
                    remainSec = 0;
                    timerTxt.setText("0.0");
                    timerTxt.setTextColor(Ui.RED);
                    main.postDelayed(() -> submit(""), 60);   // 计时结束自动提交（空答案=答错）
                    return;
                }
                timerTxt.setText(String.format("%.1f", remainSec));
                if (remainSec <= 2.5) timerTxt.setTextColor(Ui.ORANGE);
                else timerTxt.setTextColor(Ui.MUT);
                main.postDelayed(this, 100);
            }
        };
        main.postDelayed(timerTick, 100);
    }

    private void stopTimer() {
        timerOn = false;
        if (timerTick != null) main.removeCallbacks(timerTick);
        timerTxt.setTextColor(Ui.MUT);
    }

    // ── 动效 ─────────────────────────────

    private void shake(View v, long ms) {
        try {
            ObjectAnimator tx = ObjectAnimator.ofFloat(v, "translationX", 0f, -8f, 8f, -6f, 6f, -3f, 3f, 0f);
            tx.setDuration(ms);
            tx.setInterpolator(new AccelerateDecelerateInterpolator());
            tx.start();
        } catch (Throwable ignore) {}
    }

    private void shakePlayer(boolean attack) {
        try {
            ObjectAnimator ty = ObjectAnimator.ofFloat(playerEmoji, "translationY",
                    attack ? new float[]{0, -6, 6, -4, 4, 0} : new float[]{0, 8, -8, 5, -5, 0});
            ty.setDuration(350);
            ty.start();
        } catch (Throwable ignore) {}
    }

    private void flashRed(View v) {
        try {
            v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(90).withEndAction(() ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()).start();
        } catch (Throwable ignore) {}
    }

    private void showFeed(String msg, int color) {
        feedbackLayer.setText(msg);
        feedbackLayer.setTextColor(color);
        feedbackLayer.setVisibility(VISIBLE);
        feedbackLayer.setAlpha(1f);
        feedbackLayer.setScaleX(0.6f);
        feedbackLayer.setScaleY(0.6f);
        feedbackLayer.animate().alpha(0f).scaleX(1.2f).scaleY(1.2f).setDuration(1100).start();
        main.postDelayed(() -> feedbackLayer.setVisibility(GONE), 1150);
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
}
