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
 * 提示框位置可视化编辑器（V14.0）。
 * 切换场景（远征/快刷/错词复习）与提示框（7 类），在预览区拖动提示框，
 * 以提示框正中心为标点；也可用底部 +/- 按数值微调，横向 X、纵向 Y 均为 0..100。
 */
public class PositionEditorView extends LinearLayout {

    private final MainActivity act;

    private String scene = Positions.SCENE_EXPEDITION;
    private String anchor = Positions.ANCHOR_DMG;

    private FrameLayout preview;
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

    // ── 预览区 ───────────────────────────────────
    private View buildPreview() {
        preview = new FrameLayout(getContext());
        preview.setBackground(Ui.card(10, Ui.BG1, 1, Ui.LINE));
        preview.setPadding(0, 0, 0, 0);
        preview.addView(new GridLines(getContext()), new FrameLayout.LayoutParams(
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
