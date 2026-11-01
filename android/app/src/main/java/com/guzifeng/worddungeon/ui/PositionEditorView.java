package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
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

    // ── 真实界面背景（V14.1.1）────────────────────
    // 用与真实页面相同的色板/圆角/布局结构静态绘制三个场景，帮助用户在真实观感下定位提示框。
    private static final class SceneBackground extends View {
        private String scene;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint goldTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mutTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bigTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint emoji = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint btnTxt = new Paint(Paint.ANTI_ALIAS_FLAG);

        SceneBackground(Context c, String s) {
            super(c);
            scene = s;
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Ui.dp(c, 1.5f));
            barBg.setColor(0xFF0F1B30);
            txt.setColor(Ui.TXT);
            txt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            goldTxt.setColor(Ui.GOLD);
            goldTxt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            mutTxt.setColor(Ui.MUT);
            bigTxt.setColor(Ui.TXT);
            bigTxt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            emoji.setColor(Ui.TXT);
            emoji.setTextSize(Ui.dp(c, 24));
            btnTxt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }

        void setScene(String s) { scene = s; invalidate(); }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (Positions.SCENE_REVIEW.equals(scene)) drawReview(canvas);
            else drawBattle(canvas);
        }

        // ── 战斗页（远征 / 快刷）：敌区→连击→技能→题目卡→答题区→玩家 HUD ──
        // 等比例：以「真机典型画布」为设计坐标系，整体 scale = min(W/DW, H/DH) 等比缩放并居中，
        //   布局、文字、间距与真机同比例（不再纵向压缩、横向自适应，避免失真）。
        private void drawBattle(Canvas canvas) {
            float W = getWidth(), H = getHeight();
            float px = Ui.dp(getContext(), 12);
            boolean quick = Positions.SCENE_QUICK.equals(scene);
            float DW = 36 * px;                      // 设计宽（≈ 真机 432dp 屏宽）
            float DH = quick ? 38 * px : 41 * px;    // 设计高（快刷无技能行，略矮）
            float scale = Math.min(W / DW, H / DH);
            if (scale > 1.5f) scale = 1.5f;
            if (scale < 0.2f) scale = 0.2f;          // 等比优先：宁可整体缩小，绝不裁切
            float ox = (W - DW * scale) / 2f, oy = (H - DH * scale) / 2f;
            canvas.save();
            canvas.translate(ox, oy);
            canvas.scale(scale, scale);
            float cy;

            // 1) 敌区卡（emoji + 名字 + 敌血条 + hp；微目标行）
            float eTop = px * 0.5f, eH = px * 7.6f;
            card(canvas, px, eTop, DW - px * 2, eH, Ui.CARD2, Ui.LINE);
            cy = eTop + px * 2.3f;
            canvas.drawText("👹", px + px * 0.7f, cy, emoji);
            float nameX = px + px * 3.4f;
            txt.setTextSize(Ui.dp(getContext(), 13));
            canvas.drawText(quick ? "词魔·深渊领主" : "词魇·深渊领主", nameX, cy, txt);
            // 敌血条（红，约 58%，宽度按设计画布布局避免挤压右侧 hp 文字）
            float nameW = txt.measureText(quick ? "词魔·深渊领主" : "词魇·深渊领主");
            float hpW = Ui.dp(getContext(), 58);
            float barX = nameX + nameW + Ui.dp(getContext(), 10);
            float barW = DW - barX - px * 3.2f - hpW;
            if (barW > Ui.dp(getContext(), 40)) {
                bar(canvas, barX, cy - Ui.dp(getContext(), 5), barW, Ui.dp(getContext(), 7),
                        0xFF1C2B45, 0xFFF87171, 0.58f);
            }
            goldTxt.setTextSize(Ui.dp(getContext(), 11));
            canvas.drawText("185 / 320", DW - px - hpW, cy, goldTxt);
            // 微目标行
            float goalY = eTop + eH - Ui.dp(getContext(), 6);
            mutTxt.setTextSize(Ui.dp(getContext(), 11));
            canvas.drawText(quick ? "快刷一轮" : "本场答对 5 题", px + px * 0.8f, goalY, mutTxt);
            goldTxt.setTextSize(Ui.dp(getContext(), 11));
            canvas.drawText("已达成", DW - px - Ui.dp(getContext(), 46), goalY, goldTxt);

            float y = eTop + eH + px * 1.1f;

            // 2) 连击行（连击 0 + 心流圆点）
            float comboY = y + px * 1.7f;
            txt.setTextSize(Ui.dp(getContext(), 12));
            centerText(canvas, "连击 0", DW / 2f, comboY, txt);
            for (int i = 0; i < 5; i++) {
                float dx = DW / 2f + (i - 2) * Ui.dp(getContext(), 9);
                circle(canvas, dx, comboY + Ui.dp(getContext(), 14), Ui.dp(getContext(), 3), 0xFF33507A);
            }
            y += px * 3.4f;

            // 3) 技能徽章行
            if (!quick) {
                float by = y + px * 1.0f;
                float bx = px * 0.8f;
                for (int i = 0; i < 3; i++) {
                    float bw = Ui.dp(getContext(), 64), bh = Ui.dp(getContext(), 20);
                    card(canvas, bx, by, bw, bh, Ui.BG1, Ui.LINE);
                    mutTxt.setTextSize(Ui.dp(getContext(), 10));
                    canvas.drawText(new String[]{"⚡ 词锋", "💠 贪婪", "🛡 复苏"}[i],
                            bx + Ui.dp(getContext(), 6), by + bh / 2f + Ui.dp(getContext(), 4), mutTxt);
                    bx += bw + Ui.dp(getContext(), 6);
                }
                y += px * 3.0f;
            }

            // 4) 题目卡（大字释义 + 提示 + 计时条 + 秒数）
            float qTop = y + px * 0.6f, qH = px * 7.2f;
            card(canvas, px, qTop, DW - px * 2, qH, Ui.CARD2, Ui.LINE);
            bigTxt.setTextSize(Ui.dp(getContext(), 21));
            canvas.drawText("同意，许可", DW / 2f - Ui.dp(getContext(), 42), qTop + px * 2.6f, bigTxt);
            mutTxt.setTextSize(Ui.dp(getContext(), 11));
            canvas.drawText("根据释义默写英文单词", DW / 2f - Ui.dp(getContext(), 74), qTop + px * 4.4f, mutTxt);
            // 计时条
            float tY = qTop + qH - Ui.dp(getContext(), 9);
            bar(canvas, px + Ui.dp(getContext(), 6), tY, DW - px * 2 - Ui.dp(getContext(), 12),
                    Ui.dp(getContext(), 5), 0xFF0F1B30, 0xFFF59E0B, 0.7f);
            goldTxt.setTextSize(Ui.dp(getContext(), 11));
            canvas.drawText("13 秒", DW - px - Ui.dp(getContext(), 30), qTop + px * 4.4f, goldTxt);

            y = qTop + qH + px * 1.1f;

            // 5) 答题区（拼写格 + 输入框）
            float aTop = y, aH = px * 7.0f;
            card(canvas, px, aTop, DW - px * 2, aH, Ui.CARD2, Ui.LINE);
            float cellW = Ui.dp(getContext(), 30), gap = Ui.dp(getContext(), 5);
            float rowW = 7 * cellW + 6 * gap;
            float gx = (DW - rowW) / 2f;
            float gy = aTop + Ui.dp(getContext(), 15);
            for (int i = 0; i < 7; i++) {
                card(canvas, gx + i * (cellW + gap), gy, cellW, cellW, Ui.BG1, Ui.LINE);
                if (i == 0) { // 已输入一格示例
                    txt.setTextSize(Ui.dp(getContext(), 14));
                    canvas.drawText("c", gx + i * (cellW + gap) + cellW / 2f - Ui.dp(getContext(), 4),
                            gy + cellW / 2f + Ui.dp(getContext(), 5), txt);
                }
            }
            // 输入框（示例内容小写）
            float iy = gy + cellW + Ui.dp(getContext(), 8);
            card(canvas, px + Ui.dp(getContext(), 6), iy, DW - px * 2 - Ui.dp(getContext(), 12),
                    Ui.dp(getContext(), 30), Ui.BG1, Ui.LINE);
            txt.setTextSize(Ui.dp(getContext(), 15));
            canvas.drawText("concent", px + Ui.dp(getContext(), 12), iy + Ui.dp(getContext(), 20), txt);
            // 确认按钮
            float btnW = Ui.dp(getContext(), 64);
            fillRound(canvas, DW - px - btnW - Ui.dp(getContext(), 6), iy, btnW, Ui.dp(getContext(), 30),
                    999f, 0xFFFCD34D);
            btnTxt.setTextSize(Ui.dp(getContext(), 13));
            btnTxt.setColor(0xFF081326);
            canvas.drawText("✓ 确认", DW - px - btnW - Ui.dp(getContext(), 6) + Ui.dp(getContext(), 14),
                    iy + Ui.dp(getContext(), 20), btnTxt);

            y = aTop + aH + px * 1.0f;

            // 6) 玩家 HUD（🧙 生命 + 绿血条 + 词魂）
            float pTop = y, pH = px * 3.8f;
            card(canvas, px, pTop, DW - px * 2, pH, Ui.CARD2, 0);
            emoji.setTextSize(Ui.dp(getContext(), 18));
            canvas.drawText("🧙", px + Ui.dp(getContext(), 4), pTop + pH / 2f + Ui.dp(getContext(), 6), emoji);
            txt.setTextSize(Ui.dp(getContext(), 11));
            canvas.drawText("生命 100/100", px + Ui.dp(getContext(), 38), pTop + pH / 2f + Ui.dp(getContext(), 4), txt);
            float pbX = px + Ui.dp(getContext(), 130), pbW = DW - pbX - px * 2 - Ui.dp(getContext(), 60);
            bar(canvas, pbX, pTop + pH / 2f - Ui.dp(getContext(), 3), pbW, Ui.dp(getContext(), 6),
                    0xFF1C2B45, 0xFF22C55E, 1.0f);
            goldTxt.setTextSize(Ui.dp(getContext(), 11));
            canvas.drawText("💠 词魂 0", DW - px - Ui.dp(getContext(), 56), pTop + pH / 2f + Ui.dp(getContext(), 4), goldTxt);
            canvas.restore();
        }

        // ── 错词复习页：垂直居中的词卡 + 记住了/再复习 + 进度（整体等比缩放）──
        private void drawReview(Canvas canvas) {
            float W = getWidth(), H = getHeight();
            float px = Ui.dp(getContext(), 14);
            float DW = 36 * px, DH = 44 * px;   // 设计画布：词卡 + 上下留白 + 进度
            float scale = Math.min(W / DW, H / DH);
            if (scale > 1.5f) scale = 1.5f;
            if (scale < 0.2f) scale = 0.2f;     // 等比优先：宁可整体缩小，绝不裁切
            float ox = (W - DW * scale) / 2f, oy = (H - DH * scale) / 2f;
            canvas.save();
            canvas.translate(ox, oy);
            canvas.scale(scale, scale);

            float cardW = DW - px * 2;
            float cardH = px * 9.5f;
            float cardX = px;
            float cardY = (DH - cardH) / 2f - px * 1.6f;
            card(canvas, cardX, cardY, cardW, cardH, Ui.CARD, Ui.LINE);

            float cy = cardY + px * 2.2f;
            bigTxt.setTextSize(Ui.dp(getContext(), 20));
            canvas.drawText("slam [n.]", DW / 2f - Ui.dp(getContext(), 42), cy, bigTxt);
            mutTxt.setTextSize(Ui.dp(getContext(), 14));
            canvas.drawText("砰地关上（门）", DW / 2f - Ui.dp(getContext(), 60), cy + px * 2.0f, mutTxt);
            goldTxt.setTextSize(Ui.dp(getContext(), 12));
            canvas.drawText("答错 4 次", DW / 2f - Ui.dp(getContext(), 30), cy + px * 3.6f, goldTxt);

            float btnY = cardY + cardH - px * 4.6f;
            float btnW = cardW / 2f - Ui.dp(getContext(), 4);
            float b1x = cardX, b2x = cardX + cardW / 2f + Ui.dp(getContext(), 4);
            fillRound(canvas, b1x, btnY, btnW, Ui.dp(getContext(), 32), 999f, 0xFF16A34A);
            fillRound(canvas, b2x, btnY, btnW, Ui.dp(getContext(), 32), 999f, 0xFFDC2626);
            btnTxt.setTextSize(Ui.dp(getContext(), 14));
            btnTxt.setColor(0xFFF0FDF4);
            canvas.drawText("✓ 记住了", b1x + btnW / 2f - Ui.dp(getContext(), 34), btnY + Ui.dp(getContext(), 21), btnTxt);
            btnTxt.setColor(0xFFFFF7ED);
            canvas.drawText("✗ 再复习", b2x + btnW / 2f - Ui.dp(getContext(), 34), btnY + Ui.dp(getContext(), 21), btnTxt);

            // 进度
            goldTxt.setTextSize(Ui.dp(getContext(), 13));
            canvas.drawText("1 / 274 · 记住 0 个", DW / 2f - Ui.dp(getContext(), 52),
                    cardY + cardH + px * 2.0f, goldTxt);
            canvas.restore();
        }

        private void card(Canvas canvas, float x, float y, float w, float h, int bg, int border) {
            fill.setColor(bg);
            Path p = new Path();
            float r = Ui.dp(getContext(), 12);
            p.addRoundRect(x, y, x + w, y + h, r, r, Path.Direction.CW);
            canvas.drawPath(p, fill);
            if (border != 0) {
                stroke.setColor(border);
                canvas.drawPath(p, stroke);
            }
        }

        private void fillRound(Canvas canvas, float x, float y, float w, float h, float r, int color) {
            fill.setColor(color);
            Path p = new Path();
            p.addRoundRect(x, y, x + w, y + h, r, r, Path.Direction.CW);
            canvas.drawPath(p, fill);
        }

        private void bar(Canvas canvas, float x, float y, float w, float h, int bg, int fg, float pct) {
            fill.setColor(bg);
            float r = h / 2f;
            Path p = new Path();
            p.addRoundRect(x, y, x + w, y + h, r, r, Path.Direction.CW);
            canvas.drawPath(p, fill);
            fill.setColor(fg);
            Path pf = new Path();
            pf.addRoundRect(x, y, x + w * Math.max(0.02f, Math.min(1f, pct)), y + h, r, r, Path.Direction.CW);
            canvas.drawPath(pf, fill);
        }

        private void circle(Canvas canvas, float cx, float cy, float r, int color) {
            fill.setColor(color);
            canvas.drawCircle(cx, cy, r, fill);
        }

        /** 返回文本中心 x（用于对齐）。 */
        private float centerText(Canvas canvas, String s, float cx, float baseY, Paint p) {
            float w = p.measureText(s);
            canvas.drawText(s, cx - w / 2f, baseY, p);
            return cx;
        }
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
