package com.guzifeng.worddungeon.ui;

import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
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
import com.guzifeng.worddungeon.core.DamageCalc;
import com.guzifeng.worddungeon.core.GameConfig;

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
    private int enc = 0;                  // V12.0：本层内第几场（0 起），过层归零
    private int layerEncTotal = 1;        // 本层总场数（后端下发 layer_enc_total）
    private boolean layerFinal = false;    // 刚召唤的这场是否本层末场（后端下发 is_layer_final）
    private String layerName = "";        // 层名（后端下发）
    private AmbientBackground ambient;   // 氛围背景引用，用于按层切辉光色相
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
    // V11.7 对齐 V7.0：商店祝福（下一层伤害+25%）
    private boolean pendingBless = false;
    // R2-A：25 词典卡机制标志（按 V7.0 CARDS 语义接线）
    private double critChance = 0.0;        // 暴击率（灵光乍现+15% / 暴击风暴+25%）
    private double critMult = 2.0;         // 暴击倍率（致命一击改 ×3）
    private boolean comboHalved = false;   // 势如破竹：答错连击减半而非清零
    private int shield = 0;                // 错词护盾：开局 6 点，按次抵挡受击
    private boolean soulMagnet = false;    // 词魂引力：胜利词魂 +40%
    private double lifestealPct = 0.0;     // 汲词：答对按伤害 10% 回血
    private boolean immortal = false;      // 不朽：致命伤以 1 HP 存活一次
    private boolean immortalUsed = false;
    // GAP-10：当前战斗内浮层（翻牌/泉/商店），返回键先关浮层而非弹退出确认
    private View overlayView = null;
    // GAP-01：抽卡 pity 计数（普通保底 / 史诗保底），由后端 /card/draw 返回值驱动
    private int pity = 0;
    private int epicPity = 0;
    // V11.7 对齐 V7.0：心流/凝神/微目标/词魂/层间泉与商店
    private int wrongStreak = 0;           // 连错计数（凝神触发：≥2）
    private boolean flowOn = false;        // 当前是否在心流状态
    private String goalKind = "";           // none / combo4 / correct5 / flawless
    private boolean lastGoalDone = false;   // UI-iteration-07：记录目标达成跳变帧，只跳一次
    private int lastHp = 100;               // UI-iteration-12：记录上次玩家血量，判断是否受扣血
    private int lastSouls = 0;               // UI-iteration-16：记录上次词魂，判断是否获得（收益弹跳）
    private int quickDone = 0;             // 快刷已刷词数
    private int quickKills = 0;            // 快刷连续击杀数（持续模式）
    private final java.util.Map<String, String[]> layerWrong = new java.util.LinkedHashMap<>(); // 本层错词 word→[pos,mean]
    private android.speech.tts.TextToSpeech tts;
    private boolean ttsReady = false;

    // UI
    private TextView topTxt, enemyName, enemyEmoji, playerEmoji, playerName;
    private TextView enemyTag, warnTxt, hpNumTxt, comboLab, comboNum, comboSub;
    private LinearLayout topRight;
    private TextView comboTag;   // V12.7 顶栏连击数（手绘框架：顶部圆形徽+连击数）
    private HpBar enemyHp, playerHp;
    private TextView posTag, revTag, promptTxt, ladderTxt, qBody, timerTxt;
    private TextView enemyLevel;              // V12.6 敌人等级图标行（替代名字）
    private FrameLayout feedbackBar;          // V12.6 底部反馈条（连击⇄对错）
    private LinearLayout comboBox, resultBox; // V12.6 连击态 / 对错态容器
    private TextView resultBig, resultMsg;    // V12.6 对错反馈大字与文案
    private FrameLayout answerArea;
    private LinearLayout timerTrack;
    private View timerFill;
    private ValueAnimator fillAnim;
    private TextView feedbackLayer;
    private LinearLayout enemyCard;
    private LinearLayout playerHudBox;   // 玩家HUD（快刷隐藏）
    private View enemyGlow;
    private ObjectAnimator idleAnim;
    private TextView dmgPop;
    private EditText spellInput;
    private TextView confirmBtn;
    private LinearLayout spellRow;
    // V11.7 V7.0 对齐：微目标卡 / 玩家HUD词魂 / 低血vignette / 心流·凝神横幅
    private LinearLayout goalBox;
    private TextView goalTxt, goalState;
    private TextView soulNum, hpLab;
    // V11.8 投射打击：记录本次点击来源坐标（选项/判断/拼写框），发射攻击弹飞向敌人
    private float tapX = -1, tapY = -1;
    private View lowHpVignette;
    private TextView flowBanner;
    private TextView summonBanner;              // 词魔出现提示（自由位置 x73 y30）
    private Runnable hideSummonBanner, hideFeedbackBar;
    // V11.5 答题反馈：选项引用（对错高亮）+ 拼写字母格
    private final List<View> optionViews = new ArrayList<>();
    private final List<String> optionValues = new ArrayList<>();
    private LinearLayout tileRow;                 // 拼写字母格容器
    private final List<TextView> tileViews = new ArrayList<>();
    private int tileLen = 0;
    // V11.8 连击心流槽：5 格青色 dots，答对点亮、满 5 触发心流时闪光（open loop 前瞻钩子）
    private final List<TextView> comboDots = new ArrayList<>();
    // R1-1：上次已点亮格数，用于判定 off→on 的新点亮格触发轻微弹动
    private int dotFillApplied = 0;

    private static final Map<String, String> EMOJI = new HashMap<>();
    static {
        EMOJI.put("wordfiend", "👹"); EMOJI.put("confuser", "😈"); EMOJI.put("speller", "🧟");
        EMOJI.put("ghost", "👻"); EMOJI.put("polysem", "🎭");
        EMOJI.put("boss1", "👑"); EMOJI.put("boss2", "🌫"); EMOJI.put("boss3", "🔥");
    }

    public BattleView(Context c) {
        super(c);
        act = (MainActivity) c;
        ambient = new AmbientBackground(34);   // V11.3 地下城氛围底（战斗页粒子略多）
        setBackground(ambient);              // V12.0：保留引用以便按层切辉光色相
        build();
    }

    private void build() {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(16));
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 顶栏（左「第 N 层 · 词境」，右 节点圆点 / 快刷退出）
        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        topTxt = Ui.text(getContext(), "第 1 层 · 词境", 16, Ui.CYAN, true);
        top.addView(topTxt, new LinearLayout.LayoutParams(0, -2, 1f));
        topRight = new LinearLayout(getContext());
        topRight.setOrientation(LinearLayout.HORIZONTAL);
        topRight.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(topRight);
        col.addView(top);
        col.addView(spacer(8));

        // 敌人卡（face 光晕 + emoji + 名字 + 等级 + 警告 + 血条 + 微目标）
        LinearLayout ebox = new LinearLayout(getContext());
        ebox.setOrientation(LinearLayout.VERTICAL);
        ebox.setGravity(Gravity.CENTER);
        ebox.setPadding(dp(12), dp(10), dp(12), dp(10));
        ebox.setBackground(Ui.card(16, Ui.CARD, 1, Ui.LINE));
        FrameLayout faceWrap = new FrameLayout(getContext());
        enemyGlow = new View(getContext());
        int glowSize = dp(86);
        enemyGlow.setLayoutParams(new FrameLayout.LayoutParams(glowSize, glowSize, Gravity.CENTER));
        enemyEmoji = Ui.text(getContext(), "👹", 42, Ui.TXT, false);
        faceWrap.addView(enemyGlow);
        faceWrap.addView(enemyEmoji, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        ebox.addView(faceWrap);
        enemyName = Ui.text(getContext(), "", 18, Ui.TXT, true);
        enemyName.setGravity(Gravity.CENTER);
        ebox.addView(enemyName);
        enemyTag = Ui.text(getContext(), "普通战", 12, Ui.CYAN, true);
        enemyTag.setGravity(Gravity.CENTER);
        ebox.addView(enemyTag);
        warnTxt = Ui.text(getContext(), "答错 / 超时将受到 10 点伤害", 11, Ui.MUT, false);
        warnTxt.setGravity(Gravity.CENTER);
        ebox.addView(warnTxt);
        enemyHp = new HpBar(getContext(), Ui.RED, Ui.CARD2);
        enemyHp.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(8)));
        ebox.addView(enemyHp);
        hpNumTxt = Ui.text(getContext(), "100 / 100", 12, Ui.TXT, true);
        hpNumTxt.setGravity(Gravity.CENTER);
        ebox.addView(hpNumTxt);
        // 微目标行（左目标 + 右状态）
        LinearLayout goalBoxV = new LinearLayout(getContext());
        goalBoxV.setOrientation(LinearLayout.HORIZONTAL);
        goalBoxV.setGravity(Gravity.CENTER_VERTICAL);
        goalBoxV.setPadding(dp(12), dp(8), dp(12), dp(8));
        goalBoxV.setBackground(Ui.card(12, Ui.CARD2, 0, 0));
        goalTxt = Ui.text(getContext(), "", 13, Ui.TXT, false);
        goalState = Ui.text(getContext(), "", 13, Ui.GOLD, true);
        goalBoxV.addView(goalTxt, new LinearLayout.LayoutParams(0, -2, 1f));
        goalBoxV.addView(goalState);
        LinearLayout.LayoutParams goalLp = new LinearLayout.LayoutParams(-1, -2);
        goalLp.topMargin = dp(8);
        ebox.addView(goalBoxV, goalLp);
        goalBox = goalBoxV;
        enemyLevel = Ui.text(getContext(), "💠", 14, Ui.CYAN, false); // 保留引用，不显示
        enemyCard = ebox;
        col.addView(ebox);
        col.addView(spacer(8));

        // 连击行（独立：连击 + 数字 + 心流提示，下方心流圆点）
        LinearLayout comboBoxV = new LinearLayout(getContext());
        comboBoxV.setOrientation(LinearLayout.VERTICAL);
        comboBoxV.setGravity(Gravity.CENTER);
        comboBoxV.setPadding(dp(10), dp(2), dp(10), dp(2));
        LinearLayout comboRow = new LinearLayout(getContext());
        comboRow.setOrientation(LinearLayout.HORIZONTAL);
        comboRow.setGravity(Gravity.CENTER);
        comboLab = Ui.text(getContext(), "连击", 13, Ui.MUT, true);
        comboRow.addView(comboLab);
        comboNum = Ui.text(getContext(), "0", 24, Ui.GOLD, true);
        comboNum.setPadding(dp(8), 0, dp(8), 0);
        comboRow.addView(comboNum);
        comboSub = Ui.text(getContext(), "", 13, Ui.GOLD, false);
        comboRow.addView(comboSub);
        comboBoxV.addView(comboRow);
        LinearLayout dotRow = new LinearLayout(getContext());
        dotRow.setOrientation(LinearLayout.HORIZONTAL);
        dotRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < 5; i++) {
            TextView d = Ui.text(getContext(), "", 6, Ui.TEAL, false);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(8), dp(8));
            d.setBackground(Ui.pill(0xFF0E2238, 0xFF33507A));
            d.setAlpha(0.55f);
            if (i > 0) dl.leftMargin = dp(6);
            dotRow.addView(d, dl);
            comboDots.add(d);
        }
        comboBoxV.addView(dotRow);
        col.addView(comboBoxV);
        comboBox = comboBoxV;
        col.addView(spacer(6));

        // 题目卡（qhead 大字 + 词性徽章 + 复仇徽章 + 提示 + 阶梯 + 计时）
        LinearLayout qbox = new LinearLayout(getContext());
        qbox.setOrientation(LinearLayout.VERTICAL);
        qbox.setPadding(dp(14), dp(10), dp(14), dp(10));
        qbox.setBackground(Ui.card(14, Ui.CARD2, 1, Ui.LINE));   // UI-iteration-10：题干盒提亮 CARD2，与选项区同级
        LinearLayout headRow = new LinearLayout(getContext());
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        qBody = Ui.text(getContext(), "", 22, Ui.TXT, true);
        qBody.setGravity(Gravity.CENTER);
        headRow.addView(qBody, new LinearLayout.LayoutParams(0, -2, 1f));
        posTag = Ui.text(getContext(), "", 12, Ui.GOLD, true);
        posTag.setSingleLine(true);   // V12.5 词性标签不换行、不被挤压
        LinearLayout.LayoutParams posLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        posTag.setLayoutParams(posLp);
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
        timerTxt = Ui.text(getContext(), "", 13, Ui.GOLD, true);   // UI-iteration-08：秒数提级 13sp 金加粗
        timerTxt.setGravity(Gravity.CENTER);
        qbox.addView(timerTxt);
        col.addView(qbox);
        col.addView(spacer(8));

        // 答题区（V11.5 动态填充：选项 / 判断 / 拼写）
        answerArea = new FrameLayout(getContext());
        answerArea.setBackground(Ui.card(14, Ui.CARD2, 1, Ui.LINE));
        col.addView(answerArea, new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT));
        col.addView(spacer(8));

        // 玩家 HUD（V7.0 playerhud：生命标签 + 绿血条 + 词魂计数）
        LinearLayout pbox = new LinearLayout(getContext());
        pbox.setOrientation(LinearLayout.HORIZONTAL);
        pbox.setGravity(Gravity.CENTER_VERTICAL);
        pbox.setPadding(dp(10), dp(8), dp(10), dp(8));
        pbox.setBackground(Ui.card(12, Ui.CARD2, 0, 0));
        playerEmoji = Ui.text(getContext(), "🧙", 26, Ui.TXT, false);
        hpLab = Ui.text(getContext(), "生命 100/100", 13, Ui.TXT, true);
        hpLab.setTypeface(Typeface.DEFAULT_BOLD);
        playerHp = new HpBar(getContext(), Ui.GREEN, Ui.CARD);
        playerHp.setDynamicColor(true);   // UI-iteration-03：玩家血条随血量绿→金→红（敌血条不开，保持红/击杀转金）
        soulNum = Ui.text(getContext(), "💠 词魂 0", 14, Ui.GOLD, true);
        soulNum.setTypeface(Typeface.DEFAULT_BOLD);
        soulNum.setPadding(dp(8), dp(4), dp(8), dp(4));
        soulNum.setBackground(Ui.card(999, 0x33FCD34D, 1, 0x66FCD34D));
        pbox.addView(playerEmoji);
        pbox.addView(Ui.spacer(getContext(), 6));
        pbox.addView(hpLab);
        pbox.addView(Ui.spacer(getContext(), 8));
        pbox.addView(playerHp, new LinearLayout.LayoutParams(0, dp(10), 1f));
        pbox.addView(Ui.spacer(getContext(), 8));
        pbox.addView(soulNum);
        col.addView(pbox);
        playerHudBox = pbox;

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

        // V7.0 低血量红 vignette（半透明红色径向遮罩，仅 hp<=30 时显现）
        lowHpVignette = new View(getContext());
        GradientDrawable vg = new GradientDrawable();
        vg.setShape(GradientDrawable.OVAL);
        vg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        vg.setGradientRadius(Math.max(dp(300), dp(340)));
        vg.setColors(new int[]{0x55F87171, 0x00000000});
        lowHpVignette.setBackground(vg);
        lowHpVignette.setVisibility(GONE);
        addView(lowHpVignette, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // V7.0 心流/凝神横幅（顶部居中，短闪现）
        flowBanner = new TextView(getContext());
        flowBanner.setTextSize(19);
        flowBanner.setTypeface(Typeface.DEFAULT_BOLD);
        flowBanner.setGravity(Gravity.CENTER);
        flowBanner.setPadding(dp(18), dp(9), dp(18), dp(9));
        flowBanner.setVisibility(GONE);
        FrameLayout.LayoutParams fbLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        fbLp.topMargin = dp(70);
        addView(flowBanner, fbLp);

        // 答题反馈条（V12.7.11：紧凑内容尺寸 WRAP_CONTENT——修复无参数 addView 默认 MATCH_PARENT
        //  导致黑框铺满全屏的尺寸 bug；位置放用户圈定区 y34 顶部，中心≈40% 不遮单词）
        feedbackBar = new FrameLayout(getContext());
        feedbackBar.setVisibility(GONE);
        feedbackBar.setBackground(Ui.card(12, 0xFF000000, 2, Ui.GOLD));
        LinearLayout resultV = new LinearLayout(getContext());
        resultV.setOrientation(LinearLayout.VERTICAL);
        resultV.setGravity(Gravity.CENTER);
        resultV.setPadding(dp(16), dp(8), dp(16), dp(8));
        resultBig = Ui.text(getContext(), "✓", 24, Ui.GREEN, true);
        resultBig.setGravity(Gravity.CENTER);
        resultV.addView(resultBig);
        resultMsg = Ui.text(getContext(), "", 13.5f, Ui.GREEN, true);
        resultMsg.setGravity(Gravity.CENTER);
        resultV.addView(resultMsg);
        feedbackBar.addView(resultV, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        addView(feedbackBar, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        resultBox = resultV;
        placeAt(feedbackBar, 50, 34);

        // 词魔出现提示（V12.7.11：同样修复 MATCH_PARENT 尺寸 bug，紧凑内容尺寸）
        summonBanner = new TextView(getContext());
        summonBanner.setTextSize(13);
        summonBanner.setTypeface(Typeface.DEFAULT_BOLD);
        summonBanner.setGravity(Gravity.CENTER);
        summonBanner.setPadding(dp(10), dp(5), dp(10), dp(5));
        summonBanner.setVisibility(GONE);
        summonBanner.setBackground(Ui.card(10, 0x660F2A3A, 1, 0xAA38BDF8));
        addView(summonBanner, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        placeAt(summonBanner, 50, 34);   // V12.7.11 与错误提示同位置（y34）
    }

    /** 自由元素按百分比定位：x/y 为相对屏幕的百分比（x 为元素中心，y 为元素顶部）。
     *  V12.7.10 基准改为 getRootView()（屏幕），避免宿主布局高度不等于屏高时（WRAP_CONTENT）
     *  百分比定位被内容高放大、浮层落到屏幕下部的问题。 */
    private void placeAt(View v, double xPct, double yPct) {
        post(() -> {
            try {
                View rootView = getRootView();
                int w = rootView.getWidth(), h = rootView.getHeight();
                if (w <= 0 || h <= 0) return;
                v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
                int vw = v.getMeasuredWidth();
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
                lp.width = FrameLayout.LayoutParams.WRAP_CONTENT;   // V12.7.11 防御：浮层必须内容自适应，防 MATCH_PARENT 铺满全屏
                lp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
                lp.gravity = Gravity.TOP | Gravity.LEFT;
                lp.leftMargin = (int) Math.round(w * xPct / 100.0) - vw / 2;
                lp.topMargin = (int) Math.round(h * yPct / 100.0);
                v.setLayoutParams(lp);
            } catch (Throwable ignore) {}
        });
    }

    // ── 战斗流程 ─────────────────────────────

    /** V11.8 玩家 HUD 统一刷新：血条百分比 + 词魂余额。 */
    private void updateHud() {
        try {
            int newHp = Math.max(0, hp);
            if (hpLab != null) {
                hpLab.setText("生命 " + newHp + "/100");
                // UI-iteration-12：受扣血（newHp<lastHp）时数字闪红，与敌人数受击弹跳 i11 对称的负面反馈；回血不闪
                if (newHp < lastHp) {
                    hpLab.setTextColor(Ui.RED);
                    hpLab.removeCallbacks(this::hpLabReset);
                    hpLab.postDelayed(this::hpLabReset, 350);
                }
            }
            lastHp = newHp;
            if (playerHp != null) playerHp.setRatio(newHp / 100f);
            // UI-iteration-16：词魂增加（答对获利）时计数器弹跳，赚到货币有获得感；消费不跳
            int newSouls = act.souls;
            if (soulNum != null) {
                soulNum.setText("💠 词魂 " + newSouls);
                if (newSouls > lastSouls) {
                    soulNum.animate().cancel();
                    soulNum.setScaleX(1f); soulNum.setScaleY(1f);
                    soulNum.animate().scaleX(1.15f).scaleY(1.15f).setDuration(90)
                            .withEndAction(() -> soulNum.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                            .start();
                }
            }
            lastSouls = newSouls;
        } catch (Throwable ignore) {}
    }

    /** UI-iteration-12：玩家血量数字闪红后恢复常规色（方法引用作 Runnable，避免前向引用）。 */
    private void hpLabReset() { if (hpLab != null) hpLab.setTextColor(Ui.TXT); }

    /** 答题反馈条：浮在屏幕正中，先高不透明度停留约 1 秒后快速淡出。 */
    private void showAnswerResult(boolean ok, String text, int holdMs) {
        try {
            if (feedbackBar == null || resultBox == null) return;
            placeAt(feedbackBar, 50, 34);   // V12.7.11 每次显示强制定位（用户圈定区，y34 顶部≈中心40%，不遮单词）
            resultBig.setText(ok ? "✓" : "✗");
            resultBig.setTextColor(ok ? Ui.GREEN : Ui.RED);
            resultMsg.setText(text);
            resultMsg.setTextColor(ok ? Ui.GREEN : Ui.RED);
            feedbackBar.setBackground(Ui.card(12, 0xFF000000, 2, ok ? Ui.GREEN : Ui.RED));
            feedbackBar.setVisibility(VISIBLE);
            feedbackBar.setAlpha(1f);
            feedbackBar.setScaleX(0.85f);
            feedbackBar.setScaleY(0.85f);
            feedbackBar.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            main.removeCallbacks(hideFeedbackBar);
            hideFeedbackBar = () -> {
                try { feedbackBar.animate().alpha(0f).setDuration(250)
                        .withEndAction(() -> feedbackBar.setVisibility(GONE)).start(); } catch (Throwable ignore) {}
            };
            main.postDelayed(hideFeedbackBar, holdMs);
        } catch (Throwable ignore) {}
    }

    /** 词魔出现提示：弹出后高不透明度停留 0.5s，再快速淡出（V12.7.11 与错误提示节奏一致）。 */
    private void showSummonBanner() {
        try {
            if (summonBanner == null) return;
            summonBanner.setText("💠 词魔出现！击败它");
            summonBanner.setVisibility(VISIBLE);
            summonBanner.setAlpha(0f);
            summonBanner.setScaleX(0.8f);
            summonBanner.setScaleY(0.8f);
            summonBanner.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
            main.removeCallbacks(hideSummonBanner);
            hideSummonBanner = () -> summonBanner.animate().alpha(0f).setDuration(250)
                    .withEndAction(() -> summonBanner.setVisibility(GONE)).start();
            main.postDelayed(hideSummonBanner, 500);
        } catch (Throwable ignore) {}
    }

    public void startRun() {
        // 新一局前清掉上一局悬挂的全部回调（计时/自动提交/题链/飘字），防旧回调串题
        main.removeCallbacksAndMessages(null);
        hideKeyboard();
        mode = act.mode;
        book = act.book;
        difficulty = act.difficulty;
        runState = new RunState();
        layer = 1;
        enc = 0;          // V12.0：本层场次归零
        layerEncTotal = 1;
        layerFinal = false;
        hp = 100;
        combo = 0;
        wrongInRun = 0;
        questionsInRun = 0;
        correctInRun = 0;
        maxCombo = 0;
        killsInRun = 0;
        defeated = false;
        wrongStreak = 0;
        flowOn = false;
        quickDone = 0;
        quickKills = 0;
        layerWrong.clear();
        act.runNewWords.clear();
        act.runWrongWords.clear();
        act.runCards = 0;   // GAP-05：每局选卡计数归零
        pity = 0;
        epicPity = 0;
        // R2-A：卡机制标志每局重置
        critChance = 0.0; critMult = 2.0; comboHalved = false;
        shield = 0; soulMagnet = false; lifestealPct = 0.0;
        immortal = false; immortalUsed = false;
        // 词魂局内制（V11.8）：闯关/无尽各自一局，开局清零、局内获得局内使用，不跨局
        act.souls = 0;
        updateHud();
        lowHpVignette.setVisibility(GONE);
        flowBanner.setVisibility(GONE);
        initTts();
        if ("quick".equals(mode)) {
            // 快刷：无敌人、无玩家血条，纯快速答题 + 振动/音效反馈
            enemyCard.setVisibility(GONE);
            playerHudBox.setVisibility(GONE);
        } else {
            enemyCard.setVisibility(VISIBLE);
            playerHudBox.setVisibility(VISIBLE);
            spawnEnemy();
            battleIntro();
        }
        nextQuiz();
    }

    /** 离线 TTS（零网络）：设置页「单词发音」开关联动；不可用则静默不发音。 */
    private void initTts() {
        try {
            if (tts == null) {
                tts = new android.speech.tts.TextToSpeech(getContext(), t -> ttsReady = (t == android.speech.tts.TextToSpeech.SUCCESS));
            }
            if (ttsReady) tts.setLanguage(java.util.Locale.US);
        } catch (Throwable ignore) {}
    }

    private void speakWord(String w) {
        try {
            if (!act.speak || tts == null || !ttsReady || w == null || w.isEmpty()) return;
            tts.speak(w, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "wd_" + w);
        } catch (Throwable ignore) {}
    }

    private void spawnEnemy() {
        try {
            JSONObject body = new JSONObject();
            body.put("mode", mode);
            body.put("layer", layer);
            boolean campaign = "campaign".equals(mode);
            if (campaign) {
                body.put("enc", enc);   // V12.0：后端按 enc 裁决普通/守门者
            } else {
                body.put("kind", "endless".equals(mode) ? "normal" : layerKind());
            }
            JSONObject e = "quick".equals(mode)
                    ? act.router().dispatch("/enemy/spawn", "POST", bodyWithQuickLevel(body))
                    : act.router().dispatch("/enemy/spawn", "POST", body);
            enemy = e;
            // V12.0：读取后端下发的本层场次裁决
            if (campaign) {
                layerEncTotal = Math.max(1, e.optInt("layer_enc_total", 1));
                layerFinal = e.optBoolean("is_layer_final", false);
                layerName = e.optString("layer_name", "");
                if (ambient != null) ambient.setLayer(layer);
            }
            enemyName.setText(e.optString("name", "词魔"));
            enemyEmoji.setText(EMOJI.getOrDefault(e.optJSONObject("tpl").optString("id", ""), "👹"));
            // R1-7/R2-3：先瞬时复位上一轮击杀消散残留（scale 归位，绝不残留消散态），
            // 再做 R2 新怪入场：从透明 + 下方 10dp 上浮淡入（230ms），结束后交回 idleFloat 待机浮动。
            enemyEmoji.animate().cancel();
            if (idleAnim != null) idleAnim.cancel();   // 停掉上一层待机浮动，避免与入场 translationY 冲突
            enemyEmoji.setScaleX(0.6f); enemyEmoji.setScaleY(0.6f);   // UI-iteration-18：入场缩放起点 0.6
            enemyEmoji.setAlpha(0f);                 // 淡入起点
            enemyEmoji.setTranslationY(dp(10));     // 轻微上浮入场起点（下方 10dp）
            // UI-iteration-14：头部文字（名字/战斗徽章/等级图标）随 emoji 入场一起淡入，不再凭空出现
            enemyName.setAlpha(0f);
            enemyTag.setAlpha(0f);
            enemyLevel.setAlpha(0f);
            enemyHp.setRatio(1f);
            enemyHp.setCritical(false);   // UI-iteration-04：新敌满血不闪
            int maxHp = e.optInt("maxHp", 100);
            hpNumTxt.setText(maxHp + " / " + maxHp);
            int atk = e.optInt("atk", 10);
            boolean boss = e.optBoolean("boss", false);
            boolean elite = e.optBoolean("elite", false);
            enemyTag.setText(boss ? "Boss 战" : elite ? "精英战" : "普通战");
            enemyTag.setTextColor(boss ? Ui.RED : elite ? Ui.ORANGE : Ui.CYAN);
            // V12.6 等级图标：👑首领 / ⚡精英 / 💠普通（champion 金色）
            boolean champion = e.optBoolean("champion", false);
            enemyLevel.setText(boss ? "👑" : elite ? "⚡" : "💠");
            enemyLevel.setTextColor(boss ? Ui.RED : elite ? Ui.ORANGE : champion ? Ui.GOLD : Ui.CYAN);
            applyEnemyGlow(boss, elite, champion);
            // R2-3：新怪入场演出（淡入+上浮 230ms），结束后启动待机浮动循环
            enemyEmoji.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)   // UI-iteration-18：补缩放弹出 0.6→1.0
                    .setDuration(230).withEndAction(this::idleFloat).start();
            // UI-iteration-14：头部文字 230ms 同步淡入到不透明
            enemyName.animate().alpha(1f).setDuration(230).start();
            enemyTag.animate().alpha(1f).setDuration(230).start();
            enemyLevel.animate().alpha(1f).setDuration(230).start();
            warnTxt.setText("答错 / 超时将受到 " + atk + " 点伤害");
            enemyName.setTextColor(e.optBoolean("champion", false) ? Ui.GOLD : Ui.TXT);
            if (campaign) {
                topTxt.setText("第 " + layer + " 层 · " + layerName + "  " + (enc + 1) + "/" + layerEncTotal);
                // 每层第一场播报层名
                if (enc == 0 && !layerName.isEmpty()) {
                    showFeed("🗺 " + layerName, Ui.GOLD);
                }
                // 第 5 层最终 Boss 出场仪式：红光冲击 + 震波 + 播报
                if (layer >= GameConfig.LAYER_COUNT && boss) {
                    flashRed(enemyEmoji);
                    Ui.vibrate(getContext(), new long[]{0, 60, 40, 80, 40, 120});
                    showFeed("⚠ 最终Boss · 词魇·深渊领主 降临", Ui.RED);
                }
            } else {
                topTxt.setText("第 " + layer + " 层 · 词境");
            }
            buildTopRight();
            showSummonBanner();
            pickGoal(boss, elite);
            soulNum.setText("词魂 " + act.souls);
            lowHpVignette.setVisibility(hp <= 30 ? VISIBLE : GONE);
        } catch (Throwable e) {
            showFeed("敌人召唤失败", Ui.RED);
        }
    }

    /** UI-iteration-05：连击数字三档成长——≤2 金24 / 3-4 橙28 / ≥5 青32（心流）。pulse 仍在其上叠加，互不冲突。 */
    private void refreshComboNumStyle() {
        if (comboNum == null) return;
        if (combo >= 5)      { comboNum.setTextSize(32f); comboNum.setTextColor(Ui.CYAN); }
        else if (combo >= 3) { comboNum.setTextSize(28f); comboNum.setTextColor(Ui.ORANGE); }
        else                 { comboNum.setTextSize(24f); comboNum.setTextColor(Ui.GOLD); }
    }

    /** V12.7 顶栏连击数：连击≥2 显示 🔥xN，否则留空。 */
    private void refreshComboTag() {
        if (comboTag == null) return;
        comboTag.setText(combo >= 2 ? ("🔥 " + combo) : "");
    }

    /** V7.0 顶栏右侧：远征/无尽 = 节点圆点进度；快刷 = ‹ 退出快刷 + 已刷 N 词。 */
    private void buildTopRight() {
        topRight.removeAllViews();
        if ("quick".equals(mode)) {
            TextView out = Ui.text(getContext(), "‹ 退出快刷", 14, Ui.CYAN, true);
            Ui.pressable(out);
            out.setOnClickListener(v -> onBack());
            topRight.addView(out);
            TextView done = Ui.text(getContext(), "已刷 " + quickDone + " 词", 14, Ui.MUT, false);
            topRight.addView(done);
            return;
        }
        // V12.0：远征节点数 3→5，当前节点 clamp 到 5
        int nodes = "campaign".equals(mode) ? GameConfig.LAYER_COUNT : 5;
        int cur = "campaign".equals(mode) ? Math.min(layer, GameConfig.LAYER_COUNT) : ((layer - 1) % 5) + 1;
        for (int i = 1; i <= nodes; i++) {
            View dot = new View(getContext());
            int sz = dp(9);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(sz, sz);
            lp.leftMargin = dp(4);
            dot.setLayoutParams(lp);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i < cur ? Ui.GOLD : i == cur ? Ui.GOLD : Ui.CARD2);
            if (i == cur) {
                g.setStroke(dp(1.5f), Ui.GOLD);
                dot.setBackground(g);
                // UI-iteration-17：当前节点点入场弹跳（0.4→1.0），提示楼层进度推进到下一站
                dot.setScaleX(0.4f); dot.setScaleY(0.4f);
                dot.post(() -> dot.animate().scaleX(1f).scaleY(1f).setDuration(220).start());
            } else {
                dot.setBackground(g);
            }
            topRight.addView(dot);
        }
    }

    /** R2-C：微目标改读后端下发 goal（streak4/hit5/flawless），缺省时本地兜底。 */
    private void pickGoal(boolean boss, boolean elite) {
        if (goalBox == null) return;   // V12.6 微目标卡已移除：仅保留 goalKind 空态
        if ("quick".equals(mode)) { goalBox.setVisibility(GONE); goalKind = ""; return; }
        String g = enemy != null ? enemy.optString("goal", "") : "";
        // 后端命名映射到内部 goalKind
        if ("streak4".equals(g)) goalKind = "streak4";
        else if ("hit5".equals(g)) goalKind = "hit5";
        else if ("flawless".equals(g)) goalKind = "flawless";
        else {
            String[] fallback = {"streak4", "hit5", "flawless"};
            goalKind = fallback[(int) (Math.random() * fallback.length)];
        }
        if ("streak4".equals(goalKind)) goalTxt.setText("本场连击达到 4");
        else if ("hit5".equals(goalKind)) goalTxt.setText("本场答对 5 题");
        else goalTxt.setText("本场无错击败敌人");
        goalState.setText("进行中");
        goalState.setTextColor(Ui.GOLD);
        lastGoalDone = false;   // UI-iteration-07：新敌目标重新计算，复位达成跳变帧
        goalBox.setVisibility(VISIBLE);
    }

    /** 微目标进度实时刷新（答题/击杀时调用）。 */
    private void refreshGoal() {
        if (goalBox == null || goalKind.isEmpty()) return;   // V12.6 修复 NPE：goalBox 已移除
        boolean done = false;
        if ("streak4".equals(goalKind)) done = maxCombo >= 4;
        else if ("hit5".equals(goalKind)) done = correctInRun >= 5;
        else if ("flawless".equals(goalKind)) done = wrongInRun == 0 && enemy.optInt("hp", 1) <= 0;
        // UI-iteration-13：可累积目标（streak4/hit5）过程中徽章带实时进度计数，flawless 保持二态
        String prog = "";
        if (!done) {
            if ("streak4".equals(goalKind)) prog = " " + Math.min(maxCombo, 4) + "/4";
            else if ("hit5".equals(goalKind)) prog = " " + Math.min(correctInRun, 5) + "/5";
        }
        goalState.setText(done ? "已达成" : "进行中" + prog);
        goalState.setTextColor(done ? Ui.GREEN : Ui.GOLD);
        // UI-iteration-07：目标刚达成那一帧，徽章弹跳一次（仅 false→true 跳变）
        if (done && !lastGoalDone) {
            goalState.animate().cancel();
            goalState.setScaleX(1f); goalState.setScaleY(1f);
            goalState.animate().scaleX(1.18f).scaleY(1.18f).setDuration(110)
                    .withEndAction(() -> goalState.animate().scaleX(1f).scaleY(1f).setDuration(130).start())
                    .start();
        }
        lastGoalDone = done;
    }

    private JSONObject bodyWithQuickLevel(JSONObject body) throws Exception {
        body.put("quick_level", act.quickLevel);
        return body;
    }

    /** 卡片抽包用敌人类型：按当前敌人实际身份（V12.0：不再按固定层数，直接读敌人标签）。 */
    private String layerKind() {
        if ("endless".equals(mode)) return "normal";
        if (enemy != null && enemy.optBoolean("boss", false)) return "boss";
        if (enemy != null && enemy.optBoolean("elite", false)) return "elite";
        return "normal";
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
            // GAP-07：当前敌人为 confuser 时下发 enemy_confuse=true（后端同组干扰项优先）
            if (enemy != null && enemy.optJSONObject("tpl") != null
                    && "confuser".equals(enemy.optJSONObject("tpl").optString("id", ""))) {
                body.put("enemy_confuse", true);
            }
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

        // 词性徽章（V7.0 POS_COLOR：n→cyan / v→orange / adj→green / adv→teal / prep→purple / 其余→mut）
        if (pos.isEmpty()) {
            posTag.setVisibility(GONE);
        } else {
            String pl = pos.toLowerCase();
            int color = Ui.MUT;
            if (pl.startsWith("n")) color = Ui.CYAN;
            else if (pl.startsWith("v")) color = Ui.ORANGE;
            else if (pl.startsWith("adj")) color = Ui.GREEN;
            else if (pl.startsWith("adv")) color = Ui.TEAL;
            else if (pl.startsWith("prep")) color = Ui.PURPLE;
            posTag.setText(pos + (review ? " · 复习" : ""));
            posTag.setTextColor(color);
            posTag.setPadding(dp(7), dp(2), dp(7), dp(2));
            posTag.setBackground(Ui.pill(Ui.BG_PILL, color));
            posTag.setVisibility(VISIBLE);
            posTag.setSingleLine(true);
        }
        // 新单词卡自动离线朗读美式发音（V7.0 行为；零网络，走系统 TTS）
        if (!"".equals(word)) speakWord(word);
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
                // V12.7.8 修复：judge 题显示 judgeShown（与后端判定依据一致），
                // 原显示 mean 会导致出题命中错误释义分支时"看到正确释义、判断对却被判错"
                String shown = quiz.optString("judgeShown", "");
                if (shown.isEmpty()) shown = mean;
                LinearLayout meanRow = new LinearLayout(getContext());
                meanRow.setOrientation(LinearLayout.HORIZONTAL);
                meanRow.setGravity(Gravity.CENTER);
                TextView mv = Ui.text(getContext(), shown, 19, Ui.CYAN, true);
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
                yes.setOnClickListener(v -> { recordTap(yes); submitJudge("yes"); });
                no.setOnClickListener(v -> { recordTap(no); submitJudge("no"); });
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
                // V12.5 格子按词长自适应：短词放大居中、长词收紧，视觉重心稳定
                int cellDp, gapDp;
                if (len <= 2) { cellDp = 50; gapDp = 10; }
                else if (len == 3) { cellDp = 46; gapDp = 8; }
                else if (len == 4) { cellDp = 42; gapDp = 6; }
                else if (len <= 6) { cellDp = 36; gapDp = 5; }
                else if (len <= 8) { cellDp = 32; gapDp = 4; }
                else { cellDp = 28; gapDp = 3; }
                for (int i = 0; i < len; i++) {
                    float fs = cellDp >= 46 ? 21 : cellDp >= 36 ? 18 : 16;
                    TextView t = Ui.text(getContext(), i == 0 ? String.valueOf(word.charAt(0)).toUpperCase() : "",
                            fs, i == 0 ? Ui.GOLD : Ui.TXT, true);
                    t.setGravity(Gravity.CENTER);
                    int ts = dp(cellDp);
                    t.setLayoutParams(new LinearLayout.LayoutParams(ts, ts));
                    t.setBackground(Ui.card(8, i == 0 ? Ui.CARD2 : Ui.BG0, 1,
                            i == 0 ? Ui.GOLD : Ui.LINE));
                    // V7.0：点格子/点提示自动聚焦拼写输入框（隐藏 input 等效处理）
                    final TextView ft = t;
                    t.setClickable(true);
                    Ui.pressable(t);   // V12.6 格子点击按压反馈
                    t.setOnClickListener(v -> focusSpell());
                    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ts, ts);
                    t.setLayoutParams(cl);
                    tileRow.addView(t);
                    if (i < len - 1) tileRow.addView(spacer(gapDp));
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
        // R2-2：切题从"硬切"改为 180ms 淡入 + 6dp 轻微上移（题干 + 答案区/选项容器）。
        // 仅消除瞬时替换，不改变答对 350ms / 答错 1100/1600ms 的答题节奏。
        quizEnterTransition();
    }

    /** R2-2：题干 qBody 与答案区 answerArea（含选项容器）从下方 6dp 淡入上浮，180ms 到位。 */
    private void quizEnterTransition() {
        try {
            qBody.animate().cancel();
            qBody.setAlpha(0f);
            qBody.setTranslationY(dp(6));
            qBody.setScaleX(0.92f);   // UI-iteration-06：单词入场 scale pop 落定
            qBody.setScaleY(0.92f);
            qBody.animate().alpha(1f).translationY(0f)
                    .scaleX(1f).scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                    .start();
            answerArea.animate().cancel();
            answerArea.setAlpha(0f);
            answerArea.setTranslationY(dp(6));
            answerArea.animate().alpha(1f).translationY(0f).setDuration(180).start();
        } catch (Throwable ignore) {}
    }

    private void addOptions(LinearLayout col, JSONArray options, boolean en2cn) {
        if (options == null) return;
        try {
            char[] keys = {'1', '2', '3', '4'};
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
                    // UI-iteration-01: 每选项独立持有 normal/pressed 两个 Drawable（不共用实例）
                    final GradientDrawable normalBg = Ui.card(12, Ui.BG1, 1, Ui.LINE);   // UI-iteration-09：常态提亮 BG1，按钮浮起
                    final GradientDrawable pressedBg = new GradientDrawable();
                    pressedBg.setColor(Ui.BG1);
                    pressedBg.setCornerRadius(dp(12));
                    pressedBg.setStroke(dp(1.5f), Ui.CYAN);
                    b.setBackground(normalBg);
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
                    // UI-iteration-01: 按压反馈强化——按下瞬间提亮(BG1)+1.5dp青边+缩到0.95；UP回弹还原
                    b.setClickable(true);
                    b.setOnTouchListener((view, e) -> {
                        switch (e.getActionMasked()) {
                            case android.view.MotionEvent.ACTION_DOWN:
                                view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
                                b.setBackground(pressedBg);
                                break;
                            case android.view.MotionEvent.ACTION_UP:
                                b.setBackground(normalBg);
                                view.animate().scaleX(1f).scaleY(1f).setDuration(180)
                                        .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
                                break;
                            case android.view.MotionEvent.ACTION_CANCEL:
                                b.setBackground(normalBg);
                                view.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                                break;
                        }
                        return false; // 不消费事件，保证 OnClickListener 照常触发
                    });
                    b.setOnClickListener(vi -> { recordTap(b); submitOption(opt); });
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

    /** 拼写字母格实时同步：输入字符写入对应格（首格锁定），只读不回写文本；满格金色脉冲高亮。 */
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
        // 满格：全部金色高亮脉冲（V7.0 满格确认态）
        if (tileLen > 0 && t.length() + 1 >= tileLen) {
            for (TextView tile : tileViews) {
                tile.setTextColor(Ui.GOLD);
                tile.setBackground(Ui.card(8, Ui.CARD2, 2, Ui.GOLD));
                tile.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120)
                        .withEndAction(() -> tile.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
            }
        }
    }

    /** 聚焦拼写输入框并弹键盘（点格子/提示时）。 */
    private void focusSpell() {
        try {
            if (spellInput == null) return;
            spellInput.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(spellInput, InputMethodManager.SHOW_IMPLICIT);
        } catch (Throwable ignore) {}
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
        if (spellInput != null) recordTap(spellInput);
        String t = spellInput.getText().toString().trim();
        submit(t);
    }

    /** 记录本次作答点击位置（投射打击起点）。坐标换算为相对本 View 根容器的位置。 */
    private void recordTap(View v) {
        try {
            int[] vc = new int[2];
            v.getLocationInWindow(vc);
            int[] bc = new int[2];
            getLocationInWindow(bc);
            tapX = vc[0] - bc[0] + v.getWidth() / 2f;
            tapY = vc[1] - bc[1] + v.getHeight() / 2f;
        } catch (Throwable ignore) {
            tapX = -1; tapY = -1;
        }
    }

    private void submit(String answer) {
        if (!answering || quiz == null) return;
        answering = false;
        stopTimer();
        try {
            // R2-A：暴击由卡驱动（默认 critChance=0），roll < critChance 则暴击；致命一击改 ×3
            boolean crit = critChance > 0 && Math.random() < critChance;
            double critMultSend = crit ? this.critMult : 1.0;
            JSONObject body = new JSONObject();
            body.put("word", quiz.optString("word"));
            body.put("qtype", quiz.optString("qtype"));
            body.put("answer", answer);
            body.put("time_limit", quiz.optDouble("timeLimit", 6));
            body.put("remain", remainSec);
            body.put("combo", combo);
            body.put("souls", act.souls);   // V11.8 局内词魂余额随答题上报（击杀增量在后端累加返回）
            body.put("crit", crit);
            body.put("crit_mult", critMultSend);
            body.put("card_mult", bonusDamageMult);   // V11.3 战利品伤害增益
            body.put("shown", quiz.isNull("judgeShown") ? JSONObject.NULL : quiz.optString("judgeShown"));
            body.put("mode", mode);
            if ("quick".equals(mode)) body.put("quick_level", act.quickLevel);
            // R4：击杀词魂入账接线——非快刷才预测本题是否击杀敌人并下发 kill 标志，
            // 后端仅在 isCorrect && kill && !quick 时入账（普通15/精英35/Boss60，冠军×1.5）。
            // 用 DamageCalc 同参本地估算伤害，与敌人剩余 HP 比较预测 kill，避免后端入账分支死代码。
            if (!"quick".equals(mode) && enemy != null) {
                int enemyHp = enemy.optInt("hp", 0);
                String wstate = quiz.optBoolean("graduated", false) ? "MASTERED"
                        : (quiz.optBoolean("review", false) ? "FAMILIAR" : "NEW");
                int runMiss = runState != null ? runState.runMisses(quiz.optString("word")) : 0;
                DamageCalc.Result est = DamageCalc.calc(quiz.optString("word"), wstate, combo,
                        quiz.optString("qtype"), quiz.optDouble("timeLimit", 6), remainSec,
                        runMiss, crit, critMultSend, bonusDamageMult);
                body.put("kill", est.total >= enemyHp);
                body.put("kill_kind", enemy.optBoolean("boss", false) ? "boss"
                        : (enemy.optBoolean("elite", false) ? "elite" : "normal"));
                body.put("champion", enemy.optBoolean("champion", false));
            }
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
            // R2-B：以权威 souls 余额刷新展示（后端 answer 响应携带）
            if (res.has("souls")) {
                act.souls = res.optInt("souls", act.souls);
                updateHud();
            }

            if (ok) {
                combo++;
                questionsInRun++;
                quickDone++;
                correctInRun++;
                maxCombo = Math.max(maxCombo, combo);
                wrongStreak = 0;
                comboNum.setText(String.valueOf(combo));
                refreshComboTag();
                // R1-5/6：连击副文案走统一档位助手（5/8 心流档 + 10/20/30 里程碑档）
                comboSub.setText(comboSubText(combo));
                refreshComboNumStyle();   // UI-iteration-05：三档字号+变色（替代旧 setColor）
                updateComboDots(combo);
                // V12.5 连击里程碑：每 5 连击全屏横幅
                if (combo % 5 == 0) showComboBanner(combo);
                // V12.5 快答判定：拼写按剩余比例、选择按耗时
                String qt0 = quiz.optString("qtype");
                double tlim0 = quiz.optDouble("timeLimit", 6);
                String grade = null;
                if ("spell".equals(qt0)) {
                    double frac = tlim0 > 0 ? remainSec / tlim0 : 0;
                    if (frac >= 0.75) grade = "perfect";
                    else if (frac >= 0.45) grade = "good";
                } else {
                    double elapsed = tlim0 - remainSec;
                    if (elapsed <= GameConfig.FAST_T) grade = "perfect";
                    else if (elapsed <= GameConfig.NORMAL_T) grade = "good";
                }
                if (grade != null) showJudgePop(grade);
                // V7.0 心流：连击≥5 进入全屏青色光晕 +「心 流」横幅，每答对 +1 词魂
                if (combo >= 5) {
                    if (!flowOn) { flowOn = true; triggerFlow(); }
                    if (!"quick".equals(mode)) { act.souls += 1; updateHud(); }
                }
                // 新学词收集（结算页 chip）
                if ("NEW".equals(res.optString("state", "")) && !act.runNewWords.contains(quiz.optString("word"))) {
                    act.runNewWords.add(quiz.optString("word"));
                }
                // V7.0 成就解锁金色 toast（后端 new_ach 数组）；R3：key 经 MainActivity.achName 集中映射为中文名，不再直弹英文 key
                JSONArray na = res.optJSONArray("new_ach");
                if (na != null && na.length() > 0) {
                    StringBuilder sb = new StringBuilder("🏆 成就解锁：");
                    for (int i = 0; i < na.length(); i++) {
                        if (i > 0) sb.append("、");
                        sb.append(MainActivity.achName(na.optString(i)));
                    }
                    showAchToast(sb.toString());
                }
                refreshGoal();
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
                // V12.7.11 连击/答对反馈条：显示 0.25s 快速淡出切题（用户调短）
                showAnswerResult(true, "伤害 +" + damage + (combo > 1 ? " · 连击 ×" + combo : ""), 250);
                // V12.5 快答判定：越快越华丽（拼写按剩余比例/选择按耗时）
                showJudgeGrade(quiz.optString("qtype"), quiz.optDouble("timeLimit", 6));
                if (xpGain > 0) popXp(xpGain);
                if (newLevel > playerLevel) {
                    playerLevel = newLevel;
                    onLevelUp();
                } else playerLevel = newLevel;
                // R2-A：汲词 lifesteal——答对按造成伤害 10% 回血
                if (lifestealPct > 0 && hp < 100) {
                    int heal = (int) Math.round(damage * lifestealPct);
                    if (heal > 0) {
                        hp = Math.min(100, hp + heal);
                        updateHud();
                        showFeed("🩸 汲词：生命 +" + heal, Ui.GREEN);
                    }
                }
                if ("quick".equals(mode)) {
                    // 快刷无敌人：只振动+音效，300ms 快速下一题
                    boolean big = crit || wrongCnt > 0;
                    if (big) Ui.vibrate(getContext(), new long[]{0, 40, 30, 55});
                    else Ui.vibrate(getContext(), 20);
                    Sfx.play(getContext(), big ? Sfx.CRIT : Sfx.HIT);
                    main.postDelayed(this::nextQuiz, 300);
                } else {
                    hitEnemy(damage, crit, wrongCnt > 0);
                }
            } else {
                questionsInRun++;
                // R2-A：势如破竹 unstop——答错连击减半（向下取整）而非清零
                int prevFill = Math.max(0, Math.min(5, combo));   // R1-2：清槽前已点亮格数
                combo = comboHalved ? combo / 2 : 0;
                comboNum.setText(String.valueOf(combo));          // comboNum 同步回落
                refreshComboNumStyle();   // UI-iteration-05：答错断连击自然回落 24 金
                // R1-2：答错槽「红闪→塌缩清空」动画（≤350ms），结束后回到新连击态
                collapseComboDots(prevFill, combo);
                comboSub.setText(comboSubText(combo));
                wrongInRun++;
                flowOn = false;
                wrongStreak++;
                // V7.0 凝神：连错 2 次 → 紫色横幅 + 下一题限时 +2 秒
                if (wrongStreak >= 2) {
                    bonusTime += 2;
                    showFlowBanner("凝 神", Ui.PURPLE);
                    wrongStreak = 0;
                }
                // 记录本层错词（复习之泉素材，按出错次数累计≈顽固优先）+ 结算页错词 chip
                String w = quiz.optString("word", "");
                if (!w.isEmpty()) {
                    String pos = quiz.optString("pos", "");
                    String mean = quiz.optString("mean", "");
                    String[] prev = layerWrong.get(w);
                    int cnt = prev == null ? 1 : Integer.parseInt(prev[2]) + 1;
                    layerWrong.put(w, new String[]{pos, mean, String.valueOf(cnt)});
                    if (!act.runWrongWords.contains(w)) act.runWrongWords.add(w);
                }
                quickDone++;
                if ("quick".equals(mode)) {
                    // 快刷无敌人：不扣血，只沉重振动+错误音效
                    Ui.vibrate(getContext(), new long[]{0, 60, 50, 80, 50, 100});
                    Sfx.play(getContext(), Sfx.HURT);
                } else {
                    hitPlayer(enemy.optInt("atk", 10));
                }
                // UI-iteration-15：答错反馈条补上「生命 -X」代价，与答对「伤害 +X」对称（快刷不扣血则不显示）
                int dmgTaken = "quick".equals(mode) ? 0 : enemy.optInt("atk", 10);
                String hurtPre = dmgTaken > 0 ? "生命 -" + dmgTaken + " · " : "";
                refreshGoal();
                // V3.5 拼写答错回归：先红抖展示玩家拼写，约 0.6s 后替换为正确答案（金色）
                // 并停留约 1s 再进下一题；选项题保持原 1.1s 停留。
                boolean spellWrong = "spell".equals(quiz.optString("qtype")) && spellInput != null;
                if (spellWrong) {
                    // V12.7.7 答错反馈停留 1.5s（UI-iteration-15：补生命 -X 代价）
                    showAnswerResult(false, hurtPre + "正确答案：" + correctAnswer, 1500);
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
                    // V12.4：判断题答错补上正确释义（correct_mean），选择题照旧显示正确选项
                    String display;
                    if ("judge".equals(quiz.optString("qtype"))) {
                        String jm = res.optString("correct_mean", "");
                        display = ("yes".equals(correctAnswer) ? "对" : "错")
                                + (jm.isEmpty() ? "" : "，释义：" + jm);
                    } else {
                        display = correctAnswer;
                    }
                    // V12.7.7 答错反馈停留 1.5s（UI-iteration-15：补生命 -X 代价）
                    showAnswerResult(false, hurtPre + "正确答案：" + display, 1500);
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

    /** V11.8 打击动效升级：从点击处发射攻击弹（✦/⚔）飞向敌人卡片，命中后触发全部命中特效。
     *  若未记录到点击坐标（如自动提交/倒计时提交），则从屏幕中下部直接发射。 */
    private void hitEnemy(int damage, boolean crit, boolean revenge) {
        try {
            int left = Math.max(0, enemy.optInt("hp", 0) - damage);
            enemy.put("hp", left);
            boolean big = crit || revenge;
            flyProjectile(damage, big, crit, revenge, left);
        } catch (Throwable ignore) {
            main.postDelayed(this::nextQuiz, 350);
        }
    }

    /** 投射弹道：攻击弹从点击点加速飞向敌人中心，240ms 到达后执行命中特效。 */
    private void flyProjectile(int damage, boolean big, boolean crit, boolean revenge, int left) {
        try {
            int[] ec = new int[2];
            enemyEmoji.getLocationInWindow(ec);
            int[] bc = new int[2];
            getLocationInWindow(bc);
            float ex = ec[0] - bc[0] + enemyEmoji.getWidth() / 2f;
            float ey = ec[1] - bc[1] + enemyEmoji.getHeight() / 2f;
            // V11.8 修复：移除 V11.7 遗留的未声明 tapX/tapY 死代码（无字段无赋值无监听），
            // 投射物固定以敌人中心为起点，功能不变。
            final float sx = getWidth() / 2f, sy = getHeight() * 0.72f;
            TextView proj = Ui.text(getContext(), big ? "⚔" : "✦", 26, big ? Ui.GOLD : 0xFFFCD34D, true);
            proj.setX(sx - dp(13));
            proj.setY(sy - dp(13));
            addView(proj, new FrameLayout.LayoutParams(dp(26), dp(26)));
            proj.animate().x(ex - dp(13)).y(ey - dp(13)).setDuration(240)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> {
                        try { removeView(proj); } catch (Throwable ignore) {}
                        impactEnemy(damage, big, crit, revenge, left);
                    }).start();
        } catch (Throwable t) {
            impactEnemy(damage, big, crit, revenge, left);
        }
    }

    /** 命中特效：顿帧回弹 + 整卡震动 + 伤害飘字 + 金色粒子 + 震动音效 + 击杀/下一题。 */
    private void impactEnemy(int damage, boolean big, boolean crit, boolean revenge, int left) {
        try {
            enemyHp.setRatio((float) left / enemy.optInt("maxHp", 100));
            enemyHp.flashHit();   // R2-5：怪物受击血条白闪反馈（独立于 160ms 平滑）
            // UI-iteration-04：敌人残血（>0 且 ≤30%）血条持续闪烁，已死不闪
            enemyHp.setCritical(left > 0 && (float) left / enemy.optInt("maxHp", 100) <= 0.30f);
            hpNumTxt.setText(left + " / " + enemy.optInt("maxHp", 100));
            // UI-iteration-11：血量数字受击弹跳（与血条平滑/白闪同步，数字不再冷冰冰硬切）
            hpNumTxt.animate().cancel();
            hpNumTxt.setScaleX(1f); hpNumTxt.setScaleY(1f);
            hpNumTxt.animate().scaleX(1.15f).scaleY(1.15f).setDuration(90)
                    .withEndAction(() -> hpNumTxt.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                    .start();
            enemyEmoji.animate().scaleX(1.35f).scaleY(1.35f).setDuration(70)
                    .withEndAction(() -> enemyEmoji.animate().scaleX(1f).scaleY(1f).setDuration(210).start()).start();
            shake(enemyEmoji, 260);
            if (enemyCard != null) shake(enemyCard, 220);
            shakePlayer(true);
            // UI-iteration-20（收官）：答对出击时玩家头像前扑缩放（1→1.15→1），与受击 scale 对称，强化"挥剑攻击"发力感
            if (playerEmoji != null) {
                playerEmoji.animate().cancel();
                playerEmoji.setScaleX(1f); playerEmoji.setScaleY(1f);
                playerEmoji.animate().scaleX(1.15f).scaleY(1.15f).setDuration(80)
                        .withEndAction(() -> playerEmoji.animate().scaleX(1f).scaleY(1f).setDuration(200).start()).start();
            }
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
            Sfx.play(getContext(), big ? Sfx.CRIT : Sfx.HIT);
            enemyHp.setColor(left <= 0 ? Ui.GOLD : Ui.RED);
            refreshGoal();
            if (left <= 0) {
                onKill();
            } else {
                main.postDelayed(this::nextQuiz, 260);   // V12.7.7 答对：240ms 飞行 + 260ms ≈ 0.5s 切下一题
            }
        } catch (Throwable ignore) {
            main.postDelayed(this::nextQuiz, 260);
        }
    }
    private void hitPlayer(int atk) {
        // R2-A：错词护盾 shield——按点抵挡伤害（优先扣盾）
        if (shield > 0) {
            int absorbed = Math.min(shield, atk);
            shield -= absorbed;
            atk -= absorbed;
            if (absorbed > 0) showFeed("🛡 护盾抵挡 " + absorbed + " 点", Ui.CYAN);
        }
        hp = Math.max(0, hp - atk);
        // R2-A：不朽 immortal——致命伤以 1 HP 存活一次
        if (hp <= 0 && immortal && !immortalUsed) {
            immortalUsed = true;
            hp = 1;
            showFeed("✨ 不朽：致命伤害被抵挡，以 1 HP 存活！", Ui.GOLD);
        }
        updateHud();
        playerHp.flashHit();   // R2-5：玩家受击血条白闪反馈（独立于 160ms 平滑）
        flashRed(playerEmoji);
        // V7.0 低血量红 vignette：hp≤30 显现，否则收起
        lowHpVignette.setVisibility(hp <= 30 ? VISIBLE : GONE);
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
        Sfx.play(getContext(), Sfx.HURT);
    }

    /** V11.8 连击心流槽刷新：答对点亮；R1-1 第 1~4 格 off→on 时带轻微 scale 弹动（≤200ms）。 */
    private void updateComboDots(int combo) {
        applyComboDots(combo, true);
    }

    /** 核心点亮逻辑。pulseNew=true 时，新点亮的第 1~4 格（i<4）触发弹动；第 5 格交给心流整槽闪光。 */
    private void applyComboDots(int combo, boolean pulseNew) {
        try {
            int fill = Math.max(0, Math.min(5, combo));
            for (int i = 0; i < comboDots.size(); i++) {
                TextView d = comboDots.get(i);
                boolean on = i < fill;
                d.animate().cancel();
                d.setScaleX(1f); d.setScaleY(1f);
                d.setAlpha(on ? 1f : 0.55f);
                d.setBackground(Ui.pill(on ? 0xFF2DD4BF : 0xFF0E2238, on ? 0xFF2DD4BF : 0xFF33507A));
                boolean newlyLit = pulseNew && on && i >= dotFillApplied && i < 4;
                if (newlyLit) {
                    d.setScaleX(1.35f); d.setScaleY(1.35f);
                    d.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
                }
            }
            dotFillApplied = fill;
        } catch (Throwable ignore) {}
    }

    /** R1-2 答错清槽：已点亮格整槽红闪，被清掉的格（[toFill, fromFill)）scale+alpha 塌缩（总≤350ms），
     *  结束后一次性复位到新连击态（保留格回青、清空格回暗）。不影响后续 pauseThenNext 切题流程。 */
    private void collapseComboDots(int fromFill, int toFill) {
        try {
            if (fromFill <= 0) { applyComboDots(toFill, false); return; }
            for (int i = 0; i < comboDots.size(); i++) {
                TextView d = comboDots.get(i);
                if (i >= fromFill) continue;
                d.animate().cancel();
                d.setAlpha(1f);
                d.setScaleX(1f); d.setScaleY(1f);
                d.setBackground(Ui.pill(0x66F87171, 0xFFF87171));   // 整槽红闪
                if (i >= toFill) {   // 这格要塌掉
                    d.animate().scaleX(0.15f).scaleY(0.15f).alpha(0f)
                            .setStartDelay((long) (i * 20))
                            .setDuration(220).start();
                }
            }
            long total = (long) ((fromFill - 1) * 20) + 220 + 30;   // ≈330ms 上限内
            main.postDelayed(() -> applyComboDots(toFill, false), total);
        } catch (Throwable t) {
            applyComboDots(toFill, false);
        }
    }

    /** R1-3 满 5 心流一次性联动：整槽同闪一次 + 青色圆环 + 心流横幅 + 上行琶音（复用 EPIC，不新增枚举/音频）。 */
    private void triggerFlow() {
        try {
            showFlowBanner("心 流", Ui.CYAN);
            ringBurst(Ui.TEAL);                 // 青色圆环（#2DD4BF，与横幅一致）
            Sfx.play(getContext(), Sfx.EPIC);   // 明亮上行琶音
            // 整槽一次性脉冲闪光（不再在循环内对每格重复闪光）
            for (TextView d : comboDots) {
                d.animate().cancel();
                d.setScaleX(1.5f); d.setScaleY(1.5f);
                d.animate().scaleX(1f).scaleY(1f).setDuration(320).start();
            }
        } catch (Throwable ignore) {}
    }

    /** R1-5/6 连击副文案档位：5/8 心流档 + 10/20/30 里程碑档，心流期（≥5）恒有状态文案不留白。 */
    private String comboSubText(int combo) {
        if (combo >= 30) return "👑 ×" + combo + " · 登峰造极！";
        if (combo >= 20) return "⚡ ×" + combo + " · 势不可挡！";
        if (combo >= 10) return "🔥 ×" + combo + " · 势如破竹！";
        if (combo >= 8) return "🔥 输出高涨！";
        if (combo >= 5) return "💫 心流加成中";
        if (combo > 0) return "⚡ 再答 " + (5 - combo) + " 题进入心流";
        return "";
    }

    private void onKill() {
        killsInRun++;
        bonusDamageMult = 1.0;   // 上一轮伤害增益作用于刚击杀的敌人，到此结算重置
        Ui.vibrate(getContext(), new long[]{0, 50, 40, 70, 40, 90});   // 击杀胜利反馈
        Sfx.play(getContext(), Sfx.KILL);                              // V11.8 击杀琶音
        killFlash();   // V12.5 击杀全屏白金光闪
        // R1-7：敌人消散演出（缩放+alpha 渐隐 ~300ms）
        dissolveEnemy();
        // R1-7：击杀粒子比暴击更盛——金+青双层粒子（暴击仅单层 14 个，此处 22+14）
        try {
            int[] ec = new int[2];
            enemyEmoji.getLocationInWindow(ec);
            int[] bc = new int[2];
            getLocationInWindow(bc);
            float kx = ec[0] - bc[0] + enemyEmoji.getWidth() / 2f;
            float ky = ec[1] - bc[1] + enemyEmoji.getHeight() / 2f;
            burstParticles(kx, ky, 22, Ui.GOLD, 72);
            burstParticles(kx, ky, 14, Ui.TEAL, 55);
        } catch (Throwable ignore) {}
        // V7.0 战斗胜利词魂：普通 15 / 精英 35 / Boss 60（冠军 ×1.5）
        boolean boss = enemy != null && enemy.optBoolean("boss", false);
        boolean elite = enemy != null && enemy.optBoolean("elite", false);
        int soulGain = boss ? 60 : elite ? 35 : 15;
        if (enemy != null && enemy.optBoolean("champion", false)) soulGain = Math.round(soulGain * 1.5f);
        // R4：基础击杀词魂已由后端 kill 入账、随 answer 响应返回权威 souls（submit 已同步 act.souls），
        // 此处不再重复累加基础值，避免双计与"内存+N 被下一响应覆盖归零"。
        // 仅补前端独有、后端未建模的 soulMagnet ×1.4 差额（+40%）。
        if (soulMagnet) act.souls += Math.round(soulGain * 0.4f);
        soulNum.setText("词魂 " + act.souls);
        showFeed("击杀入账 词魂 +" + (soulMagnet ? Math.round(soulGain * 1.4f) : soulGain), Ui.GOLD);
        // 快刷持续模式（V12.2）：击杀后立刻召唤下一个敌人继续刷，不结算、不翻牌、不进层间，
        // 直到用户主动点「退出快刷」（快刷击杀不发词魂，后端 kill 入账已排除 quick）。
        if ("quick".equals(mode)) {
            // 快刷无敌人，onKill 不触发；安全返回
            return;
        }
        if ("campaign".equals(mode)) {
            // V12.0：本层末场之前 → 直接召唤下一场小怪，不开复习之泉/商店
            if (!layerFinal) {
                enc++;
                showFeed("第 " + layer + " 层 · 第 " + (enc + 1) + "/" + layerEncTotal + " 场", Ui.CYAN);
                spawnEnemy();
                main.postDelayed(this::nextQuiz, 480);
                return;
            }
            // 本层末场：第 5 层最终 Boss → 通关结算
            if (layer >= GameConfig.LAYER_COUNT) {
                finishRun(true);
                return;
            }
            // 过层：走现有层间链（精英末场翻牌 → 复习之泉 → 商店 → 下一层）
            if (shouldDrawReward()) main.postDelayed(this::openRewardDraw, 550);
            else main.postDelayed(this::openSpring, 550);
            return;
        }
        // 无尽：层间节点链：翻卡（冠军）→ 复习之泉 → 词境商店 → 下一层
        // R1-7：浮层延迟 380→550ms，让敌人消散演出可见后再盖浮层
        if (shouldDrawReward()) main.postDelayed(this::openRewardDraw, 550);
        else main.postDelayed(this::openSpring, 550);
    }

    /** R1-7 击杀消散：敌人面部缩放+alpha 渐隐（~300ms）。spawnEnemy 在下一怪入场时复位 scale/alpha。 */
    private void dissolveEnemy() {
        try {
            if (enemyEmoji == null) return;
            enemyEmoji.animate().cancel();
            enemyEmoji.animate().scaleX(0.1f).scaleY(0.1f).alpha(0f).setDuration(300).start();
        } catch (Throwable ignore) {}
    }

    /** 是否翻牌：远征刚击杀的本层末场是精英（自然覆盖第 2、4 层；最终Boss前不翻牌）；无尽冠军（每 5 层）。 */
    private boolean shouldDrawReward() {
        if ("campaign".equals(mode)) return enemy != null && enemy.optBoolean("elite", false);
        if ("endless".equals(mode)) return layer % 5 == 0;
        return false;
    }

    /** 进入下一层。 */
    private void advanceLayer() {
        layer++;
        enc = 0;   // V12.0：进入新层，本层场次归零
        if (pendingBless) {
            bonusDamageMult = 1.25;   // V7.0 临时祝福：下一层伤害 +25%
            pendingBless = false;
        }
        showFeed("⚔ 层末守门者已破！进入第 " + layer + " 层", Ui.GOLD);
        spawnEnemy();
        main.postDelayed(this::nextQuiz, 500);
    }

    /** GAP-01：打开战利品翻牌——异步调真实后端 /card/draw 抽三槽，确认后应用卡效果 → 进复习之泉。 */
    private void openRewardDraw() {
        final RewardDrawView[] holder = new RewardDrawView[1];
        new Thread(() -> {
            JSONObject res = null;
            try {
                JSONObject b = new JSONObject();
                b.put("kind", layerKind());
                b.put("layer", layer);
                b.put("mode", mode);
                b.put("pity", pity);
                b.put("epic_pity", epicPity);
                res = act.router().dispatch("/card/draw", "POST", b);
            } catch (Throwable ignore) {}
            final JSONObject drawRes = res;
            main.post(() -> {
                if (drawRes != null) {
                    pity = drawRes.optInt("pity", pity);
                    epicPity = drawRes.optInt("epicPity", epicPity);
                }
                RewardDrawView rw = new RewardDrawView(getContext(), drawRes, new RewardDrawView.OnReward() {
                    @Override public void onReward(JSONObject card) {
                        if (card != null) act.runCards++;   // GAP-05：本局选卡计数上报
                        applyCard(card);
                        overlayView = null;
                        if (holder[0] != null) removeView(holder[0]);
                        openSpring();
                    }
                    @Override public void onReroll() {
                        rerollRewardDraw(holder);   // 真实扣 25 词魂后重抽
                    }
                });
                holder[0] = rw;
                overlayView = rw;
                addView(rw, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            });
        }).start();
    }

    /** GAP-01：刷新一组——走真实 /shop/buy reroll（扣 25 词魂），成功后重抽三槽重建浮层。 */
    private void rerollRewardDraw(RewardDrawView[] holder) {
        new Thread(() -> {
            JSONObject res = null;
            try {
                JSONObject b = new JSONObject();
                b.put("item", "reroll");
                b.put("souls", act.souls);
                res = act.router().dispatch("/shop/buy", "POST", b);
            } catch (Throwable ignore) {}
            final JSONObject buyRes = res;
            final boolean bought = res != null && res.optBoolean("ok", false);
            main.post(() -> {
                if (!bought) {
                    showFeed("词魂不足，无法刷新", Ui.RED);
                    return;
                }
                if (buyRes != null) {
                    act.souls = buyRes.optInt("souls", act.souls);
                    updateHud();
                }
                // 重抽三槽
                JSONObject draw = null;
                try {
                    JSONObject b = new JSONObject();
                    b.put("kind", layerKind());
                    b.put("layer", layer);
                    b.put("mode", mode);
                    b.put("pity", pity);
                    b.put("epic_pity", epicPity);
                    draw = act.router().dispatch("/card/draw", "POST", b);
                } catch (Throwable ignore) {}
                if (draw != null) {
                    pity = draw.optInt("pity", pity);
                    epicPity = draw.optInt("epicPity", epicPity);
                }
                // 用新槽位重建翻牌内容（复用同一浮层，先移除旧视图再换源重渲染）
                if (holder[0] != null) removeView(holder[0]);
                RewardDrawView rw = new RewardDrawView(getContext(), draw, new RewardDrawView.OnReward() {
                    @Override public void onReward(JSONObject card) {
                        if (card != null) act.runCards++;
                        applyCard(card);
                        overlayView = null;
                        removeView(holder[0]);
                        openSpring();
                    }
                    @Override public void onReroll() {
                        showFeed("每组只能刷新一次", Ui.MUT);
                    }
                });
                holder[0] = rw;
                overlayView = rw;
                addView(rw, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            });
        }).start();
    }

    /** V7.0 复习之泉：每层固定节点，先回 30 HP，再把本层错词做「记得/不记得」复习，答对每词 +2 HP。 */
    private void openSpring() {
        final SpringView[] holder = new SpringView[1];
        SpringView sv = new SpringView(getContext(), layerWrong, hp, new SpringView.OnSpring() {
            @Override public void onHeal(int newHp) {
                hp = Math.max(0, Math.min(100, newHp));
                playerHp.setRatio(hp / 100f);
            }
            @Override public void onDone() {
                overlayView = null;
                if (holder[0] != null) removeView(holder[0]);
                layerWrong.clear();
                openShop();
            }
        });
        holder[0] = sv;
        overlayView = sv;
        addView(sv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** V7.0 词境商店：每层固定节点，用词魂买补给与卡牌。 */
    private void openShop() {
        final ShopView[] holder = new ShopView[1];
        ShopView sv = new ShopView(getContext(), act.souls, new ShopView.OnShop() {
            @Override public void onBuy(String kind, int price) {
                // GAP-01：真实扣词魂走后端 /shop/buy（item/potion|bless|card）
                final String item;
                final int rarity;
                switch (kind) {
                    case "heal":  item = "potion"; rarity = 0; break;
                    case "bless": item = "bless";  rarity = 0; break;
                    default:      item = "card";   rarity = price >= 150 ? 2 : price >= 85 ? 1 : 0; break;
                }
                new Thread(() -> {
                    JSONObject res = null;
                    boolean ok;
                    try {
                        JSONObject b = new JSONObject();
                        b.put("item", item);
                        if ("card".equals(item)) b.put("rarity", rarity);
                        res = act.router().dispatch("/shop/buy", "POST", b);
                        ok = res.optBoolean("ok", false);
                    } catch (Throwable t) { ok = false; }
                    final JSONObject buy = res;
                    final boolean bought = ok;
                    main.post(() -> {
                        if (!bought) {
                            showFeed("词魂不足或购买失败", Ui.RED);
                            return;
                        }
                        if (buy != null) {
                            act.souls = buy.optInt("souls", act.souls);
                            soulNum.setText("词魂 " + act.souls);
                        }
                        switch (kind) {
                            case "heal":
                                hp = Math.min(100, hp + 30);
                                playerHp.setRatio(hp / 100f);
                                showFeed("❤️ 血药：生命 +30", Ui.GREEN);
                                break;
                            case "bless":
                                pendingBless = true;
                                showFeed("🗡 祝福：下一层伤害 +25%", Ui.ORANGE);
                                break;
                            case "card":
                                act.runCards++;   // GAP-05：本局购卡也计入选卡计数
                                JSONObject c = buy != null ? buy.optJSONObject("card") : null;
                                if (c != null) applyCard(c); else applyShopCard(price);
                                break;
                        }
                    });
                }).start();
            }
            @Override public void onLeave() {
                overlayView = null;
                if (holder[0] != null) removeView(holder[0]);
                advanceLayer();
            }
        });
        holder[0] = sv;
        overlayView = sv;
        addView(sv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** GAP-01：商店买卡真实效果（普通=回血15 / 稀有=攻击增益+25% / 史诗=攻击增益+50%）。 */
    private void applyShopCard(int price) {
        if (price >= 150) {
            bonusDamageMult = 1.5;
            showFeed("🌟 史诗卡：本场伤害 ×1.5", Ui.PURPLE);
        } else if (price >= 85) {
            bonusDamageMult = 1.25;
            showFeed("🌟 稀有卡：本场伤害 ×1.25", Ui.GOLD);
        } else {
            hp = Math.min(100, hp + 15);
            playerHp.setRatio(hp / 100f);
            showFeed("🌟 普通卡：生命 +15", Ui.CYAN);
        }
    }

    /** GAP-01：应用后端词典卡（card={id,name,rarity,effect}），按 id 接线战斗效果。 */
    private void applyCard(JSONObject card) {
        if (card == null) return;
        try {
            String id = card.optString("id", "");
            String name = card.optString("name", "词典卡");
            int rarity = card.optInt("rarity", 0);
            int color = rarity >= 2 ? Ui.PURPLE : rarity == 1 ? Ui.GOLD : Ui.CYAN;
            showFeed("🌟 获得卡：" + name, color);
            switch (id) {
                // ── 暴击类（R2-A：接暴击机制）──
                case "flash":        critChance += 0.15; break;                 // 灵光乍现：15% 暴击
                case "critstorm":    critChance += 0.25; break;                 // 暴击风暴：+25% 暴击率
                case "deadly":       critMult = 3.0; critChance += 0.10; break;  // 致命一击：暴击×3 + 10% 暴击率
                // ── 连击类 ──
                case "unstop":       comboHalved = true; break;                 // 势如破竹：答错连击减半
                case "combolightning": bonusDamageMult = Math.max(bonusDamageMult, 1.15); break; // 连击闪电
                case "combomaster":  bonusDamageMult = Math.max(bonusDamageMult, 1.1); break;      // 连击大师
                // ── 护盾/吸血/词魂/不朽（R2-A：机制型卡）──
                case "shield":       shield = 6; break;                        // 错词护盾：开局 6 盾
                case "lifesteal":    lifestealPct = 0.10; break;                // 汲词：答对按伤害 10% 回血
                case "soulmagnet":   soulMagnet = true; break;                  // 词魂引力：胜利词魂 +40%
                case "immortal":     immortal = true; break;                    // 不朽：致命伤以 1 HP 存活一次
                // ── 时间类 ──
                case "warmup":      bonusTime += 2; break;                     // 热身：战斗前 3 题限时+2s
                case "timectrl":    bonusTime += 3; break;                     // 时间掌控：限时+3s
                case "timewarp":    bonusTime += 4; break;                    // 时间扭曲：限时+4s
                // ── 拼写/伤害类 ──
                case "spellboost":   bonusDamageMult = Math.max(bonusDamageMult, 1.2); break;
                case "spellmaster":  bonusDamageMult = Math.max(bonusDamageMult, 1.1);
                                     hp = Math.min(100, hp + 4); playerHp.setRatio(hp / 100f); break;
                case "spellchain":   bonusDamageMult = Math.max(bonusDamageMult, 1.2); break;
                case "retreat":      bonusDamageMult = Math.max(bonusDamageMult, 1.15); break;
                case "braver":       bonusDamageMult = Math.max(bonusDamageMult, 1.1); break;
                case "firstblood":   bonusDamageMult = Math.max(bonusDamageMult, 1.2); break;
                case "speednote":    bonusDamageMult = Math.max(bonusDamageMult, 1.1); break;
                case "buildup":      bonusDamageMult = Math.max(bonusDamageMult, 1.1); break;
                case "steady":       hp = Math.min(100, hp + 5); playerHp.setRatio(hp / 100f); break;
                case "doubleedged":  bonusDamageMult = Math.max(bonusDamageMult, 1.3); break;
                case "wordfire":     bonusDamageMult = Math.max(bonusDamageMult, 1.3); break;
                case "glasscannon":  bonusDamageMult = Math.max(bonusDamageMult, 1.5); break;
                default:
                    // 未识别 id：按稀有度给温和本局增益（不静默拍平为零效果）
                    if (rarity >= 2) bonusDamageMult = Math.max(bonusDamageMult, 1.3);
                    else if (rarity == 1) bonusDamageMult = Math.max(bonusDamageMult, 1.1);
                    else { hp = Math.min(100, hp + 5); playerHp.setRatio(hp / 100f); }
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
        // V12.7.7 答错：反馈停留 1.5s 后统一切下一题
        main.postDelayed(this::nextQuiz, 1500);
    }

    /** V7.0 返回键：战斗内浮层（翻牌/泉/商店）打开时先关浮层并推进流程，再弹「放弃本层战斗？」二次确认。 */
    public void onBack() {
        hideKeyboard();
        // GAP-10：浮层可见时返回键先关浮层，不被退出确认吞掉
        if (overlayView != null) {
            View ov = overlayView;
            overlayView = null;
            try { removeView(ov); } catch (Throwable ignore) {}
            if (ov instanceof RewardDrawView) openSpring();
            else if (ov instanceof SpringView) openShop();
            else if (ov instanceof ShopView) advanceLayer();
            return;
        }
        try {
            if ("quick".equals(mode)) {
                new android.app.AlertDialog.Builder(getContext())
                        .setTitle("退出快刷？")
                        .setMessage("本次已刷 " + quickDone + " 词；退出后返回主页，学习进度已保存，错词已记入错词本。")
                        .setNegativeButton("继续刷", null)
                        .setPositiveButton("退出", (d, w) -> exitToHome())
                        .show();
            } else {
                new android.app.AlertDialog.Builder(getContext())
                        .setTitle("放弃本层战斗？")
                        .setMessage("本局进度与词魂将不保留，错词仍会记入错词本。")
                        .setNegativeButton("继续战斗", null)
                        .setPositiveButton("放弃", (d, w) -> exitToHome())
                        .show();
            }
        } catch (Throwable ignore) {
            exitToHome();
        }
    }

    private void exitToHome() {
        // 退出战斗清掉本页全部悬挂回调（计时/自动提交/题链），防止回主页后旧回调串题
        main.removeCallbacksAndMessages(null);
        act.show(MainActivity.Screen.HOME);
    }

    @Override
    protected void onDetachedFromWindow() {
        // R3：统一清理本页 Handler 上悬挂回调（计时/自动提交/题链 + R1 连击塌缩复位 postDelayed +
        // 成就 toast 自动隐藏等），与 exitToHome 的 removeCallbacksAndMessages 并存幂等、不冲突
        try { main.removeCallbacksAndMessages(null); } catch (Throwable ignore) {}
        // R3：倒计时填充动画一并取消，防离开战斗后 fillAnim 仍回调 applyFillWidth
        try { if (fillAnim != null) { fillAnim.cancel(); fillAnim = null; } } catch (Throwable ignore) {}
        if (getBackground() instanceof AmbientBackground) ((AmbientBackground) getBackground()).stop();
        if (idleAnim != null) idleAnim.cancel();
        try { if (tts != null) { tts.stop(); tts.shutdown(); tts = null; ttsReady = false; } } catch (Throwable ignore) {}
        super.onDetachedFromWindow();
    }

    // ── 计时 ─────────────────────────────

    private double timeLimit = 6;
    private double remainSec = 0;
    private Runnable timerTick;
    private Runnable autoSubmit;   // 计时到点的空答案提交（纳入 stopTimer 统一移除，防悬挂）
    private boolean timerOn = false;
    // R2-1：计时渲染去冗余——仅在文本/颜色实际变化时 setText/setColor，避免每 100ms 无谓 relayout
    private String lastTimerText = "";
    private int lastTimerColor = 0;
    private int lastFillTargetPx = -1;   // 上一次已请求的填充像素宽，未变化则跳过动画重建
    private int lastTickSec = -1;        // V12.5 上一次滴答的整秒
    private AnimatorSet dangerPulse;     // V12.5 倒计时危机脉冲
    private boolean dangerStarted = false;                    // UI-iteration-02：标记危机是否已进入，避免每 100ms 重复切背景
    private android.graphics.drawable.Drawable timerDangerBg; // UI-iteration-02：红橙危机填充，懒建一次

    /** V12.5 倒计时危机：计时数字 0.5s 缩放脉冲（重复反向）。 */
    private void startDangerPulse() {
        try {
            if (dangerPulse != null && dangerPulse.isRunning()) return;
            ObjectAnimator sx = ObjectAnimator.ofFloat(timerTxt, "scaleX", 1f, 1.1f);
            ObjectAnimator sy = ObjectAnimator.ofFloat(timerTxt, "scaleY", 1f, 1.1f);
            sx.setRepeatCount(ObjectAnimator.INFINITE); sx.setRepeatMode(ObjectAnimator.REVERSE);
            sy.setRepeatCount(ObjectAnimator.INFINITE); sy.setRepeatMode(ObjectAnimator.REVERSE);
            // UI-iteration-02：红条同步呼吸 alpha 1.0↔0.35，与数字同 480ms 反向
            ObjectAnimator fa = ObjectAnimator.ofFloat(timerFill, "alpha", 1f, 0.35f);
            fa.setRepeatCount(ObjectAnimator.INFINITE); fa.setRepeatMode(ObjectAnimator.REVERSE);
            dangerPulse = new AnimatorSet();
            dangerPulse.playTogether(sx, sy, fa);
            dangerPulse.setDuration(480);
            dangerPulse.start();
        } catch (Throwable ignore) {}
    }

    private void stopDangerPulse() {
        try {
            if (dangerPulse != null) { dangerPulse.cancel(); dangerPulse = null; }
            if (timerTxt != null) { timerTxt.setScaleX(1f); timerTxt.setScaleY(1f); }
            if (timerFill != null) timerFill.setAlpha(1f);   // UI-iteration-02：复位红条呼吸透明度
        } catch (Throwable ignore) {}
    }

    /** R2-1：timerTxt 仅在文本或颜色真正变化时才写 TextView（避免每 100ms 触发 relayout）。 */
    private void setTimerText(String s, int color) {
        if (!s.equals(lastTimerText)) {
            lastTimerText = s;
            timerTxt.setText(s);
        }
        if (color != lastTimerColor) {
            lastTimerColor = color;
            timerTxt.setTextColor(color);
        }
    }

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
        lastTimerText = "";          // R2-1：切题后强制首帧写入
        lastTimerColor = 0;
        lastFillTargetPx = -1;       // R2-1：新题重置填充目标，确保回满
        lastTickSec = -1;            // V12.5 滴答重置
        stopDangerPulse();
        dangerStarted = false;   // UI-iteration-02：新题退出危机，还原松弛金橙
        if (timerFill != null) timerFill.setBackground(Ui.gradient(999, Ui.GOLD, Ui.ORANGE, 0));
        setTimerText(secText(remainSec), Ui.GOLD);   // UI-iteration-08：常态金（危机红由下方 danger 分支覆盖）
        setTimerFill(1f);
        timerTick = new Runnable() {
            @Override
            public void run() {
                if (!timerOn) return;
                remainSec -= 0.1;
                if (remainSec <= 0) {
                    remainSec = 0;
                    setTimerText("0.0 秒", Ui.RED);
                    setTimerFill(0f);
                    // 计时结束自动提交（空答案=答错）；与玩家手动提交互斥：
                    // submit() 首句 answering 守卫 + stopTimer() 会移除本回调，防双提交/连跳两题
                    autoSubmit = () -> submit("");
                    main.postDelayed(autoSubmit, 60);
                    return;
                }
                float pct = (float) Math.max(0, remainSec / timeLimit);
                boolean danger = remainSec <= 3;   // V12.5 最后 3 秒危机
                if (danger) {
                    if (!dangerStarted) {   // UI-iteration-02：首次进入危机，整条填充切红橙
                        dangerStarted = true;
                        if (timerDangerBg == null) timerDangerBg = Ui.gradient(999, Ui.RED, Ui.ORANGE, 0);
                        timerFill.setBackground(timerDangerBg);
                    }
                    startDangerPulse();
                    int cs = (int) Math.ceil(remainSec);
                    if (cs != lastTickSec) { lastTickSec = cs; Sfx.play(getContext(), Sfx.TICK); }
                }
                int color = danger ? Ui.RED : Ui.GOLD;   // UI-iteration-08：常态金，仅危机转红（i02）
                setTimerText(secText(remainSec), color);   // R2-1：仅变化时写
                setTimerFill(pct);
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
            if (target == lastFillTargetPx) return;          // R2-1：目标像素宽未变化，跳过动画重建/分配
            lastFillTargetPx = target;
            int from = Math.max(dp(2), timerFill.getWidth());
            if (from == target) { applyFillWidth(target); return; }
            if (fillAnim != null) fillAnim.cancel();
            fillAnim = ValueAnimator.ofInt(from, target);
            fillAnim.setDuration(90);
            fillAnim.setInterpolator(new LinearInterpolator());
            fillAnim.addUpdateListener(a -> applyFillWidth((int) a.getAnimatedValue()));
            fillAnim.start();
        } catch (Throwable ignore) {}
    }

    /** R2-1：复用 timerFill 现有 LayoutParams，仅改 width，避免动画每帧 new LayoutParams。 */
    private void applyFillWidth(int w) {
        try {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) timerFill.getLayoutParams();
            if (lp == null || lp.width == w) return;
            lp.width = w;
            timerFill.setLayoutParams(lp);
        } catch (Throwable ignore) {}
    }

    private void stopTimer() {
        timerOn = false;
        if (timerTick != null) main.removeCallbacks(timerTick);
        if (autoSubmit != null) main.removeCallbacks(autoSubmit);
        if (lastTimerColor != Ui.MUT) {   // R2-1：同步缓存，避免下次重复写
            lastTimerColor = Ui.MUT;
            timerTxt.setTextColor(Ui.MUT);
        }
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

    /** V7.0 升级：全屏金色放射演出「LEVEL UP · 词灵使 Lv.X」+ 粒子。
     *  R2-4：总展示时长由 1300+400≈1700ms 压到 600+260=860ms（≤900ms），
     *  字卡仍清晰弹出，但不再长时间遮挡新题；新题在字卡淡出后立即可读可答。 */
    private void onLevelUp() {
        try {
            TextView lv = new TextView(getContext());
            lv.setText("LEVEL UP · 词灵使 Lv." + playerLevel);
            lv.setTextSize(22);
            lv.setTypeface(Typeface.DEFAULT_BOLD);
            lv.setTextColor(Ui.GOLD);
            lv.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            bg.setGradientRadius(dp(180));
            bg.setColors(new int[]{0x66FBBF24, 0x00000000});
            lv.setBackground(bg);
            int w = Math.min(getWidth(), dp(320));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, dp(110), Gravity.CENTER);
            addView(lv, lp);
            lv.setAlpha(0f);
            lv.setScaleX(0.6f);
            lv.setScaleY(0.6f);
            lv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).start();
            burstParticles(getWidth() / 2f, getHeight() / 2f, 26, 0xFFFBBF24, 100);
            Ui.vibrate(getContext(), new long[]{0, 40, 30, 60, 30, 80, 40, 100});
            Sfx.play(getContext(), Sfx.LEVEL);   // V11.8 升级琶音
            main.postDelayed(() -> {
                lv.animate().alpha(0f).setDuration(260)
                        .withEndAction(() -> { try { removeView(lv); } catch (Throwable ignore) {} }).start();
            }, 600);
        } catch (Throwable ignore) {}
    }

    /** V7.0 心流/凝神横幅：顶部居中短闪现（青色=心流，紫色=凝神）。 */
    /** V12.5 快答判定弹出：PERFECT 金 / GOOD 蓝，放大上浮 800ms。 */
    private void showJudgePop(String grade) {
        try {
            boolean perfect = "perfect".equals(grade);
            int color = perfect ? Ui.GOLD : Ui.CYAN;
            TextView t = Ui.text(getContext(), perfect ? "PERFECT" : "GOOD", 28, color, true);
            t.setShadowLayer(16, 0, 0, color);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
            lp.gravity = Gravity.CENTER;
            lp.topMargin = -dp(70);
            addView(t, lp);
            t.setScaleX(0.5f); t.setScaleY(0.5f); t.setAlpha(0f);
            t.setTranslationY(dp(20));
            t.animate().scaleX(1.2f).scaleY(1.2f).alpha(1f).translationY(-dp(10)).setDuration(200)
                    .withEndAction(() -> t.animate().scaleX(1f).scaleY(1f).translationY(-dp(55)).alpha(0f)
                            .setDuration(500).start()).start();
            main.postDelayed(() -> { try { removeView(t); } catch (Throwable ignore) {} }, 800);
        } catch (Throwable ignore) {}
    }

    /** V12.5 击杀全屏闪光：白金径向光闪 0.5s。 */
    private void killFlash() {
        try {
            View f = new View(getContext());
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            g.setGradientRadius(Math.max(getWidth(), getHeight()) * 0.7f);
            g.setColors(new int[]{0x88FFFFFF, 0x33FBBF24, 0x00000000});
            f.setBackground(g);
            addView(f, new FrameLayout.LayoutParams(-1, -1));
            f.setAlpha(0f);
            f.animate().alpha(1f).setDuration(80)
                    .withEndAction(() -> f.animate().alpha(0f).setDuration(420)
                            .withEndAction(() -> { try { removeView(f); } catch (Throwable ignore) {} }).start()).start();
        } catch (Throwable ignore) {}
    }

    /** V12.5 战斗入场横幅：1.2s 全屏层名+怪物+敌人名（快刷不打断）。 */
    private void battleIntro() {
        try {
            String layerText;
            if ("endless".equals(mode)) layerText = "无尽 · 第 " + layer + " 层";
            else layerText = "第 " + layer + " 层" + (layerName.isEmpty() ? "" : " · " + layerName);
            String face = enemyEmoji == null ? "👹" : enemyEmoji.getText().toString();
            String ename = enemy == null ? "词魔" : enemy.optString("name", "词魔");

            LinearLayout box = new LinearLayout(getContext());
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setBackgroundColor(0xCC0D1828);
            TextView lt = Ui.text(getContext(), layerText, 16, Ui.CYAN, true);
            lt.setLetterSpacing(0.2f);
            TextView mo = Ui.text(getContext(), face, 46, Ui.TXT, false);
            TextView en = Ui.text(getContext(), ename, 30, Ui.TXT, true);
            en.setShadowLayer(24, 0, 0, Ui.RED);
            box.addView(lt); box.addView(mo); box.addView(en);
            addView(box, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            box.setAlpha(0f);
            box.animate().alpha(1f).setDuration(200).start();
            box.postDelayed(() -> box.animate().alpha(0f).setDuration(350)
                    .withEndAction(() -> { try { removeView(box); } catch (Throwable ignore) {} }).start(), 850);
            Sfx.play(getContext(), Sfx.FLIP);
        } catch (Throwable ignore) {}
    }

    /** V12.5 快答判定：拼写按剩余比例（≥75% PERFECT/≥45% GOOD），选择按耗时（≤3s/≤6s）。 */
    private void showJudgeGrade(String qtype, double timeLimit) {
        try {
            String grade = null;
            if ("spell".equals(qtype)) {
                double frac = timeLimit > 0 ? remainSec / timeLimit : 0;
                if (frac >= 0.75) grade = "PERFECT";
                else if (frac >= 0.45) grade = "GOOD";
            } else {
                double elapsed = timeLimit - remainSec;
                if (elapsed <= GameConfig.FAST_T) grade = "PERFECT";
                else if (elapsed <= GameConfig.NORMAL_T) grade = "GOOD";
            }
            if (grade != null) judgePop(grade);
        } catch (Throwable ignore) {}
    }

    /** 快答弹字：PERFECT 金 / GOOD 蓝，从屏幕中部放大上浮淡出（800ms）。 */
    private void judgePop(String grade) {
        try {
            boolean perfect = "PERFECT".equals(grade);
            int color = perfect ? Ui.GOLD : Ui.CYAN;
            TextView t = Ui.text(getContext(), grade, perfect ? 30 : 26, color, true);
            t.setShadowLayer(16, 0, 0, color);
            t.setGravity(Gravity.CENTER);
            addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            t.setScaleX(0.5f); t.setScaleY(0.5f); t.setAlpha(0f);
            t.setTranslationY(dp(20));
            t.animate().scaleX(1.2f).scaleY(1.2f).alpha(1f).translationY(0).setDuration(220)
                    .withEndAction(() -> t.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
            t.postDelayed(() -> t.animate().alpha(0f).translationY(-dp(60)).setDuration(350)
                    .withEndAction(() -> { try { removeView(t); } catch (Throwable ignore) {} }).start(), 450);
        } catch (Throwable ignore) {}
    }

    /** V12.5 连击里程碑：每 5 连击全屏 COMBO×N 横幅 + 颜色分级（5绿/10金/15橙/20紫）。 */
    private void showComboBanner(int n) {
        try {
            int color; String sub;
            if (n >= 20) { color = Ui.PURPLE; sub = "势不可挡！"; }
            else if (n >= 15) { color = Ui.ORANGE; sub = "输出爆表！"; }
            else if (n >= 10) { color = Ui.GOLD; sub = "连击高手！"; }
            else { color = Ui.GREEN; sub = "手感火热！"; }
            LinearLayout box = new LinearLayout(getContext());
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            TextView big = Ui.text(getContext(), "COMBO ×" + n, 40, color, true);
            big.setShadowLayer(18, 0, 0, color);
            TextView sb = Ui.text(getContext(), sub, 17, color, true);
            box.addView(big); box.addView(sb);
            addView(box, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            box.setScaleX(0.3f); box.setScaleY(0.3f); box.setAlpha(0f);
            box.animate().scaleX(1.1f).scaleY(1.1f).alpha(1f).setDuration(200)
                    .withEndAction(() -> box.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
            main.postDelayed(() -> box.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> { try { removeView(box); } catch (Throwable ignore) {} }).start(), 800);
            Sfx.play(getContext(), Sfx.EPIC);
            Ui.vibrate(getContext(), new long[]{0, 40, 30, 60, 30, 80});
        } catch (Throwable ignore) {}
    }

    private void showFlowBanner(String text, int color) {
        try {
            flowBanner.setText(text);
            flowBanner.setTextColor(color);
            flowBanner.setBackground(Ui.card(999, 0x33000000, 1, color));
            flowBanner.setVisibility(VISIBLE);
            flowBanner.setAlpha(1f);
            flowBanner.setScaleX(0.7f);
            flowBanner.setScaleY(0.7f);
            flowBanner.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
            main.postDelayed(() -> flowBanner.animate().alpha(0f).setDuration(350)
                    .withEndAction(() -> flowBanner.setVisibility(GONE)).start(), 900);
        } catch (Throwable ignore) {}
    }

    /** V7.0 成就解锁金色 toast：顶部滑入短停留。 */
    private void showAchToast(String text) {
        try {
            TextView t = new TextView(getContext());
            t.setText(text);
            t.setTextSize(14);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(0xFF081326);
            t.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[]{0xFFFCD34D, 0xFFF59E0B});
            g.setCornerRadius(dp(12));
            t.setBackground(g);
            t.setPadding(dp(16), dp(11), dp(16), dp(11));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = dp(24);
            addView(t, lp);
            t.setAlpha(0f);
            t.setTranslationY(-dp(30));
            t.animate().alpha(1f).translationY(0f).setDuration(260).start();
            main.postDelayed(() -> t.animate().alpha(0f).translationY(-dp(20)).setDuration(300)
                    .withEndAction(() -> { try { removeView(t); } catch (Throwable ignore) {} }).start(), 1800);
        } catch (Throwable ignore) {}
    }

    /** 心流里程碑光环：圆环从中心扩散淡出（V11.3）。R1-4：颜色入参化，默认紫色（其余调用点保持原色）。 */
    private void ringBurst() { ringBurst(Ui.PURPLE); }

    private void ringBurst(int color) {
        try {
            View ring = new View(getContext());
            int size = dp(40);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0x00000000);
            g.setStroke(dp(3), color);
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
