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
import android.view.inputmethod.InputMethodManager;
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
    private int questionsInRun = 0;
    private int correctInRun = 0;
    private int maxCombo = 0;
    private int killsInRun = 0;
    private JSONObject enemy;
    private JSONObject quiz;
    private boolean answering = false;
    private boolean defeated = false;

    // UI
    private TextView topTxt, enemyName, enemyEmoji, playerEmoji, playerName;
    private TextView enemyTag, warnTxt, hpNumTxt, comboLab, comboNum, comboSub;
    private HpBar enemyHp, playerHp;
    private TextView posTag, revTag, promptTxt, ladderTxt, qBody, timerTxt;
    private FrameLayout answerArea;
    private LinearLayout timerTrack;
    private View timerFill;
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
        col.setPadding(dp(16), dp(14), dp(16), dp(16));
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
        col.addView(spacer(8));

        // 敌人卡（V7.0：face 大字 + 名字 + rtag + 反击警告 + 血条 + 血量数字）
        LinearLayout ebox = new LinearLayout(getContext());
        ebox.setOrientation(LinearLayout.VERTICAL);
        ebox.setGravity(Gravity.CENTER);
        ebox.setPadding(dp(12), dp(10), dp(12), dp(10));
        ebox.setBackground(Ui.card(16, Ui.CARD, 1, Ui.LINE));
        enemyEmoji = Ui.text(getContext(), "👹", 46, Ui.TXT, false);
        ebox.addView(enemyEmoji);
        enemyName = Ui.text(getContext(), "词魔", 15, Ui.TXT, true);
        enemyName.setGravity(Gravity.CENTER);
        ebox.addView(enemyName);
        enemyTag = Ui.text(getContext(), "普通战", 11, Ui.CYAN, true);
        enemyTag.setGravity(Gravity.CENTER);
        ebox.addView(enemyTag);
        warnTxt = Ui.text(getContext(), "答错 / 超时将受到 10 点伤害", 11, Ui.MUT, false);
        warnTxt.setGravity(Gravity.CENTER);
        ebox.addView(warnTxt);
        enemyHp = new HpBar(getContext(), Ui.RED, Ui.CARD2);
        enemyHp.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(8)));
        ebox.addView(enemyHp);
        hpNumTxt = Ui.text(getContext(), "100 / 100", 11, Ui.TXT, true);
        hpNumTxt.setGravity(Gravity.CENTER);
        ebox.addView(hpNumTxt);
        col.addView(ebox);
        col.addView(spacer(6));

        // combo-line（V7.0 金色连击大字）
        LinearLayout comboRow = new LinearLayout(getContext());
        comboRow.setOrientation(LinearLayout.HORIZONTAL);
        comboRow.setGravity(Gravity.CENTER);
        comboLab = Ui.text(getContext(), "连击", 12, Ui.MUT, true);
        comboRow.addView(comboLab);
        comboNum = Ui.text(getContext(), "0", 26, Ui.GOLD, true);
        comboNum.setPadding(dp(8), 0, dp(8), 0);
        comboRow.addView(comboNum);
        comboSub = Ui.text(getContext(), "", 12, Ui.GOLD, false);
        comboRow.addView(comboSub);
        col.addView(comboRow);
        col.addView(spacer(6));

        // 题目卡（V7.0 qcard：qhead 大字 + 词性徽章 + 复仇徽章 + 提示 + 阶梯 + 计时）
        LinearLayout qbox = new LinearLayout(getContext());
        qbox.setOrientation(LinearLayout.VERTICAL);
        qbox.setPadding(dp(14), dp(10), dp(14), dp(10));
        qbox.setBackground(Ui.card(14, Ui.BG1, 1, Ui.LINE));
        LinearLayout headRow = new LinearLayout(getContext());
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        qBody = Ui.text(getContext(), "", 22, Ui.TXT, true);
        qBody.setGravity(Gravity.CENTER);
        headRow.addView(qBody, new LinearLayout.LayoutParams(0, -2, 1f));
        posTag = Ui.text(getContext(), "", 12, Ui.GOLD, true);
        headRow.addView(posTag);
        revTag = Ui.text(getContext(), "", 12, Ui.PURPLE, true);
        headRow.addView(revTag);
        qbox.addView(headRow);
        promptTxt = Ui.text(getContext(), "", 12, Ui.MUT, false);
        promptTxt.setGravity(Gravity.CENTER);
        qbox.addView(promptTxt);
        ladderTxt = Ui.text(getContext(), "", 10, Ui.MUT, true);
        ladderTxt.setGravity(Gravity.CENTER);
        qbox.addView(ladderTxt);
        // 计时条（金色渐变填充）
        timerTrack = new LinearLayout(getContext());
        timerTrack.setOrientation(LinearLayout.HORIZONTAL);
        timerTrack.setBackground(Ui.card(999, Ui.CARD2, 0, 0));
        timerFill = new View(getContext());
        timerFill.setBackground(Ui.gradient(999, Ui.GOLD, Ui.ORANGE, 0));
        timerTrack.addView(timerFill, new LinearLayout.LayoutParams(-1, dp(8)));
        qbox.addView(timerTrack, new LinearLayout.LayoutParams(-1, dp(8)));
        timerTxt = Ui.text(getContext(), "", 11, Ui.MUT, false);
        timerTxt.setGravity(Gravity.CENTER);
        qbox.addView(timerTxt);
        col.addView(qbox);
        col.addView(spacer(8));

        // 答题区
        answerArea = new FrameLayout(getContext());
        answerArea.setBackground(Ui.card(14, Ui.CARD2, 1, Ui.LINE));
        col.addView(answerArea, new LinearLayout.LayoutParams(-1, dp(148)));
        col.addView(spacer(8));

        // 玩家 HUD（V7.0 playerhud：生命条 + 词魂）
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
        // 新一局前清掉上一局悬挂的全部回调（计时/自动提交/题链/飘字），防旧回调串题
        main.removeCallbacksAndMessages(null);
        hideKeyboard();
        mode = act.mode;
        book = act.book;
        difficulty = act.difficulty;
        runState = new RunState();
        layer = 1;
        hp = 100;
        combo = 0;
        wrongInRun = 0;
        questionsInRun = 0;
        correctInRun = 0;
        maxCombo = 0;
        killsInRun = 0;
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
            int maxHp = e.optInt("maxHp", 100);
            hpNumTxt.setText(maxHp + " / " + maxHp);
            int atk = e.optInt("atk", 10);
            boolean boss = e.optBoolean("boss", false);
            boolean elite = e.optBoolean("elite", false);
            enemyTag.setText(boss ? "Boss 战" : elite ? "精英战" : "普通战");
            enemyTag.setTextColor(boss ? Ui.RED : elite ? Ui.ORANGE : Ui.CYAN);
            warnTxt.setText("答错 / 超时将受到 " + atk + " 点伤害");
            if (e.optBoolean("champion", false)) enemyName.setTextColor(Ui.GOLD);
            topTxt.setText(modeLabel() + " · 第" + layer + "层" + ("quick".equals(mode) ? " · " + act.quickLevel + "档" : ""));
        } catch (Throwable e) {
            showFeed("敌人召唤失败", Ui.RED);
        }
    }

    private JSONObject bodyWithQuickLevel(JSONObject body) throws Exception {
        body.put("quick_level", act.quickLevel);
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
        hideKeyboard();   // 切题时先收键盘（拼写题会在 renderQuiz 里重新请求弹出）
        try {
            JSONObject body = new JSONObject();
            body.put("layer", layer);
            body.put("mode", mode);
            if (!"all".equals(book)) body.put("book", book);
            body.put("adaptive", "adaptive".equals(difficulty));
            if ("adaptive".equals(difficulty)) body.put("adaptive", true);
            else if (!"all".equals(difficulty)) body.put("difficulty", diffLevel());
            body.put("run_state", runState.toJson());
            if ("quick".equals(mode)) body.put("quick_level", act.quickLevel);
            quiz = act.router().dispatch("/quiz/draw", "POST", body);
            renderQuiz();
        } catch (Throwable e) {
            showFeed("出题失败：" + e.getMessage(), Ui.RED);
        }
    }

    private int diffLevel() {
        switch (difficulty) {
            case "easy": return 1;
            case "normal": return 2;
            case "hard": return 3;
            case "hell": return 3;
            case "all": return 0;
            default: return 2;
        }
    }

    private void renderQuiz() {
        if (quiz == null) return;
        String qtype = quiz.optString("qtype", "en2cn");
        String word = quiz.optString("word", "");
        String pos = quiz.optString("pos", "");
        String mean = quiz.optString("mean", "");
        boolean review = quiz.optBoolean("review", false);
        int step = quiz.optInt("step", 0);
        boolean graduated = quiz.optBoolean("graduated", false);
        int stage = quiz.optInt("stage", 0);
        int wrongCount = quiz.optInt("wrongCount", 0);

        // 词性徽章
        posTag.setText(pos.isEmpty() ? "" : pos.toUpperCase() + (review ? " · 复习" : ""));
        posTag.setVisibility(pos.isEmpty() ? GONE : VISIBLE);
        // 复仇徽章（V7.0 revbadge：错过该词后答对伤害更高）
        if (wrongCount > 0 && quiz.optBoolean("revEngaged", false)) {
            double mult = Math.min(1.5 + 0.25 * (wrongCount - 1), 2.0);
            revTag.setText("⚔ 再战 · 伤害+" + Math.round(mult * 100) + "%");
            revTag.setVisibility(VISIBLE);
        } else {
            revTag.setVisibility(GONE);
        }
        // 题目提示
        String prompt;
        if ("en2cn".equals(qtype)) prompt = "选择正确的中文释义";
        else if ("cn2en".equals(qtype)) prompt = "选择正确的英文单词";
        else if ("judge".equals(qtype)) prompt = "这个释义对吗？";
        else prompt = "根据释义默写英文单词";
        promptTxt.setText(prompt);
        // 题型阶梯（V7.0 ladder：识→认→写→拼；复习则 10分/1天/3天/7天/16天/30天）
        if (graduated) {
            String[] labs = {"10分", "1天", "3天", "7天", "16天", "30天"};
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < labs.length; i++) {
                sb.append(i <= stage ? "▣" : "▢").append(" ").append(labs[i]).append(" ");
            }
            ladderTxt.setText("复习  " + sb.toString().trim());
        } else {
            String[] labs = {"识", "认", "写", "拼"};
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < labs.length; i++) {
                sb.append(i <= step ? "▣" : "▢").append(labs[i]).append(" ");
            }
            ladderTxt.setText(sb.toString().trim());
        }

        answerArea.removeAllViews();
        spellRow = null;

        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(10), dp(8), dp(10), dp(8));
        answerArea.addView(col, new LayoutParams(-1, -1));

        switch (qtype) {
            case "judge": {
                qBody.setText(word);
                qBody.setTextSize(24);
                LinearLayout meanRow = new LinearLayout(getContext());
                meanRow.setOrientation(LinearLayout.HORIZONTAL);
                meanRow.setGravity(Gravity.CENTER);
                TextView mv = Ui.text(getContext(), mean, 19, Ui.CYAN, true);
                meanRow.addView(mv);
                LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
                ml.topMargin = dp(2);
                ml.bottomMargin = dp(8);
                col.addView(meanRow, ml);
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView yes = Ui.button(getContext(), "✓ 对", Ui.GREEN, Ui.BG0, false);
                TextView no = Ui.button(getContext(), "✗ 错", Ui.RED, Ui.BG0, false);
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
                qBody.setText(mean);
                qBody.setTextSize(19);
                spellRow = new LinearLayout(getContext());
                spellRow.setOrientation(LinearLayout.HORIZONTAL);
                spellRow.setGravity(Gravity.CENTER_VERTICAL);
                spellInput = new EditText(getContext());
                spellInput.setSingleLine(true);
                // V3.5 回归：VISIBLE_PASSWORD 关闭自动纠错/首字母大写/联想条，
                // 配合 NO_SUGGESTIONS 保证逐字符顺序输入、系统删除键原生行为，
                // 杜绝 H5 曾出现的"输入自动反过来/丢字母"；未注册任何 TextWatcher/KeyListener。
                spellInput.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                spellInput.setFocusable(true);
                spellInput.setFocusableInTouchMode(true);
                spellInput.setTextColor(Ui.TXT);
                spellInput.setHintTextColor(Ui.GOLD);
                String hint = word.isEmpty() ? "输入英文拼写" : "首字母 " + word.substring(0, 1).toUpperCase()
                        + "，共 " + word.length() + " 个字母，点击此处拼写";
                spellInput.setHint(hint);
                spellInput.setBackground(Ui.card(10, Ui.BG0, 1, Ui.LINE));
                spellInput.setPadding(dp(10), dp(8), dp(10), dp(8));
                spellInput.setTextSize(18);
                spellInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
                // 回车/完成：走与选项提交完全一致的 submit() 判定逻辑
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
                // 键盘生命周期：拼写题出现自动聚焦并弹出输入法（等 attach 后再请求）
                spellInput.post(() -> {
                    try {
                        spellInput.requestFocus();
                        InputMethodManager imm = (InputMethodManager)
                                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showSoftInput(spellInput, InputMethodManager.SHOW_IMPLICIT);
                    } catch (Throwable ignore) {}
                });
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
                    LinearLayout b = new LinearLayout(getContext());
                    b.setOrientation(LinearLayout.HORIZONTAL);
                    b.setGravity(Gravity.CENTER_VERTICAL);
                    b.setPadding(dp(10), dp(10), dp(10), dp(10));
                    b.setBackground(Ui.card(10, Ui.BG0, 1, Ui.LINE));
                    TextView key = Ui.text(getContext(), String.valueOf(j + 1), 12, Ui.MUT, true);
                    key.setGravity(Gravity.CENTER);
                    key.setBackground(Ui.card(6, Ui.CARD2, 0, 0));
                    int ks = dp(24);
                    key.setLayoutParams(new LinearLayout.LayoutParams(ks, ks));
                    b.addView(key);
                    TextView v = Ui.text(getContext(), opt, 14, Ui.TXT, false);
                    v.setPadding(dp(8), 0, 0, 0);
                    b.addView(v);
                    b.setOnClickListener(vi -> submitOption(opt));
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
        // 读取拼写后走统一 submit() 判定；答对清空输入框，答错保留玩家拼写做红抖/换答案展示
        if (spellInput == null) { submit(""); return; }
        String t = spellInput.getText().toString().trim();
        submit(t);
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
            if ("quick".equals(mode)) body.put("quick_level", act.quickLevel);
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
                questionsInRun++;
                correctInRun++;
                maxCombo = Math.max(maxCombo, combo);
                comboNum.setText(String.valueOf(combo));
                comboSub.setText(combo >= 5 ? "心流加成中" : combo >= 8 ? "输出高涨！" : "");
                comboNum.setTextColor(combo >= 5 ? Ui.PURPLE : Ui.GOLD);
                // 答对：拼写框立即清空并收键盘，进入打击反馈
                if (spellInput != null) { spellInput.setText(""); }
                hideKeyboard();
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
                questionsInRun++;
                combo = 0;
                comboNum.setText("0");
                comboNum.setTextColor(Ui.GOLD);
                comboSub.setText("");
                wrongInRun++;
                hitPlayer(enemy.optInt("atk", 10));
                // V3.5 拼写答错回归：先红抖展示玩家拼写，约 0.6s 后替换为正确答案（金色）
                // 并停留约 1s 再进下一题；选项题保持原 1.1s 停留。
                boolean spellWrong = "spell".equals(quiz.optString("qtype")) && spellInput != null;
                if (spellWrong) {
                    showFeed("正确答案：" + correctAnswer, Ui.RED);
                    try {
                        final EditText f = spellInput;
                        f.setTextColor(Ui.RED);
                        f.clearFocus();
                        hideKeyboard();
                        shake(f, 350);
                        main.postDelayed(() -> {
                            try {
                                f.setText(correctAnswer);
                                f.setTextColor(Ui.GREEN);   // 正确答案变绿
                                f.setKeyListener(null);   // 答案展示态禁编辑/删除，防误改
                            } catch (Throwable ignore) {}
                        }, 600);
                    } catch (Throwable ignore) {}
                } else {
                    showFeed("✗ 正确答案：" + correctAnswer, Ui.RED);
                }
                pauseThenNext(spellWrong);
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
            hpNumTxt.setText(left + " / " + enemy.optInt("maxHp", 100));
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
        killsInRun++;
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
            act.result().onFinish(true, mode, layer, wrongInRun, combo,
                    questionsInRun, correctInRun, maxCombo, killsInRun);
            act.show(MainActivity.Screen.RESULT);
        }, 800);
    }

    private void pauseThenNext() { pauseThenNext(false); }

    private void pauseThenNext(boolean spellReveal) {
        if (hp <= 0) {
            main.postDelayed(() -> {
                act.result().onFinish(false, mode, layer, wrongInRun, combo,
                        questionsInRun, correctInRun, maxCombo, killsInRun);
                act.show(MainActivity.Screen.RESULT);
            }, 900);
            return;
        }
        // 拼写错题：红抖 0.6s + 正确答案停留 ~1.0s（共 1.6s）；选项题保持 1.1s 一致停留
        main.postDelayed(this::nextQuiz, spellReveal ? 1600 : 1100);
    }

    public void onBack() {
        hideKeyboard();
        // 退出战斗清掉本页全部悬挂回调（计时/自动提交/题链），防止回主页后旧回调串题
        main.removeCallbacksAndMessages(null);
        act.show(MainActivity.Screen.HOME);
    }

    // ── 计时 ─────────────────────────────

    private double timeLimit = 6;
    private double remainSec = 0;
    private Runnable timerTick;
    private Runnable autoSubmit;   // 计时到点的空答案提交（纳入 stopTimer 统一移除，防悬挂）
    private boolean timerOn = false;

    private void startTimer() {
        if (quiz == null) return;
        // 先清掉上一题残留（timerTick + 自动提交匿名回调），再开启新循环；
        // 注意必须先 stopTimer() 再置 timerOn=true，否则旧顺序里 stopTimer() 会把
        // timerOn 重置为 false，导致首个 tick 因 !timerOn 直接 return，计时整条死掉。
        stopTimer();
        timeLimit = quiz.optDouble("timeLimit", 6);
        remainSec = timeLimit;
        timerOn = true;
        timerTxt.setText(String.format("%.1f", remainSec) + " 秒");
        timerTxt.setTextColor(Ui.MUT);
        setTimerFill(1f);
        timerTick = new Runnable() {
            @Override
            public void run() {
                if (!timerOn) return;
                remainSec -= 0.1;
                if (remainSec <= 0) {
                    remainSec = 0;
                    timerTxt.setText("0.0 秒");
                    timerTxt.setTextColor(Ui.RED);
                    setTimerFill(0f);
                    // 计时结束自动提交（空答案=答错）；与玩家手动提交互斥：
                    // submit() 首句 answering 守卫 + stopTimer() 会移除本回调，防双提交/连跳两题
                    autoSubmit = () -> submit("");
                    main.postDelayed(autoSubmit, 60);
                    return;
                }
                timerTxt.setText(String.format("%.1f", remainSec) + " 秒");
                setTimerFill((float) Math.max(0, remainSec / timeLimit));
                if (remainSec <= 2.5) timerTxt.setTextColor(Ui.ORANGE);
                else timerTxt.setTextColor(Ui.MUT);
                main.postDelayed(this, 100);
            }
        };
        main.postDelayed(timerTick, 100);
    }

    private void setTimerFill(float pct) {
        try {
            int trackW = Math.max(dp(2), timerTrack.getWidth());
            int w = Math.max(dp(2), (int) (trackW * Math.max(0f, Math.min(1f, pct))));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, dp(8));
            timerFill.setLayoutParams(lp);
        } catch (Throwable ignore) {}
    }

    private void stopTimer() {
        timerOn = false;
        if (timerTick != null) main.removeCallbacks(timerTick);
        if (autoSubmit != null) main.removeCallbacks(autoSubmit);
        timerTxt.setTextColor(Ui.MUT);
    }

    /** 收起输入法（拼写题提交/切题/退出时调用）。 */
    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && spellInput != null) {
                imm.hideSoftInputFromWindow(spellInput.getWindowToken(), 0);
            }
        } catch (Throwable ignore) {}
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
