package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;
import com.guzifeng.worddungeon.store.ProgressStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 回顾 · 战绩看板（V12.8）：
 * 总览巨数字 + 周/月/年分段 + 折线（答题数/正确率）+ 柱状（胜绿/败蓝/金高峰）+ 环形（模式占比）+ 最近战绩。
 * 数据来自 ProgressStore 的 runs 表；图表全部 Canvas 手绘，不引第三方库。
 */
public class StatsView extends FrameLayout {

    private final MainActivity act;
    private LinearLayout body;
    private SegView seg;
    private LineChartView lineChart;
    private BarChartView barChart;
    private DonutChartView donut;
    private LinearLayout donutLegend;
    private LinearLayout runsHost;
    private int period = 0;   // 0=周 1=月 2=年

    public StatsView(Context c) {
        super(c);
        act = (MainActivity) c;
        setBackground(new AmbientBackground(28));

        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(34), dp(16), dp(20));
        sv.addView(body);
        addView(sv, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        build();
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }

    private void build() {
        // 顶部：标题 + 返回
        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(getContext(), "回顾 · 战绩看板", 20, Ui.TXT, true);
        TextView sub = Ui.text(getContext(), "REVIEW · BATTLE ARCHIVE", 10, Ui.MUT, false);
        LinearLayout tl = new LinearLayout(getContext());
        tl.setOrientation(LinearLayout.VERTICAL);
        tl.addView(title);
        tl.addView(sub);
        top.addView(tl);
        View space = new View(getContext());
        top.addView(space, new LinearLayout.LayoutParams(0, 0, 1f));
        TextView back = Ui.text(getContext(), "‹", 26, Ui.TXT, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Ui.card(999, Ui.CARD, 1, Ui.LINE));
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
        Ui.pressable(back);
        back.setOnClickListener(v -> act.show(MainActivity.Screen.SETTINGS));
        top.addView(back);
        body.addView(top);
        body.addView(Ui.spacer(getContext(), 14));

        // 总览（3 巨数字，数据载入前占位）
        LinearLayout ov = new LinearLayout(getContext());
        ov.setOrientation(LinearLayout.HORIZONTAL);
        ov.setWeightSum(3);
        for (int i = 0; i < 3; i++) {
            LinearLayout cell = new LinearLayout(getContext());
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, dp(12), 0, dp(10));
            cell.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
            TextView n = Ui.text(getContext(), "—", 22, Ui.TXT, true);
            TextView l = Ui.text(getContext(), "", 10, Ui.MUT, false);
            cell.addView(n);
            cell.addView(l);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            cp.setMargins(i == 0 ? 0 : dp(6), 0, 0, 0);
            cell.setLayoutParams(cp);
            ov.addView(cell);
            if (i == 0) { nRuns = n; lRuns = l; }
            if (i == 1) { nQ = n; lQ = l; }
            if (i == 2) { nAcc = n; lAcc = l; }
        }
        body.addView(ov);
        lRuns.setText("总闯关");
        lQ.setText("总答题");
        lAcc.setText("平均正确率");

        // 宽卡：最深层 / 最高连击 / 总词魂
        LinearLayout wide = new LinearLayout(getContext());
        wide.setOrientation(LinearLayout.VERTICAL);
        wide.setPadding(dp(14), dp(12), dp(14), dp(12));
        wide.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wp.topMargin = dp(8);
        wide.setLayoutParams(wp);
        TextView deep = Ui.text(getContext(), "最深纪录", 11, Ui.MUT, false);
        TextView deepV = Ui.text(getContext(), "—", 18, Ui.TXT, true);
        wide.addView(deep);
        wide.addView(deepV);
        LinearLayout wr = new LinearLayout(getContext());
        wr.setOrientation(LinearLayout.HORIZONTAL);
        wr.setWeightSum(2);
        TextView combo = Ui.text(getContext(), "最高连击 —", 11, Ui.MUT, false);
        TextView souls = Ui.text(getContext(), "总词魂 —", 11, Ui.MUT, false);
        wr.addView(combo, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        wr.addView(souls, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams wrp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wrp.topMargin = dp(8);
        wr.setLayoutParams(wrp);
        wide.addView(wr);
        body.addView(wide);
        deepTxt = deepV; comboTxt = combo; soulsTxt = souls;
        body.addView(Ui.spacer(getContext(), 12));

        // 周/月/年分段
        seg = new SegView(getContext());
        body.addView(seg);
        body.addView(Ui.spacer(getContext(), 12));

        // 折线图卡
        lineChart = new LineChartView(getContext());
        body.addView(card("答题趋势", "每日答题数 · 正确率", lineChart, dp(190)));

        // 柱状图卡
        barChart = new BarChartView(getContext());
        body.addView(card("闯关对比", "胜利为绿 · 失败为蓝 · 高峰为金", barChart, dp(175)));

        // 环形图卡
        LinearLayout donutRow = new LinearLayout(getContext());
        donutRow.setGravity(Gravity.CENTER_VERTICAL);
        donut = new DonutChartView(getContext());
        donut.setLayoutParams(new LinearLayout.LayoutParams(dp(120), dp(120)));
        donutRow.addView(donut);
        donutLegend = new LinearLayout(getContext());
        donutLegend.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        dlp.leftMargin = dp(16);
        donutLegend.setLayoutParams(dlp);
        donutRow.addView(donutLegend);
        body.addView(card("模式战绩", "远征 / 无尽 / 快刷 占比", donutRow, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 最近战绩
        runsHost = new LinearLayout(getContext());
        runsHost.setOrientation(LinearLayout.VERTICAL);
        body.addView(card("最近战绩", "近 8 局", runsHost, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private TextView nRuns, lRuns, nQ, lQ, nAcc, lAcc;
    private TextView deepTxt, comboTxt, soulsTxt;

    /** 包一个标题 + 内容的卡片。 */
    private LinearLayout card(String title, String hint, View content, int contentH) {
        LinearLayout c = new LinearLayout(getContext());
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(13), dp(13), dp(13), dp(12));
        c.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(12);
        c.setLayoutParams(cp);
        TextView h = Ui.text(getContext(), title, 13, Ui.TXT, true);
        c.addView(h);
        TextView ht = Ui.text(getContext(), hint, 10, Ui.MUT, false);
        c.addView(ht);
        LinearLayout.LayoutParams ctp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, contentH);
        ctp.topMargin = dp(8);
        content.setLayoutParams(ctp);
        c.addView(content);
        return c;
    }

    /** 进入页面：后台加载全部数据。 */
    public void refresh() {
        new Thread(() -> {
            try {
                ProgressStore store = ProgressStore.get(getContext());
                final ProgressStore.RunOverview ov = store.runOverview();
                final ProgressStore.RunSeries series = store.runSeries(period);
                final int[] share = store.modeShare();
                final List<ProgressStore.RunRow> recent = store.recentRuns(8);
                post(() -> render(ov, series, share, recent));
            } catch (Throwable e) {
                post(() -> {
                    TextView e2 = Ui.text(getContext(), "数据加载失败", 12, Ui.MUT, false);
                    e2.setGravity(Gravity.CENTER);
                    e2.setPadding(0, dp(24), 0, dp(24));
                });
            }
        }).start();
    }

    private void render(ProgressStore.RunOverview ov, ProgressStore.RunSeries s,
                        int[] share, List<ProgressStore.RunRow> recent) {
        nRuns.setText(String.valueOf(ov.totalRuns));
        nQ.setText(String.format(Locale.US, "%,d", ov.totalQuestions));
        int avg = ov.totalQuestions > 0 ? Math.round(ov.totalCorrect * 100f / ov.totalQuestions) : 0;
        nAcc.setText(ov.totalRuns > 0 ? avg + "%" : "—");
        deepTxt.setText("第 " + ov.deepestLayer + " 层");
        comboTxt.setText("最高连击 " + ov.maxCombo);
        soulsTxt.setText("总词魂 " + String.format(Locale.US, "%,d", ov.totalSouls));

        lineChart.setData(s);
        barChart.setData(s);
        donut.setData(share, ov.totalRuns);
        buildDonutLegend(share);
        buildRuns(recent);
    }

    private void buildDonutLegend(int[] share) {
        donutLegend.removeAllViews();
        String[] names = {"远征", "无尽", "快刷"};
        int[] colors = {Ui.CYAN, Ui.GREEN, Ui.GOLD};
        int total = share[0] + share[1] + share[2];
        for (int i = 0; i < 3; i++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(getContext());
            dot.setBackground(Ui.card(3, colors[i], 0, 0));
            dot.setLayoutParams(new LinearLayout.LayoutParams(dp(9), dp(9)));
            row.addView(dot);
            TextView nm = Ui.text(getContext(), names[i], 11, Ui.MUT, false);
            LinearLayout.LayoutParams nmp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nmp.leftMargin = dp(7);
            row.addView(nm, nmp);
            TextView v = Ui.text(getContext(), share[i] + "", 12, Ui.TXT, true);
            row.addView(v);
            int p = total > 0 ? Math.round(share[i] * 100f / total) : 0;
            TextView pp = Ui.text(getContext(), p + "%", 10, Ui.MUT, false);
            LinearLayout.LayoutParams ppp = new LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT);
            pp.setGravity(Gravity.RIGHT);
            row.addView(pp, ppp);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dp(8);
            row.setLayoutParams(rp);
            donutLegend.addView(row);
        }
    }

    private void buildRuns(List<ProgressStore.RunRow> recent) {
        runsHost.removeAllViews();
        if (recent.isEmpty()) {
            TextView e = Ui.text(getContext(), "暂无战绩，先去闯一局吧", 12, Ui.MUT, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(16), 0, dp(16));
            runsHost.addView(e);
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.US);
        for (ProgressStore.RunRow r : recent) {
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            int color = "endless".equals(r.mode) ? Ui.GREEN : "quick".equals(r.mode) ? Ui.GOLD : Ui.CYAN;
            String nm = "endless".equals(r.mode) ? "无尽" : "quick".equals(r.mode) ? "快刷" : "远征";
            TextView m = Ui.text(getContext(), nm, 12, color, true);
            m.setLayoutParams(new LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(m);

            LinearLayout mt = new LinearLayout(getContext());
            mt.setOrientation(LinearLayout.VERTICAL);
            TextView title = Ui.text(getContext(), "第 " + r.layer + " 层 · " + (r.win ? "通关" : "阵亡"), 12, Ui.TXT, false);
            int acc = r.questions > 0 ? Math.round(r.correct * 100f / r.questions) : 0;
            TextView st = Ui.text(getContext(), (r.win ? "胜利" : "阵亡") + " · " + r.questions + " 题", 10, Ui.MUT, false);
            mt.addView(title);
            mt.addView(st);
            row.addView(mt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView a = Ui.text(getContext(), acc + "%", 12, r.win ? Ui.GOLD : Ui.RED, true);
            row.addView(a);
            TextView tm = Ui.text(getContext(), sdf.format(new Date(r.ts * 1000)), 10, Ui.MUT, false);
            tm.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams tmp = new LinearLayout.LayoutParams(dp(42), LinearLayout.LayoutParams.WRAP_CONTENT);
            row.addView(tm, tmp);

            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(9);
            row.setLayoutParams(rp);
            // 分隔线
            View line = new View(getContext());
            line.setBackgroundColor(Ui.LINE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            line.setLayoutParams(lp);
            runsHost.addView(line);
            runsHost.addView(row);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 周/月/年分段控件
    // ════════════════════════════════════════════════════════════════
    private class SegView extends LinearLayout {
        private final TextView[] btns = new TextView[3];

        SegView(Context c) {
            super(c);
            setOrientation(LinearLayout.HORIZONTAL);
            setPadding(dp(4), dp(4), dp(4), dp(4));
            setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
            String[] names = {"本周", "本月", "今年"};
            for (int i = 0; i < 3; i++) {
                TextView b = Ui.text(c, names[i], 13, Ui.MUT, true);
                b.setGravity(Gravity.CENTER);
                b.setPadding(0, dp(9), 0, dp(9));
                Ui.pressable(b);
                final int idx = i;
                b.setOnClickListener(v -> {
                    if (period == idx) return;
                    period = idx;
                    applySel();
                    refresh();
                });
                btns[i] = b;
                addView(b, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }
            applySel();
        }

        void applySel() {
            for (int i = 0; i < 3; i++) {
                boolean on = i == period;
                btns[i].setBackgroundColor(on ? Ui.CARD2 : Color.TRANSPARENT);
                btns[i].setTextColor(on ? Ui.GOLD : Ui.MUT);
                btns[i].setBackground(Ui.card(8, on ? Ui.CARD2 : Color.TRANSPARENT, 0, 0));
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 折线图：答题数（蓝）+ 正确率（金）
    // ════════════════════════════════════════════════════════════════
    private static class LineChartView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private interface F { float f(float x); }
        private String[] labels = new String[0];
        private int[] q = new int[0], acc = new int[0];

        LineChartView(Context c) { super(c); }

        void setData(ProgressStore.RunSeries s) {
            labels = s.labels;
            q = s.questions;
            acc = new int[s.questions.length];
            for (int i = 0; i < acc.length; i++) {
                acc[i] = s.questions[i] > 0 ? Math.round(s.correct[i] * 100f / s.questions[i]) : 0;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            int pl = Ui.dp(getContext(), 30), pr = Ui.dp(getContext(), 30);
            int pt = Ui.dp(getContext(), 12), pb = Ui.dp(getContext(), 20);
            int iw = w - pl - pr, ih = h - pt - pb;
            p.setTypeface(Typeface.DEFAULT);

            // 网格 + 右轴正确率刻度（0/50/100）
            p.setTextSize(Ui.dp(getContext(), 9));
            for (int g = 0; g <= 2; g++) {
                int v = g * 50;
                float y = pt + ih - v / 100f * ih;
                p.setColor(0x33FFFFFF);
                p.setStrokeWidth(1);
                cv.drawLine(pl, y, w - pr, y, p);
                p.setColor(Ui.MUT);
                cv.drawText(v + "", w - pr + Ui.dp(getContext(), 3), y + Ui.dp(getContext(), 3), p);
            }
            int maxQv = 1;
            for (int x : q) maxQv = Math.max(maxQv, x);
            final int maxQ = maxQv;
            // 左轴刻度（0 / maxQ）
            p.setColor(Ui.MUT);
            cv.drawText(String.valueOf(maxQ), 2, pt + Ui.dp(getContext(), 3), p);
            cv.drawText("0", 2, pt + ih, p);

            if (q.length < 2) return;
            F xAt = i -> pl + iw * i / (q.length - 1);
            F yQ = v -> pt + ih - v / (float) maxQ * ih;
            F yA = v -> pt + ih - v / 100f * ih;

            // 答题数面积 + 折线
            Path area = new Path();
            Path qline = new Path();
            for (int i = 0; i < q.length; i++) {
                float x = xAt.f(i), y = yQ.f(q[i]);
                if (i == 0) { area.moveTo(x, y); qline.moveTo(x, y); }
                else { area.lineTo(x, y); qline.lineTo(x, y); }
            }
            area.lineTo(xAt.f(q.length - 1), pt + ih);
            area.lineTo(xAt.f(0), pt + ih);
            area.close();
            p.setColor(0x2638BDF8);
            cv.drawPath(area, p);
            p.setColor(Ui.CYAN);
            p.setStrokeWidth(Ui.dp(getContext(), 2));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            cv.drawPath(qline, p);

            // 正确率折线
            Path aline = new Path();
            for (int i = 0; i < acc.length; i++) {
                float x = xAt.f(i), y = yA.f(acc[i]);
                if (i == 0) aline.moveTo(x, y); else aline.lineTo(x, y);
            }
            p.setColor(Ui.GOLD);
            p.setStrokeWidth(Ui.dp(getContext(), 1.6f));
            cv.drawPath(aline, p);

            // 点
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < q.length; i++) {
                p.setColor(Ui.CYAN);
                cv.drawCircle(xAt.f(i), yQ.f(q[i]), Ui.dp(getContext(), 2.4f), p);
                p.setColor(Ui.GOLD);
                cv.drawCircle(xAt.f(i), yA.f(acc[i]), Ui.dp(getContext(), 2.4f), p);
            }
            // x 标签
            p.setColor(Ui.MUT);
            p.setTextSize(Ui.dp(getContext(), 9.5f));
            p.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < labels.length; i++) {
                cv.drawText(labels[i], xAt.f(i), h - Ui.dp(getContext(), 5), p);
            }
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 柱状图：总闯关（蓝底）+ 胜利段（绿），峰值金
    // ════════════════════════════════════════════════════════════════
    private static class BarChartView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String[] labels = new String[0];
        private int[] runs = new int[0], wins = new int[0];

        BarChartView(Context c) { super(c); }

        void setData(ProgressStore.RunSeries s) {
            labels = s.labels;
            runs = s.runs;
            wins = s.wins;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            int pl = Ui.dp(getContext(), 6), pr = Ui.dp(getContext(), 6);
            int pt = Ui.dp(getContext(), 16), pb = Ui.dp(getContext(), 20);
            int iw = w - pl - pr, ih = h - pt - pb;
            int n = runs.length;
            if (n == 0) return;
            int maxR = 1;
            for (int x : runs) maxR = Math.max(maxR, x);
            float slot = iw / (float) n;
            float bw = slot * 0.56f;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Ui.dp(getContext(), 9.5f));
            float r = Ui.dp(getContext(), 3);

            for (int i = 0; i < n; i++) {
                float cx = pl + slot * i + slot / 2f;
                float totalH = runs[i] / (float) maxR * ih;
                float winH = wins[i] / (float) maxR * ih;
                float top = pt + ih - totalH;
                boolean peak = runs[i] == maxR && runs[i] > 0;
                RectF bar = new RectF(cx - bw / 2, top, cx + bw / 2, pt + ih);
                if (peak) {
                    p.setColor(Ui.GOLD);
                    cv.drawRoundRect(bar, r, r, p);
                } else {
                    p.setColor(Ui.CYAN);
                    cv.drawRoundRect(bar, r, r, p);
                    if (winH > 0) {
                        RectF wseg = new RectF(cx - bw / 2, pt + ih - winH, cx + bw / 2, pt + ih);
                        p.setColor(Ui.GREEN);
                        cv.drawRoundRect(wseg, r, r, p);
                    }
                }
                // 顶部数字
                p.setColor(Ui.MUT);
                cv.drawText(String.valueOf(runs[i]), cx, top - Ui.dp(getContext(), 3), p);
                // x 标签
                cv.drawText(labels[i], cx, h - Ui.dp(getContext(), 5), p);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 环形图：模式占比
    // ════════════════════════════════════════════════════════════════
    private static class DonutChartView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int[] share = new int[3];
        private int total = 0;

        DonutChartView(Context c) { super(c); }

        void setData(int[] s, int t) {
            share = s;
            total = t;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float ring = Ui.dp(getContext(), 14);
            float rad = Math.min(w, h) / 2f - ring / 2 - Ui.dp(getContext(), 2);
            RectF oval = new RectF(cx - rad, cy - rad, cx + rad, cy + rad);

            // 底环
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(ring);
            p.setColor(0x14FFFFFF);
            cv.drawCircle(cx, cy, rad, p);

            int[] colors = {Ui.CYAN, Ui.GREEN, Ui.GOLD};
            int sum = share[0] + share[1] + share[2];
            if (sum > 0) {
                p.setStrokeCap(Paint.Cap.ROUND);
                float start = -90f;
                for (int i = 0; i < 3; i++) {
                    if (share[i] == 0) continue;
                    float sweep = share[i] / (float) sum * 360f;
                    p.setColor(colors[i]);
                    cv.drawArc(oval, start, sweep - 3f, false, p);
                    start += sweep;
                }
            }
            // 中心数字
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.TXT);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Ui.dp(getContext(), 20));
            p.setTypeface(Typeface.DEFAULT_BOLD);
            cv.drawText(String.valueOf(total), cx, cy + Ui.dp(getContext(), 6), p);
            p.setColor(Ui.MUT);
            p.setTextSize(Ui.dp(getContext(), 9));
            p.setTypeface(Typeface.DEFAULT);
            cv.drawText("总闯关", cx, cy + Ui.dp(getContext(), 18), p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }
}
