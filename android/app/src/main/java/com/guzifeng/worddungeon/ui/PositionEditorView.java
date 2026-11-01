package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;
import com.guzifeng.worddungeon.store.Positions;

import java.util.ArrayList;
import java.util.List;

/**
 * 提示框位置可视化编辑器（V14.0，V14.1.1 升级真实背景）。
 * 切换场景（远征/快刷/错词复习）与提示框（7 类），预览区显示与真实界面同视觉的静态背景，
 * 在背景上拖动提示框，以提示框正中心为标点；也可用底部 +/- 按数值微调，横向 X、纵向 Y 均为 0..100。
 */
public class PositionEditorView extends LinearLayout {

    private final MainActivity act;

    private String scene = Positions.SCENE_EXPEDITION;
    private String anchor = Positions.ANCHOR_DMG;

    private FrameLayout preview;
    private SceneBackground sceneBg;
    private AnchorCard dragCard;
    private TextView xVal, yVal;
    private final List<TextView> sceneBtns = new ArrayList<>();
    private final List<TextView> anchorBtns = new ArrayList<>();

    public PositionEditorView(Context c) {
        super(c);
        act = (MainActivity) c;
        setOrientation(VERTICAL);
        setBackgroundColor(Ui.BG0);
        setPadding(dp(12), dp(12), dp(12), dp(12));

        addView(buildTopBar());
        addView(Ui.spacer(getContext(), 8));
        addView(buildSceneRow());
        addView(Ui.spacer(getContext(), 8));
        addView(buildAnchorRow());
        addView(Ui.spacer(getContext(), 8));
        addView(buildPreview(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        addView(Ui.spacer(getContext(), 8));
        addView(buildControls());
    }

    // ── 顶部栏 ───────────────────────────────────
    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.button(getContext(), "‹ 返回", Ui.BG_PILL, Ui.CYAN, false);
        back.setOnClickListener(v -> act.show(MainActivity.Screen.SETTINGS));
        bar.addView(back);
        TextView title = Ui.text(getContext(), "提示框位置", 18, Ui.TXT, true);
        title.setPadding(dp(12), 0, 0, 0);
        bar.addView(title);
        return bar;
    }

    // ── 场景切换 ─────────────────────────────────
    private View buildSceneRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        for (int i = 0; i < Positions.SCENES.length; i++) {
            final String sc = Positions.SCENES[i];
            TextView b = Ui.button(getContext(), Positions.SCENE_LABELS[i],
                    Ui.BG_PILL, Ui.MUT, false);
            b.setOnClickListener(v -> {
                scene = sc;
                if (sceneBg != null) sceneBg.setScene(sc);
                refreshToggles();
                placeCard();
                updateVals();
            });
            sceneBtns.add(b);
            row.addView(b);
            if (i < Positions.SCENES.length - 1) row.addView(Ui.spacer(getContext(), 8));
        }
        return row;
    }

    // ── 提示框选择（横向滚动）────────────────────
    private View buildAnchorRow() {
        HorizontalScrollView sv = new HorizontalScrollView(getContext());
        sv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        for (int i = 0; i < Positions.ANCHORS.length; i++) {
            final String an = Positions.ANCHORS[i];
            TextView b = Ui.button(getContext(), Positions.ANCHOR_LABELS[i],
                    Ui.BG_PILL, Ui.MUT, false);
            b.setOnClickListener(v -> {
                anchor = an;
                refreshToggles();
                placeCard();
                updateVals();
            });
            anchorBtns.add(b);
            row.addView(b);
            if (i < Positions.ANCHORS.length - 1) row.addView(Ui.spacer(getContext(), 6));
        }
        sv.addView(row);
        return sv;
    }

    // ── 预览区（V14.1.1：真实界面背景 + 淡网格参考线 + 可拖提示框）───────
    private View buildPreview() {
        preview = new FrameLayout(getContext());
        preview.setBackground(Ui.card(10, Ui.BG0, 1, Ui.LINE));
        preview.setClipToPadding(false);
        sceneBg = new SceneBackground(getContext(), scene);
        preview.addView(sceneBg, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        GridLines grid = new GridLines(getContext());
        grid.setAlpha(0.45f);
        preview.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        dragCard = new AnchorCard(getContext());
        dragCard.setText(anchorLabel());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        preview.addView(dragCard, clp);
        enableDrag();

        preview.post(this::placeCard);
        return preview;
    }

    private String anchorLabel() {
        for (int i = 0; i < Positions.ANCHORS.length; i++) {
            if (Positions.ANCHORS[i].equals(anchor)) return Positions.ANCHOR_LABELS[i];
        }
        return "";
    }

    private void enableDrag() {
        dragCard.setOnTouchListener((View v, MotionEvent ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                int[] loc = new int[2];
                preview.getLocationInWindow(loc);
                float fx = ev.getRawX() - loc[0];
                float fy = ev.getRawY() - loc[1];
                int w = preview.getWidth(), h = preview.getHeight();
                if (w <= 0 || h <= 0) return true;
                int nx = clampPct(Math.round(fx / w * 100f));
                int ny = clampPct(Math.round(fy / h * 100f));
                Positions.setBoth(getContext(), scene, anchor, nx, ny);
                applyCardMargin(nx, ny);
                updateVals();
                return true;
            }
            return true;
        });
    }

    /** 按存储的百分比把卡片中心放到对应点。 */
    private void placeCard() {
        dragCard.setText(anchorLabel());
        int x = Positions.getX(getContext(), scene, anchor);
        int y = Positions.getY(getContext(), scene, anchor);
        applyCardMargin(x, y);
        updateVals();
    }

    private void applyCardMargin(int x, int y) {
        preview.post(() -> {
            int w = preview.getWidth(), h = preview.getHeight();
            if (w <= 0 || h <= 0) return;
            int cx = Math.round(w * x / 100f);
            int cy = Math.round(h * y / 100f);
            dragCard.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
            int cw = dragCard.getMeasuredWidth(), ch = dragCard.getMeasuredHeight();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dragCard.getLayoutParams();
            lp.leftMargin = cx - cw / 2;
            lp.topMargin = cy - ch / 2;
            dragCard.setLayoutParams(lp);
        });
    }

    // ── 底部数值控制 ─────────────────────────────
    private View buildControls() {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(VERTICAL);
        box.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
        box.setPadding(dp(12), dp(10), dp(12), dp(12));

        box.addView(buildStepper("横向 X", true));
        box.addView(Ui.spacer(getContext(), 8));
        box.addView(buildStepper("纵向 Y", false));
        box.addView(Ui.spacer(getContext(), 10));

        TextView reset = Ui.button(getContext(), "恢复本场景默认", Ui.BG_PILL, Ui.GOLD, false);
        reset.setOnClickListener(v -> {
            Positions.resetScene(getContext(), scene);
            placeCard();
            updateVals();
        });
        box.addView(reset);
        return box;
    }

    private View buildStepper(String label, boolean horizontal) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView lab = Ui.text(getContext(), label, 14, Ui.MUT, true);
        row.addView(lab);
        row.addView(stretch());

        TextView minus = Ui.button(getContext(), "−", Ui.BG_PILL, Ui.CYAN, false);
        TextView plus = Ui.button(getContext(), "+", Ui.BG_PILL, Ui.CYAN, false);
        TextView val = Ui.text(getContext(), "0", 16, Ui.GOLD, true);
        val.setGravity(Gravity.CENTER);
        val.setMinWidth(dp(48));
        if (horizontal) xVal = val; else yVal = val;

        minus.setOnClickListener(v -> step(horizontal, -1));
        plus.setOnClickListener(v -> step(horizontal, 1));

        row.addView(minus);
        row.addView(Ui.spacer(getContext(), 6));
        row.addView(val);
        row.addView(Ui.spacer(getContext(), 6));
        row.addView(plus);
        return row;
    }

    private void step(boolean horizontal, int delta) {
        int x = Positions.getX(getContext(), scene, anchor);
        int y = Positions.getY(getContext(), scene, anchor);
        if (horizontal) {
            x = clampPct(x + delta);
            Positions.setX(getContext(), scene, anchor, x);
        } else {
            y = clampPct(y + delta);
            Positions.setY(getContext(), scene, anchor, y);
        }
        applyCardMargin(x, y);
        updateVals();
    }

    private void updateVals() {
        if (xVal != null)
            xVal.setText(String.valueOf(Positions.getX(getContext(), scene, anchor)));
        if (yVal != null)
            yVal.setText(String.valueOf(Positions.getY(getContext(), scene, anchor)));
    }

    private void refreshToggles() {
        for (int i = 0; i < sceneBtns.size(); i++) {
            TextView b = sceneBtns.get(i);
            boolean on = Positions.SCENES[i].equals(scene);
            b.setBackground(Ui.card(999, on ? Ui.BG1 : Ui.BG_PILL, 1, on ? Ui.CYAN : Ui.LINE));
            b.setTextColor(on ? Ui.CYAN : Ui.MUT);
        }
        for (int i = 0; i < anchorBtns.size(); i++) {
            TextView b = anchorBtns.get(i);
            boolean on = Positions.ANCHORS[i].equals(anchor);
            b.setBackground(Ui.card(999, on ? Ui.BG1 : Ui.BG_PILL, 1, on ? Ui.GOLD : Ui.LINE));
            b.setTextColor(on ? Ui.GOLD : Ui.MUT);
        }
    }

    private View stretch() {
        View v = new View(getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private static int clampPct(int v) { return Math.max(0, Math.min(100, v)); }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshToggles();
        post(this::placeCard);
    }

    // ── 真实界面背景（V14.4.2 重写：View 树复刻 + 整体等比缩放）────────
    // 用户要求「完全一样然后缩小」：不再手绘坐标，改为用与真实页面（BattleView/WrongbookView）
    //   完全相同的 dp 值、字号、颜色、卡片结构静态搭建 View 树，固定设计画布
    //   （宽 432dp × 战斗 492 / 快刷 456 / 复习 528dp），整体等比缩放居中于预览区。
    private static final class SceneBackground extends FrameLayout {
        private String scene;
        private final LinearLayout page;   // 静态页容器（固定设计尺寸）
        private float DW, DH;               // 设计尺寸（像素）

        SceneBackground(Context c, String s) {
            super(c);
            scene = s;
            DW = Ui.dp(c, 432f);                 // 设计宽 = 36 × 12dp = 432dp
            DH = Ui.dp(c, dhDp(s));
            page = new LinearLayout(c);
            if (Positions.SCENE_REVIEW.equals(s)) buildReview(page);
            else buildBattle(page, Positions.SCENE_QUICK.equals(s));
            page.setLayoutParams(new FrameLayout.LayoutParams((int) DW, (int) DH, Gravity.CENTER));
            addView(page);
        }

        void setScene(String s) {
            if (s.equals(scene)) return;
            scene = s;
            page.removeAllViews();
            boolean quick = Positions.SCENE_QUICK.equals(s);
            DH = Ui.dp(getContext(), dhDp(s));
            if (Positions.SCENE_REVIEW.equals(s)) buildReview(page);
            else buildBattle(page, quick);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) page.getLayoutParams();
            lp.width = (int) DW; lp.height = (int) DH;
            page.setLayoutParams(lp);
            requestLayout();
        }

        private static float dhDp(String s) {
            if (Positions.SCENE_QUICK.equals(s)) return 456f;   // 快刷：38 × 12dp，无技能行
            if (Positions.SCENE_REVIEW.equals(s)) return 528f;  // 复习：44 × 12dp
            return 492f;                                        // 远征：41 × 12dp
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            float W = getWidth(), H = getHeight();
            if (W <= 0 || H <= 0) return;
            float scale = Math.min(W / DW, H / DH);
            if (scale > 1.5f) scale = 1.5f;
            if (scale < 0.2f) scale = 0.2f;    // 等比优先：宁可整体缩小，绝不裁切
            page.setPivotX(page.getWidth() / 2f);
            page.setPivotY(page.getHeight() / 2f);
            page.setScaleX(scale);
            page.setScaleY(scale);
        }

        // ── 战斗页（远征 / 快刷）：与 BattleView.build() 同结构同参数 ──
        private void buildBattle(final LinearLayout col, boolean quick) {
            Context c = getContext();
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(16), dp(12), dp(16), dp(12));

            // 顶栏（左标题，右：远征节点圆点 / 快刷退出+已刷）
            LinearLayout top = new LinearLayout(c);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(Ui.text(c, quick ? "快速刷题" : "第 1 层 · 词境", 16, Ui.CYAN, true),
                    new LinearLayout.LayoutParams(0, -2, 1f));   // 快刷标题「快速刷题」（BattleView L707）；远征显示层标题
            if (quick) {
                top.addView(Ui.text(c, "‹ 退出快刷", 14, Ui.CYAN, true));
                top.addView(Ui.text(c, "已刷 12 词", 14, Ui.MUT, false));   // 真机退出与已刷紧邻无间隔（BattleView L896-901）
            } else {
                LinearLayout dots = new LinearLayout(c);
                dots.setOrientation(LinearLayout.HORIZONTAL);
                dots.setGravity(Gravity.CENTER_VERTICAL);
                for (int i = 0; i < 5; i++) {
                    View dot = new View(c);
                    int sz = dp(9);
                    LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(sz, sz);
                    dlp.leftMargin = dp(4);
                    if (i == 0) {   // 第 1 层当前节点：GOLD + 1.5dp 金描边（BattleView L915-921）
                        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        g.setColor(Ui.GOLD);
                        g.setStroke(dp(1.5f), Ui.GOLD);
                        dot.setBackground(g);
                    } else {
                        dot.setBackground(circleFill(Ui.CARD2));
                    }
                    dots.addView(dot, dlp);
                }
                top.addView(dots);
            }
            col.addView(top);
            col.addView(spacer(6));

            // 敌人卡（光晕 + emoji + 名字 + 标签 + 警告 + 血条 + hp + 微目标）
            LinearLayout ebox = new LinearLayout(c);
            ebox.setOrientation(LinearLayout.VERTICAL);
            ebox.setGravity(Gravity.CENTER);
            ebox.setPadding(dp(12), dp(6), dp(12), dp(6));
            ebox.setBackground(Ui.card(16, Ui.CARD, 1, Ui.CYAN));   // 普通敌卡描边 CYAN（BattleView applyEnemyGlow L2942）
            FrameLayout faceWrap = new FrameLayout(c);
            View glow = new View(c);
            android.graphics.drawable.GradientDrawable glowG = new android.graphics.drawable.GradientDrawable();
            glowG.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            glowG.setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT);
            glowG.setGradientRadius(dp(49));   // 光晕半径 49dp（BattleView L2936-2941）
            glowG.setColors(new int[]{alphaColor(Ui.CYAN, 0.22f), alphaColor(Ui.CYAN, 0f)});
            glow.setBackground(glowG);
            faceWrap.addView(glow, new FrameLayout.LayoutParams(dp(98), dp(98), Gravity.CENTER));
            faceWrap.addView(Ui.text(c, "👹", 42, Ui.TXT, false),
                    new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            ebox.addView(faceWrap);
            TextView eName = Ui.text(c, "词魇·深渊领主", 18, Ui.TXT, true);
            eName.setGravity(Gravity.CENTER);
            ebox.addView(eName);
            TextView eTag = Ui.text(c, "普通战", 11, Ui.CYAN, true);
            eTag.setGravity(Gravity.CENTER);
            ebox.addView(eTag);
            TextView warn = Ui.text(c, "答错 / 超时将受到 10 点伤害", 11, Ui.MUT, false);
            warn.setGravity(Gravity.CENTER);
            ebox.addView(warn);
            ebox.addView(hpBar(6, Ui.RED, 0.58f), new LinearLayout.LayoutParams(-1, dp(6)));
            TextView hpNum = Ui.text(c, "185 / 320", 12, Ui.TXT, true);
            hpNum.setGravity(Gravity.CENTER);
            ebox.addView(hpNum);
            // 微目标行（仅远征；快刷 GONE）
            if (!quick) {
                LinearLayout goal = new LinearLayout(c);
                goal.setOrientation(LinearLayout.HORIZONTAL);
                goal.setGravity(Gravity.CENTER_VERTICAL);
                goal.setPadding(dp(10), dp(5), dp(10), dp(5));
                goal.setBackground(Ui.card(12, Ui.CARD2, 0, 0));
                goal.addView(Ui.text(c, "本场答对 5 题", 13, Ui.TXT, false),
                        new LinearLayout.LayoutParams(0, -2, 1f));
                goal.addView(Ui.text(c, "已达成", 13, Ui.GOLD, true));
                LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
                glp.topMargin = dp(6);
                ebox.addView(goal, glp);
            }
            col.addView(ebox);
            ebox.setVisibility(quick ? GONE : VISIBLE);   // 快刷隐藏整张敌卡（BattleView L702-706）
            col.addView(spacer(6));

            // 弹性上（快刷显示，答题单元垂直居中；远征 GONE 顶部排列）
            View elasticTop = new View(c);
            col.addView(elasticTop, new LinearLayout.LayoutParams(-1, 0, 1f));
            elasticTop.setVisibility(quick ? VISIBLE : GONE);

            // 连击行（连击 0 + 心流圆点）
            LinearLayout comboBoxV = new LinearLayout(c);
            comboBoxV.setOrientation(LinearLayout.VERTICAL);
            comboBoxV.setGravity(Gravity.CENTER);
            comboBoxV.setPadding(dp(10), 0, dp(10), 0);
            LinearLayout comboRow = new LinearLayout(c);
            comboRow.setOrientation(LinearLayout.HORIZONTAL);
            comboRow.setGravity(Gravity.CENTER);
            comboRow.addView(Ui.text(c, "连击", 12, Ui.MUT, true));
            TextView comboNum = Ui.text(c, "0", 24, Ui.GOLD, true);
            comboNum.setPadding(dp(8), 0, dp(8), 0);
            comboRow.addView(comboNum);
            comboBoxV.addView(comboRow);
            LinearLayout dotRow = new LinearLayout(c);
            dotRow.setOrientation(LinearLayout.HORIZONTAL);
            dotRow.setGravity(Gravity.CENTER);
            for (int i = 0; i < 5; i++) {
                View d = new View(c);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(7), dp(7));
                d.setBackground(Ui.pill(0xFF0E2238, 0xFF33507A));
                d.setAlpha(0.55f);
                if (i > 0) dlp.leftMargin = dp(5);
                dotRow.addView(d, dlp);
            }
            comboBoxV.addView(dotRow);
            col.addView(comboBoxV);
            col.addView(spacer(4));

            // 技能徽章行（仅远征；快刷无）
            if (!quick) {
                LinearLayout chips = new LinearLayout(c);
                chips.setOrientation(LinearLayout.HORIZONTAL);
                chips.setGravity(Gravity.CENTER);
                chips.setPadding(dp(4), 0, dp(4), 0);
                String[] names = {"⚡ 词锋", "💠 贪婪", "🛡 复苏"};
                int[] cols = {0xFFFCD34D, 0xFF33507A, 0xFF4ADE80};
                for (int i = 0; i < names.length; i++) {
                    TextView chip = Ui.text(c, names[i], 10.5f, 0xFFC7D6E8, true);
                    chip.setPadding(dp(8), dp(3), dp(8), dp(3));
                    chip.setBackground(Ui.pill(0xFF0E2238, cols[i]));
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
                    if (i > 0) clp.leftMargin = dp(5);
                    chips.addView(chip, clp);
                }
                col.addView(chips);
            }

            // 题目卡（释义大字 + 词性徽章 + 计时条 + 秒数）
            LinearLayout qbox = new LinearLayout(c);
            qbox.setOrientation(LinearLayout.VERTICAL);
            qbox.setPadding(dp(14), dp(6), dp(14), dp(6));
            qbox.setBackground(Ui.card(14, Ui.CARD2, 1, Ui.LINE));
            FrameLayout headRow = new FrameLayout(c);
            TextView qBody = Ui.text(c, "集中；专心", 19, Ui.TXT, true);   // 拼写题释义 19sp（BattleView L1183-1184）
            qBody.setGravity(Gravity.CENTER);
            headRow.addView(qBody, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
            TextView posTag = Ui.text(c, "v", 12, Ui.ORANGE, true);   // 裸词性 + ORANGE + pill 背景（BattleView L1050-1062）
            posTag.setSingleLine(true);
            posTag.setPadding(dp(7), dp(2), dp(7), dp(2));
            posTag.setBackground(Ui.pill(Ui.BG_PILL, Ui.ORANGE));
            headRow.addView(posTag, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT));
            qbox.addView(headRow);
            LinearLayout timerTrack = new LinearLayout(c);
            timerTrack.setOrientation(LinearLayout.HORIZONTAL);
            timerTrack.setBackground(Ui.card(999, 0xFF0F1B30, 0, 0));
            View timerFill = new View(c);
            timerFill.setBackground(Ui.gradient(999, Ui.GOLD, Ui.ORANGE, 0));
            timerTrack.addView(timerFill, new LinearLayout.LayoutParams(-1, dp(6)));
            qbox.addView(timerTrack, new LinearLayout.LayoutParams(-1, dp(6)));
            TextView timerTxt = Ui.text(c, "13 秒", 13, Ui.GOLD, true);
            timerTxt.setGravity(Gravity.CENTER);
            qbox.addView(timerTxt);
            col.addView(qbox);
            col.addView(spacer(6));

            // 答题区（拼写：字母格 + 输入行，与 renderQuiz spell 分支同参数）
            FrameLayout answerArea = new FrameLayout(c);
            answerArea.setBackground(Ui.card(14, Ui.CARD2, 1, Ui.LINE));
            LinearLayout av = new LinearLayout(c);
            av.setOrientation(LinearLayout.VERTICAL);
            av.setPadding(dp(10), dp(8), dp(10), dp(8));   // 答题区容器 padding 10/8/10/8（BattleView L1095）
            LinearLayout tileRow = new LinearLayout(c);
            tileRow.setOrientation(LinearLayout.HORIZONTAL);
            tileRow.setGravity(Gravity.CENTER);
            for (int i = 0; i < 7; i++) {          // concent = 7 字母（len≤8 → 32dp 格、4dp 距、16sp）
                TextView t = Ui.text(c, "", 16, Ui.TXT, true);
                t.setGravity(Gravity.CENTER);
                t.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
                t.setBackground(Ui.card(8, Ui.BG0, 1, Ui.LINE));
                tileRow.addView(t);
                if (i < 6) tileRow.addView(spacer(4));
            }
            av.addView(tileRow);
            av.addView(spacer(6));
            LinearLayout spellRow = new LinearLayout(c);
            spellRow.setOrientation(LinearLayout.HORIZONTAL);
            spellRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView input = Ui.text(c, "concent", 18, Ui.TXT, false);
            input.setBackground(Ui.card(10, Ui.BG0, 1, Ui.LINE));
            input.setPadding(dp(10), dp(8), dp(10), dp(8));
            spellRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
            spellRow.addView(spacer(6));
            TextView confirm = Ui.text(c, "✓ 确认", 14, 0xFF081326, true);
            confirm.setGravity(Gravity.CENTER);
            confirm.setPadding(dp(12), dp(10), dp(12), dp(10));
            confirm.setBackground(Ui.gradient(999, 0xFFFCD34D, 0xFFF59E0B, 0));
            spellRow.addView(confirm);
            av.addView(spellRow);
            answerArea.addView(av, new FrameLayout.LayoutParams(-1, -2));
            col.addView(answerArea, new LinearLayout.LayoutParams(-1, -2));
            col.addView(spacer(6));

            // 弹性下（快刷显示）
            View elasticBottom = new View(c);
            col.addView(elasticBottom, new LinearLayout.LayoutParams(-1, 0, 1f));
            elasticBottom.setVisibility(quick ? VISIBLE : GONE);

            // 玩家 HUD（🧙 生命 + 绿血条 + 词魂）
            LinearLayout pbox = new LinearLayout(c);
            pbox.setOrientation(LinearLayout.HORIZONTAL);
            pbox.setGravity(Gravity.CENTER_VERTICAL);
            pbox.setPadding(dp(10), dp(4), dp(10), dp(4));
            pbox.setBackground(Ui.card(12, Ui.CARD2, 0, 0));
            pbox.addView(Ui.text(c, "🧙", 26, Ui.TXT, false));
            pbox.addView(Ui.spacer(c, 6));
            pbox.addView(Ui.text(c, "生命 100/100", 13, Ui.TXT, true));
            pbox.addView(Ui.spacer(c, 8));
            pbox.addView(hpBar(10, Ui.GREEN, 1.0f), new LinearLayout.LayoutParams(0, dp(10), 1f));
            pbox.addView(Ui.spacer(c, 8));
            TextView soul = Ui.text(c, "💠 词魂 0", 14, Ui.GOLD, true);
            soul.setPadding(dp(8), dp(4), dp(8), dp(4));
            soul.setBackground(Ui.card(999, 0x33FCD34D, 1, 0x66FCD34D));
            pbox.addView(soul);
            col.addView(pbox);
            pbox.setVisibility(quick ? GONE : VISIBLE);   // 快刷隐藏玩家 HUD（BattleView L702-706）
        }

        // ── 错词复习页：与 WrongbookView.renderReview 同结构（弹性上下 + 词卡 + 进度）──
        private void buildReview(final LinearLayout body) {
            Context c = getContext();
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(16), dp(14), dp(16), dp(20));   // 页级 padding 与真机一致（WrongbookView L39）
            View et = new View(c);
            body.addView(et, new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            card.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
            card.addView(Ui.text(c, "slam  [n.]", 20, Ui.TXT, true));
            TextView mv = Ui.text(c, "砰地关上（门）", 15, Ui.MUT, false);
            mv.setPadding(0, dp(6), 0, 0);
            card.addView(mv);
            TextView info = Ui.text(c, "答错 4 次", 12, Ui.ORANGE, true);
            info.setPadding(0, dp(8), 0, dp(12));
            card.addView(info);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(Ui.button(c, "✓ 记住了", Ui.GREEN, Ui.BG0, false),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(spacer(8));
            row.addView(Ui.button(c, "✗ 再复习", Ui.RED, Ui.BG0, false),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(row);
            body.addView(card);
            TextView nav = Ui.text(c, "1 / 274 · 记住 0 个", 13, Ui.CYAN, false);
            nav.setGravity(Gravity.CENTER);
            nav.setPadding(0, dp(10), 0, 0);
            body.addView(nav);
            View eb = new View(c);
            body.addView(eb, new LinearLayout.LayoutParams(-1, 0, 1f));
        }

        // ── 工具 ──
        private View hpBar(int h, int color, float pct) {
            Context c = getContext();
            FrameLayout f = new FrameLayout(c);
            f.setBackground(Ui.card(999, Ui.CARD2, 0, 0));   // 深槽
            View fill = new View(c);
            fill.setBackground(Ui.card(999, color, 0, 0));
            fill.setPivotX(0f);
            fill.setScaleX(Math.max(0.02f, Math.min(1f, pct)));
            f.addView(fill, new FrameLayout.LayoutParams(-1, -1));
            return f;
        }

        private static android.graphics.drawable.GradientDrawable circleFill(int color) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(color);
            return g;
        }

        /** 与 BattleView.alphaColor 同逻辑：给色值叠加 alpha（0..1）。 */
        private static int alphaColor(int color, float a) {
            int alpha = Math.round(255 * Math.max(0f, Math.min(1f, a)));
            return (alpha << 24) | (color & 0x00FFFFFF);
        }

        private View spacer(int h) { return Ui.spacer(getContext(), h); }
        private int dp(float v) { return Ui.dp(getContext(), v); }
    }

    // ── 网格参考线 ───────────────────────────────
    private static final class GridLines extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

        GridLines(Context c) {
            super(c);
            line.setColor(0x55FFFFFF);
            line.setStrokeWidth(2f);
            line.setPathEffect(new DashPathEffect(new float[]{10f, 12f}, 0f));
            text.setColor(0x66FFFFFF);
            text.setTextSize(Ui.dp(c, 11));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            int[] pcts = {25, 50, 75};
            for (int p : pcts) {
                float x = w * p / 100f;
                canvas.drawLine(x, 0, x, h, line);
                float y = h * p / 100f;
                canvas.drawLine(0, y, w, y, line);
            }
            canvas.drawText("0", 6, h - 8, text);
            canvas.drawText("100", w - 46, h - 8, text);
            canvas.drawText("0", 6, 18, text);
        }
    }

    // ── 可拖动提示框卡片（中心十字标点）──────────
    private static final class AnchorCard extends TextView {
        private final Paint cross = new Paint(Paint.ANTI_ALIAS_FLAG);

        AnchorCard(Context c) {
            super(c);
            setTextSize(14);
            setGravity(Gravity.CENTER);
            setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14));
            setBackground(Ui.card(10, Ui.CARD2, 2, Ui.GOLD));
            setTextColor(Ui.TXT);
            cross.setColor(Ui.GOLD);
            cross.setStrokeWidth(3f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Ui.dp(getContext(), 5);
            canvas.drawLine(cx - r, cy, cx + r, cy, cross);
            canvas.drawLine(cx, cy - r, cx, cy + r, cross);
        }
    }
}
