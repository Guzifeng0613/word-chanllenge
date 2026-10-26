package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.guzifeng.worddungeon.App;

/** UI 工具：dp 换算、深蓝系配色、卡片/按钮/徽章/开关/进度条样式工厂（V10.1 对齐 V7.0 前端） */
public final class Ui {

    // 深蓝系（与 V7.0 前端 :root 一致）
    public static final int BG0 = 0xFF0D1828;
    public static final int BG1 = 0xFF16263E;
    public static final int CARD = 0xFF1C3050;
    public static final int CARD2 = 0xFF22385E;
    public static final int LINE = 0xFF33507A;
    public static final int TXT = 0xFFEAF2FC;
    public static final int MUT = 0xFF9DB4D0;
    public static final int CYAN = 0xFF38BDF8;
    public static final int GOLD = 0xFFFBBF24;
    public static final int GREEN = 0xFF34D399;
    public static final int RED = 0xFFF87171;
    public static final int ORANGE = 0xFFFB923C;
    public static final int TEAL = 0xFF2DD4BF;
    public static final int PURPLE = 0xFFC084FC;
    public static final int BLUE = 0xFF3B82F6;
    public static final int PINK = 0xFFDB2777;
    public static final int BG_PILL = 0xFF0E2238;

    private Ui() {}

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 圆角卡片背景（渐变支持：start/end 非 0 时画线性渐变） */
    public static GradientDrawable card(int radiusDp, int fill, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(App.ctx(), radiusDp));
        if (strokeDp > 0) {
            g.setStroke(dp(App.ctx(), strokeDp), strokeColor);
        }
        return g;
    }

    /** 线性渐变卡片（V7.0 发射卡/横幅/选中态用） */
    public static GradientDrawable gradient(int radiusDp, int start, int end, int angleDeg) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{start, end});
        g.setCornerRadius(dp(App.ctx(), radiusDp));
        return g;
    }

    public static GradientDrawable pill(int fill, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(App.ctx(), 999));
        if (strokeColor != 0) g.setStroke(dp(App.ctx(), 1), strokeColor);
        return g;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** 按钮：胶囊/圆角 + 背景色 */
    public static TextView button(Context c, String s, int bg, int fg, boolean big) {
        TextView b = text(c, s, big ? 17 : 14, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 14), dp(c, big ? 14 : 10), dp(c, 14), dp(c, big ? 14 : 10));
        b.setBackground(pill(bg, 0));
        b.setClickable(true);
        return b;
    }

    /** 金色圆徽章（V7.0 hb-lvl / level-badge） */
    public static TextView goldBadge(Context c, String s, int sizeDp) {
        TextView t = text(c, s, sizeDp * 0.4f, 0xFF0B1424, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFCD34D, 0xFFF59E0B});
        g.setCornerRadius(dp(c, 999));
        t.setBackground(g);
        int px = dp(c, sizeDp);
        t.setLayoutParams(new LinearLayout.LayoutParams(px, px));
        return t;
    }

    /** 进度条容器（bar + 内层 i 填充），return[0]=外层容器 [1]=填充 View */
    public static View[] bar(Context c, float pct01, int fillStart, int fillEnd, int hDp) {
        LinearLayout outer = new LinearLayout(c);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setBackground(card(999, BG_PILL, 0, 0));
        int h = dp(c, hDp);
        outer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, h));
        View fill = new View(c);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{fillStart, fillEnd});
        g.setCornerRadius(dp(c, 999));
        fill.setBackground(g);
        float p = Math.max(0f, Math.min(1f, pct01));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, h, p);
        fill.setLayoutParams(fp);
        outer.addView(fill);
        return new View[]{outer, fill};
    }

    /** 开关（V7.0 switch：开=青色胶囊内钮右移） */
    public static View switchBtn(Context c, boolean on) {
        LinearLayout s = new LinearLayout(c);
        s.setOrientation(LinearLayout.HORIZONTAL);
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setPadding(dp(c, 4), dp(c, 3), dp(c, 4), dp(c, 3));
        int w = dp(c, 44), h = dp(c, 26);
        s.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        applySwitch(s, on);
        s.setClickable(true);
        return s;
    }

    public static void applySwitch(LinearLayout s, boolean on) {
        Context c = s.getContext();
        GradientDrawable g = on
                ? new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFF1D4ED8, 0xFF38BDF8})
                : pill(0xFF22385E, LINE);
        g.setCornerRadius(dp(c, 999));
        s.setBackground(g);
        s.removeAllViews();
        View knob = new View(c);
        int size = dp(c, 20);
        knob.setBackground(pill(0xFFFFFFFF, 0));
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(size, size);
        kp.leftMargin = on ? dp(c, 44 - 24) : 0;
        knob.setLayoutParams(kp);
        s.addView(knob);
    }

    /** 震动反馈（设置页「震动触感」开关联动；wav 形如 {0, 50, 40, 70}：延迟+震动+停顿+震动） */
    public static void vibrate(Context c, long ms) {
        vibrate(c, new long[]{0, ms});
    }

    public static void vibrate(Context c, long[] wav) {
        try {
            SharedPreferences sp = c.getSharedPreferences("wd_ui", Context.MODE_PRIVATE);
            if (!sp.getBoolean("vibrate", true)) return;   // V11.3 用户开关：默认开（可在设置关闭）
            Vibrator vb = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (vb == null || !vb.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                vb.vibrate(VibrationEffect.createWaveform(wav, -1));
            } else {
                long total = 0;
                for (long x : wav) total += x;
                @SuppressWarnings("deprecation")
                long v = total;
                vb.vibrate(v);
            }
        } catch (Throwable ignore) {}
    }

    /** 横分隔线 */
    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    /** 占位 View：固定宽高（垂直容器里作竖向间距，水平容器里作横向间距） */
    public static View spacer(Context c, int dpH) {
        View v = new View(c);
        int px = dp(c, dpH);
        v.setLayoutParams(new android.widget.LinearLayout.LayoutParams(px, px));
        return v;
    }

    // ── V11.3 动效工具 ─────────────────────────

    /** 按压反馈：按下缩小、抬起回弹（onTouch 返回 false，不消费事件，OnClickListener 照常触发）。 */
    public static void pressable(View v) {
        v.setClickable(true);
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(180)
                            .setInterpolator(new OvershootInterpolator(3f)).start();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    break;
            }
            return false;
        });
    }

    /** 持续呼吸脉动（开始按钮 / 主 CTA 吸引），可配缩放幅度。 */
    public static void pulseLoop(View v, float amp, long period) {
        android.animation.ObjectAnimator a = android.animation.ObjectAnimator
                .ofFloat(v, "scaleX", 1f, amp);
        a.setDuration(period);
        a.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        a.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
        a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        a.start();
        android.animation.ObjectAnimator ay = android.animation.ObjectAnimator
                .ofFloat(v, "scaleY", 1f, amp);
        ay.setDuration(period);
        ay.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        ay.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
        ay.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        ay.start();
    }

    /** 入场：从下方淡入上浮，带 overshoot，delay 逐项错开。 */
    public static void entrance(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(App.ctx(), 22));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(420)
                .setInterpolator(new OvershootInterpolator(0.8f)).start();
    }

    /** 主 CTA：持续发光脉动（scale）+ 按压变暗（alpha 反馈，不与 scale 脉动冲突）。 */
    public static void ctaGlow(View v) {
        pulseLoop(v, 1.035f, 1100);
        v.setClickable(true);
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().alpha(0.82f).setDuration(70).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().alpha(1f).setDuration(170).start();
                    break;
            }
            return false;
        });
    }
}
