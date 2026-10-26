package com.guzifeng.worddungeon.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private int playerLevel = 1;
    // V11.3 战利品增益
    private double bonusDamageMult = 1.0;   // 作用于下一个敌人
    private int bonusTime = 0;             // 下题时间 +秒

    // UI
    private TextView topTxt, enemyName, enemyEmoji, playerEmoji, playerName;
    private TextView enemyTag, warnTxt, hpNumTxt, comboLab, comboNum, comboSub;
    private HpBar enemyHp, playerHp;
    private TextView posTag, revTag, promptTxt, ladderTxt, qBody, timerTxt;
    private FrameLayout answerArea;
    private LinearLayout timerTrack;
    private View timerFill;
    private ValueAnimator fillAnim;
    private TextView feedbackLayer;
    private LinearLayout enemyCard;
    private View enemyGlow;
    private ObjectAnimator idleAnim;
    private TextView dmgPop;
    private EditText spellInput;
    private TextView confirmBtn;
    private LinearLayout spellRow;
    // V11.5 答题反馈：选项引用（对错高亮）+ 拼写字母格
    private final List<View> optionViews = new ArrayList<>();
    private final List<String> optionValues = new ArrayList<>();
    private LinearLayout tileRow;                 // 拼写字母格容器
    private final List<TextView> tileViews = new ArrayList<>();
    private int tileLen = 0;

    private static final Map<String, String> EMOJI = new HashMap<>();
    static {
        EMOJI.put("wordfiend", "👹"); EMOJI.put("confuser", "😈"); EMOJI.put("speller", "🧟");
        EMOJI.put("ghost", "👻"); EMOJI.put("polysem", "🎭");
        EMOJI.put("boss1", "👑"); EMOJI.put("boss2", "🌫"); EMOJI.put("boss3", "🔥");
    }

    public BattleView(Context c) {
        super(c);
        act = (MainActivity) c;
        setBackground(new AmbientBackground(34));   // V11.3 地下城氛围底（战斗页粒子略多）
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
        // V11.3 敌人面部：径向光晕（类型色）+ emoji，光晕随类型变化
        FrameLayout faceWrap = new FrameLayout(getContext());
        enemyGlow = new View(getContext());
        int glowSize = dp(98);
        enemyGlow.setLayoutParams(new FrameLayout.LayoutParams(glowSize, glowSize, Gravity.CENTER));
        enemyEmoji = Ui.text(getContext(), "👹", 46, Ui.TXT, false);
        faceWrap.addView(enemyGlow);
        faceWrap.addView(enemyEmoji, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        ebox.addView(faceWrap);
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
        enemyCard = ebox;
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

        // 答题区（V11.5 高度上调以容纳拼写字母格 + 确认按钮）
        answerArea = new FrameLayout(getContext());
        answerArea.setBackground(Ui.card(14, Ui.CARD2, 1, Ui.LINE));
        col.addView(answerArea, new LinearLayout.LayoutParams(-1, dp(166)));
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
        // 伤害飘字（打击感：紧贴敌人弹出的金色伤害数字）
        dmgPop = new TextView(getContext());
        dmgPop.setTextSize(24);
        dmgPop.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        dmgPop.setGravity(Gravity.CENTER);
        dmgPop.setVisibility(GONE);
        addView(dmgPop, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
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
            applyEnemyGlow(boss, elite, e.optBoolean("champion", false));
            idleFloat();
            warnTxt.setText("答错 / 超时将受到 " + atk + " 点伤害");
            enemyName.setTextColor(e.optBoolean("champion", false) ? Ui.GOLD : Ui.TXT);
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
        optionViews.clear();
        optionValues.clear();
        tileViews.clear();
        tileLen = 0;

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
                Ui.pressable(yes);
                Ui.pressable(no);
                yes.setOnClickListener(v -> submitJudge("yes"));
                no.setOnClickListener(v -> submitJudge("no"));
                row.addView(yes, new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(spacer(8));
                row.addView(no, new LinearLayout.LayoutParams(0, -2, 1f));
                col.addView(row);
                // V11.6 对/错按钮纳入反馈高亮（答对绿放大 / 答错红抖，正确答案标绿）
                optionViews.add(yes); optionValues.add("yes");
                optionViews.add(no);  optionValues.add("no");
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
                // V11.5 字母格：先显示一排字母位（首字母已锁定，其余为空），实时同步输入
                int len = word.length();
                tileLen = len;
                tileRow = new LinearLayout(getContext());
                tileRow.setOrientation(LinearLayout.HORIZONTAL);
                tileRow.setGravity(Gravity.CENTER);
                for (int i = 0; i < len; i++) {
                    TextView t = Ui.text(getContext(), i == 0 ? String.valueOf(word.charAt(0)).toUpperCase() : "",
                            17, i == 0 ? Ui.GOLD : Ui.TXT, true);
                    t.setGravity(Gravity.CENTER);
                    int ts = dp(i == 0 ? 30 : 30);
                    t.setLayoutParams(new LinearLayout.LayoutParams(ts, ts));
                    t.setBackground(Ui.card(8, i == 0 ? Ui.CARD2 : Ui.BG0, 1,
                            i == 0 ? Ui.GOLD : Ui.LINE));
                    tileRow.addView(t);
                    if (i < len - 1) tileRow.addView(spacer(4));
                    tileViews.add(t);
                }
                col.addView(tileRow);
                col.addView(spacer(6));
                spellRow = new LinearLayout(getContext());
                spellRow.setOrientation(LinearLayout.HORIZONTAL);
                spellRow.setGravity(Gravity.CENTER_VERTICAL);
                spellInput = new EditText(getContext());
                spellInput.setSingleLine(true);
                // V3.5 回归：VISIBLE_PASSWORD 关闭自动纠错/首字母大写/联想条，
                // 配合 NO_SUGGESTIONS 保证逐字符顺序输入、系统删除键原生行为，
                // 杜绝 H5 曾出现的"输入自动反过来/丢字母"；只读 TextWatcher 同步字母格，不回写文本。
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
                // V11.5 字母格实时同步（只读不回写，绝不干扰输入顺序/删除键）
                spellInput.addTextChangedListener(new android.text.TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void afterTextChanged(android.text.Editable e) {
                        syncTiles(e.toString());
                    }
                });
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
                // V11.5 确认按钮：一眼可见的提交入口（回车同样有效）
                confirmBtn = Ui.text(getContext(), "✓ 确认", 14, 0xFF081326, true);
                confirmBtn.setGravity(Gravity.CENTER);
                confirmBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
                confirmBtn.setBackground(Ui.gradient(999, 0xFFFCD34D, 0xFFF59E0B, 0));
                Ui.pressable(confirmBtn);
                confirmBtn.setOnClickListener(v -> submitSpell());
                spellRow.addView(spacer(6));
                spellRow.addView(confirmBtn);
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
            char[] keys = {'A', 'B', 'C', 'D'};
            int rowIdx = 0;
            for (int i = 0; i < options.length(); i += 2) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int j = i; j < Math.min(i + 2, options.length()); j++) {
                    final String opt = options.getString(j);
                    final int optNo = j;
                    LinearLayout b = new LinearLayout(getContext());
                    b.setOrientation(LinearLayout.HORIZONTAL);
                    b.setGravity(Gravity.CENTER_VERTICAL);
                    b.setPadding(dp(8), dp(9), dp(8), dp(9));
                    // V11.5 字母徽章：A/B/C/D 方徽章 + 圆角卡片，信息层次更强
                    b.setBackground(Ui.card(12, Ui.BG0, 1, Ui.LINE));
                    TextView key = Ui.text(getContext(), String.valueOf(keys[Math.min(j, 3)]), 13, Ui.CYAN, true);
                    key.setGravity(Gravity.CENTER);
                    key.setBackground(Ui.card(8, Ui.CARD2, 0, 0));
                    int ks = dp(26);
                    key.setLayoutParams(new LinearLayout.LayoutParams(ks, ks));
                    b.addView(key);
                    TextView v = Ui.text(getContext(), opt, 15, Ui.TXT, j % 2 == 0);
                    v.setPadding(dp(8), 0, dp(2), 0);
                    v.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                    b.addView(v);
                    Ui.pressable(b);
                    b.setOnClickListener(vi -> submitOption(opt));
                    row.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
                    if (j < Math.min(i + 2, options.length()) - 1) row.addView(spacer(8));
                    optionViews.add(b);
                    optionValues.add(opt);
                    // V11.5 选项逐个滑入（出招表节奏）
                    b.setAlpha(0f);
                    b.setTranslationY(dp(14));
                    final View fb = b;
                    final long d = 60L * (j + 1);
                    b.post(() -> fb.animate().alpha(1f).translationY(0f)
                            .setStartDelay(d).setDuration(260).start());
                }
                col.addView(row);
                if (i + 2 < options.length()) col.addView(spacer(7));
            }
        } catch (Exception e) { /* ignore */ }
    }

    // ── V11.5 答题/拼写反馈 ─────────────────────────

    /** 拼写字母格实时同步：输入字符写入对应格（首格锁定），只读不回写文本。 */
    private void syncTiles(String typed) {
        if (tileViews.isEmpty()) return;
        String t = typed == null ? "" : typed;
        for (int i = 0; i < tileViews.size(); i++) {
            TextView tile = tileViews.get(i);
            if (i == 0) continue;   // 首字母锁定
            if (i < t.length() + 1) {
                char c = t.charAt(i - 1);
                tile.setText(String.valueOf(c).toUpperCase());
                tile.setTextColor(Ui.GOLD);
                tile.setBackground(Ui.card(8, Ui.CARD2, 1, Ui.GOLD));
            } else {
                tile.setText("");
                tile.setTextColor(Ui.TXT);
                tile.setBackground(Ui.card(8, Ui.BG0, 1, Ui.LINE));
            }
        }
    }

    /** 选项题对错高亮：答对绿色放大，答错红色抖动；正确答案始终绿框，其余变暗。 */
    private void highlightOptions(String picked, String correct) {
        for (int i = 0; i < optionViews.size(); i++) {
            View v = optionViews.get(i);
            if (v == null) continue;
            String val = i < optionValues.size() ? optionValues.get(i) : "";
            boolean isCorrect = val.equals(correct);
            boolean isPicked = val.equals(picked);
            if (isCorrect) {
                v.setBackground(Ui.card(12, 0x1434D399, 2, Ui.GREEN));
                v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(180).start();
            } else if (isPicked) {
                v.setBackground(Ui.card(12, 0x26F87171, 2, Ui.RED));
                shake(v, 320);
            } else {
                v.setAlpha(0.42f);
            }
            v.setClickable(false);
        }
    }

    /** 拼写答对：字母格从左到右逐个点亮为绿色（魔法铭刻感）。 */
    private void spellTilesReveal(String word, boolean ok) {
        String w = word == null ? "" : word;
        for (int i = 0; i < tileViews.size(); i++) {
            final TextView tile = tileViews.get(i);
            final int idx = i;
            char ch = i < w.length() ? w.charAt(i) : ' ';
            main.postDelayed(() -> {
                try {
                    tile.setText(String.valueOf(Character.toUpperCase(ch)));
                    tile.setTextColor(Ui.GREEN);
                    tile.setBackground(Ui.card(8, 0x1434D399, 2, Ui.GREEN));
                    tile.animate().scaleX(1.18f).scaleY(1.18f).setDuration(90)
                            .withEndAction(() -> tile.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
                } catch (Throwable ignore) {}
            }, 80L * idx);
        }
    }

    /** 拼写答错：格子红抖，随后 0.6s 起逐个揭晓正确答案（金色）。 */
    private void spellTilesWrong(String word) {
        for (TextView tile : tileViews) {
            if (tile == null) continue;
            tile.setTextColor(Ui.RED);
            tile.setBackground(Ui.card(8, 0x26F87171, 1, Ui.RED));
        }
        if (tileRow != null) shake(tileRow, 380);
        String w = word == null ? "" : word;
        for (int i = 0; i < tileViews.size(); i++) {
            final TextView tile = tileViews.get(i);
            final int idx = i;
            char ch = i < w.length() ? w.charAt(i) : ' ';
            main.postDelayed(() -> {
                try {
                    tile.setText(String.valueOf(Character.toUpperCase(ch)));
                    tile.setTextColor(Ui.GOLD);
                    tile.setBackground(Ui.card(8, Ui.CARD2, 2, Ui.GOLD));
                } catch (Throwable ignore) {}
            }, 600L + 90L * idx);
        }
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
            body.put("card_mult", bonusDamageMult);   // V11.3 战利品伤害增益
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
            int xpGain = res.optInt("xp_gained", 0);
            int newLevel = res.optInt("level", 1);
            String correctAnswer = res.optString("correct_answer", "");

            if (ok) {
                combo++;
                questionsInRun++;
                correctInRun++;
                maxCombo = Math.max(maxCombo, combo);
                comboNum.setText(String.valueOf(combo));
                comboSub.setText(combo >= 8 ? "输出高涨！" : combo >= 5 ? "心流加成中" : "");
                comboNum.setTextColor(combo >= 5 ? Ui.PURPLE : Ui.GOLD);
                if (combo > 0 && combo % 5 == 0) ringBurst();   // V11.3 心流里程碑光环
                // V11.5 答题反馈：选项题高亮答对的选项（绿框+放大，其余变暗）；拼写题格子逐个点亮
                boolean isSpell = "spell".equals(quiz.optString("qtype"));
                if (isSpell) {
                    spellTilesReveal(correctAnswer, true);
                } else {
                    highlightOptions(answer, correctAnswer);
                }
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
                if (xpGain > 0) popXp(xpGain);
                if (newLevel > playerLevel) {
                    playerLevel = newLevel;
                    onLevelUp();
                } else playerLevel = newLevel;
                hitEnemy(damage, crit, wrongCnt > 0);
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
                    // V11.5：字母格红抖 + 逐个揭晓正确答案（金色）
                    spellTilesWrong(correctAnswer);
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
                    // V11.6 judge 题把 yes/no 转成 对/错 展示，与按钮高亮一致
                    String display = "judge".equals(quiz.optString("qtype"))
                            ? ("yes".equals(correctAnswer) ? "对" : "错") : correctAnswer;
                    showFeed("✗ 正确答案：" + display, Ui.RED);
                    // V11.5 选项题答错：选中项红抖 + 正确答案绿框高亮
                    highlightOptions(answer, correctAnswer);
                }
                pauseThenNext(spellWrong);
            }
        } catch (Throwable e) {
            showFeed("结算失败：" + e.getMessage(), Ui.RED);
            answering = true;
        }
    }

    // ── 敌人/玩家受击 ─────────────────────────

    private void hitEnemy(int damage, boolean crit, boolean revenge) {
        try {
            int left = Math.max(0, enemy.optInt("hp", 0) - damage);
            enemy.put("hp", left);
            enemyHp.setRatio((float) left / enemy.optInt("maxHp", 100));
            hpNumTxt.setText(left + " / " + enemy.optInt("maxHp", 100));
            // 攻击动效：命中顿帧（敌人瞬间放大→回弹）+ 整卡震动 + 伤害飘字 + 金色粒子
            boolean big = crit || revenge;
            enemyEmoji.animate().scaleX(1.35f).scaleY(1.35f).setDuration(70)
                    .withEndAction(() -> enemyEmoji.animate().scaleX(1f).scaleY(1f).setDuration(210).start()).start();
            shake(enemyEmoji, 260);
            if (enemyCard != null) shake(enemyCard, 220);
            shakePlayer(true);
            pulse(comboNum);
            popDmg((crit ? "💥" : "") + "+" + damage, revenge ? Ui.PURPLE : Ui.GOLD);
            int[] ec = new int[2];
            enemyEmoji.getLocationInWindow(ec);
            int[] bc = new int[2];
            getLocationInWindow(bc);
            float cx = ec[0] - bc[0] + enemyEmoji.getWidth() / 2f;
            float cy = ec[1] - bc[1] + enemyEmoji.getHeight() / 2f;
            burstParticles(cx, cy, big ? 14 : 8, big ? (revenge ? 0xFFC084FC : 0xFFFBBF24) : 0xFFFCD34D, big ? 52 : 36);
            if (big) Ui.vibrate(getContext(), new long[]{0, 45, 35, 60});   // 暴击/复仇：双段冲击
            else Ui.vibrate(getContext(), 22);                              // 答对：清脆一击
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
        if (playerEmoji != null) {
            playerEmoji.animate().scaleX(1.3f).scaleY(1.3f).setDuration(80)
                    .withEndAction(() -> playerEmoji.animate().scaleX(1f).scaleY(1f).setDuration(200).start()).start();
        }
        shakePlayer(false);
        int[] pc = new int[2];
        if (playerEmoji != null) playerEmoji.getLocationInWindow(pc);
        int[] bc = new int[2];
        getLocationInWindow(bc);
        burstParticles(pc[0] - bc[0] + (playerEmoji == null ? 0 : playerEmoji.getWidth() / 2f),
                pc[1] - bc[1] + (playerEmoji == null ? 0 : playerEmoji.getHeight() / 2f),
                6, 0xFFF87171, 30);
        Ui.vibrate(getContext(), new long[]{0, 70, 50, 90});   // 答错：沉重两段挫败震
    }

    private void onKill() {
        killsInRun++;
        bonusDamageMult = 1.0;   // 上一轮伤害增益作用于刚击杀的敌人，到此结算重置
        Ui.vibrate(getContext(), new long[]{0, 50, 40, 70, 40, 90});   // 击杀胜利反馈
        // 快刷：一局一个敌人，击杀即结算（保持"快速"节奏，不翻牌）
        if ("quick".equals(mode)) {
            finishRun(true);
            return;
        }
        // 远征通关（第 3 层 Boss）→ 结算
        if ("campaign".equals(mode) && layer >= 3) {
            finishRun(true);
            return;
        }
        // 精英 / 冠军：翻牌抽卡；其余直接进下一层
        if (shouldDrawReward()) main.postDelayed(this::openRewardDraw, 380);
        else main.postDelayed(this::advanceLayer, 380);
    }

    /** 是否翻牌：远征精英（第 2 层）；无尽冠军（每 5 层）。 */
    private boolean shouldDrawReward() {
        if ("campaign".equals(mode)) return layer == 2;
        if ("endless".equals(mode)) return layer % 5 == 0;
        return false;
    }

    /** 进入下一层。 */
    private void advanceLayer() {
        layer++;
        showFeed("⚔ 击杀！进入第 " + layer + " 层", Ui.GOLD);
        spawnEnemy();
        main.postDelayed(this::nextQuiz, 500);
    }

    /** 打开战利品翻牌，确认后应用奖励并进入下一层。 */
    private void openRewardDraw() {
        final RewardDrawView[] holder = new RewardDrawView[1];
        RewardDrawView rw = new RewardDrawView(getContext(), r -> {
            applyReward(r);
            if (holder[0] != null) removeView(holder[0]);
            advanceLayer();
        });
        holder[0] = rw;
        addView(rw, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** 应用翻牌奖励。 */
    private void applyReward(RewardDrawView.Reward r) {
        try {
            switch (r.kind) {
                case "heal":
                    hp = Math.min(100, hp + r.amount);
                    playerHp.setRatio(hp / 100f);
                    showFeed("❤️ 生命 +" + r.amount, Ui.GREEN);
                    break;
                case "damage":
                    bonusDamageMult = 1.0 + r.amount / 100.0;
                    showFeed("🗡 伤害增益 ×" + bonusDamageMult, r.color);
                    break;
                case "time":
                    bonusTime = r.amount;
                    showFeed("⏳ 下题时间 +" + r.amount + " 秒", r.color);
                    break;
                case "xp":
                    new Thread(() -> {
                        try {
                            JSONObject b = new JSONObject();
                            b.put("amount", r.amount);
                            JSONObject res = act.router().dispatch("/reward/xp", "POST", b);
                            int lv = res.optInt("level", 1);
                            if (lv > playerLevel) {
                                playerLevel = lv;
                                main.postDelayed(this::onLevelUp, 200);
                            }
                        } catch (Throwable ignore) {}
                    }).start();
                    showFeed("🌟 XP +" + r.amount, r.color);
                    break;
            }
        } catch (Throwable ignore) {}
    }

    /** 结算本局。 */
    private void finishRun(boolean win) {
        defeated = !win;
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

    @Override
    protected void onDetachedFromWindow() {
        if (getBackground() instanceof AmbientBackground) ((AmbientBackground) getBackground()).stop();
        if (idleAnim != null) idleAnim.cancel();
        super.onDetachedFromWindow();
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
        timeLimit = quiz.optDouble("timeLimit", 6) + bonusTime;   // V11.3 战利品时间延长
        if (bonusTime > 0) bonusTime = 0;
        remainSec = timeLimit;
        timerOn = true;
        timerTxt.setText(secText(remainSec));
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
                timerTxt.setText(secText(remainSec));
                setTimerFill((float) Math.max(0, remainSec / timeLimit));
                if (remainSec <= 2.5) timerTxt.setTextColor(Ui.ORANGE);
                else timerTxt.setTextColor(Ui.MUT);
                main.postDelayed(this, 100);
            }
        };
        main.postDelayed(timerTick, 100);
    }

    private String secText(double s) {
        if (s <= 0) return "0.0 秒";
        if (s > 3) return (int) Math.ceil(s) + " 秒";   // 前段整数秒，简洁不跳
        return String.format("%.1f", s) + " 秒";          // 最后 3 秒显示小数，制造紧迫
    }

    private void setTimerFill(float pct) {
        try {
            int trackW = Math.max(dp(2), timerTrack.getWidth());
            int target = Math.max(dp(2), (int) (trackW * Math.max(0f, Math.min(1f, pct))));
            int from = Math.max(dp(2), timerFill.getWidth());
            if (from == target) return;
            if (fillAnim != null) fillAnim.cancel();
            fillAnim = ValueAnimator.ofInt(from, target);
            fillAnim.setDuration(90);
            fillAnim.setInterpolator(new LinearInterpolator());
            fillAnim.addUpdateListener(a -> {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) a.getAnimatedValue(), dp(8));
                timerFill.setLayoutParams(lp);
            });
            fillAnim.start();
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

    // ── 敌人光晕 / 待机浮动（V11.3） ─────────────────────

    private void applyEnemyGlow(boolean boss, boolean elite, boolean champion) {
        int color = boss ? Ui.RED : elite ? Ui.ORANGE : Ui.CYAN;
        if (champion) color = Ui.GOLD;
        float centerA = boss ? 0.50f : elite ? 0.38f : 0.22f;
        if (champion) centerA = 0.46f;
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(dp(49));
        g.setColors(new int[]{alphaColor(color, centerA), alphaColor(color, 0f)});
        enemyGlow.setBackground(g);
        if (enemyCard != null) enemyCard.setBackground(Ui.card(16, Ui.CARD, boss ? 2 : 1, color));
    }

    private void idleFloat() {
        if (idleAnim != null) idleAnim.cancel();
        boolean boss = enemy != null && enemy.optBoolean("boss", false);
        idleAnim = ObjectAnimator.ofFloat(enemyEmoji, "translationY", 0f, -dp(7), 0f, dp(4), 0f);
        idleAnim.setDuration(boss ? 2600 : 2100);
        idleAnim.setRepeatCount(ObjectAnimator.INFINITE);
        idleAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        idleAnim.start();
    }

    private static int alphaColor(int color, float a) {
        int alpha = Math.round(255 * Math.max(0f, Math.min(1f, a)));
        return (alpha << 24) | (color & 0x00FFFFFF);
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

    private void pulse(View v) {
        try {
            v.animate().scaleX(1.35f).scaleY(1.35f).setDuration(80)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
        } catch (Throwable ignore) {}
    }

    /** 伤害飘字：在敌人位置上方弹出，上浮+放大+淡出 */
    private void popDmg(String txt, int color) {
        try {
            dmgPop.setText(txt);
            dmgPop.setTextColor(color);
            int[] ec = new int[2];
            enemyEmoji.getLocationInWindow(ec);
            int[] bc = new int[2];
            getLocationInWindow(bc);
            float cx = ec[0] - bc[0] + enemyEmoji.getWidth() / 2f;
            float cy = ec[1] - bc[1] + enemyEmoji.getHeight() / 2f;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dmgPop.getLayoutParams();
            lp.leftMargin = (int) (cx - dp(34));
            lp.topMargin = (int) (cy - dp(44));
            dmgPop.setLayoutParams(lp);
            dmgPop.setVisibility(VISIBLE);
            dmgPop.setAlpha(1f);
            dmgPop.setScaleX(0.8f);
            dmgPop.setScaleY(0.8f);
            dmgPop.animate().translationY(-dp(34)).scaleX(1.25f).scaleY(1.25f).setDuration(260).start();
            dmgPop.animate().alpha(0f).scaleX(1.1f).scaleY(1.1f).setStartDelay(240).setDuration(360)
                    .withEndAction(() -> {
                        dmgPop.setVisibility(GONE);
                        dmgPop.setTranslationY(0f);
                    }).start();
        } catch (Throwable ignore) {}
    }

    /** XP 飘字：玩家头顶弹出，上浮淡出（V11.3 奖励感）。 */
    private void popXp(int xp) {
        try {
            TextView t = new TextView(getContext());
            t.setText("✨ +" + xp + " XP");
            t.setTextSize(13);
            t.setTextColor(Ui.CYAN);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            int[] pc = new int[2];
            playerEmoji.getLocationInWindow(pc);
            int[] bc = new int[2];
            getLocationInWindow(bc);
            float cx = pc[0] - bc[0] + playerEmoji.getWidth() / 2f;
            float cy = pc[1] - bc[1];
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
            lp.leftMargin = (int) (cx - dp(30));
            lp.topMargin = (int) (cy - dp(18));
            addView(t, lp);
            t.animate().translationY(-dp(32)).alpha(0f).setDuration(900)
                    .withEndAction(() -> { try { removeView(t); } catch (Throwable ignore) {} }).start();
        } catch (Throwable ignore) {}
    }

    /** 升级：全屏金色光爆 + LEVEL UP + 环粒子 + 强震动（V11.3 成瘾节点）。 */
    private void onLevelUp() {
        showFeed("✦ 升级！Lv." + playerLevel + " ✦", Ui.GOLD);
        burstParticles(getWidth() / 2f, getHeight() / 2f, 22, 0xFFFBBF24, 90);
        Ui.vibrate(getContext(), new long[]{0, 40, 30, 60, 30, 80, 40, 100});
    }

    /** 心流里程碑光环：紫色圆环从中心扩散淡出（V11.3）。 */
    private void ringBurst() {
        try {
            View ring = new View(getContext());
            int size = dp(40);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0x00000000);
            g.setStroke(dp(3), Ui.PURPLE);
            ring.setBackground(g);
            addView(ring, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            ring.animate().scaleX(7f).scaleY(7f).alpha(0f).setDuration(620)
                    .withEndAction(() -> { try { removeView(ring); } catch (Throwable ignore) {} }).start();
        } catch (Throwable ignore) {}
    }

    /** 粒子爆发：从给定点向四周飞散的金色（暴击/复仇紫金色）粒子 */
    private void burstParticles(float cx, float cy, int n, int color, float spread) {
        try {
            for (int i = 0; i < n; i++) {
                View p = new View(getContext());
                int sz = dp(3f + (float) Math.random() * 4f);
                p.setBackground(Ui.card(999, color, 0, 0));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sz, sz);
                lp.leftMargin = (int) (cx - sz / 2f);
                lp.topMargin = (int) (cy - sz / 2f);
                addView(p, lp);
                double ang = Math.random() * Math.PI * 2;
                float dist = dp(spread * (0.5f + (float) Math.random() * 0.7f));
                float dx = (float) (Math.cos(ang) * dist);
                float dy = (float) (Math.sin(ang) * dist) - dp(10);
                final View fv = p;
                p.animate().translationX(dx).translationY(dy).alpha(0f).scaleX(0.4f).scaleY(0.4f)
                        .setDuration(380 + (long) (Math.random() * 220))
                        .withEndAction(() -> {
                            try { removeView(fv); } catch (Throwable ignore) {}
                        }).start();
            }
        } catch (Throwable ignore) {}
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
}
