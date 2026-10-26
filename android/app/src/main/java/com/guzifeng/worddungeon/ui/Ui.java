package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.guzifeng.worddungeon.App;

/** UI 工具：dp 换算、深蓝系配色、卡片/按钮样式工厂 */
public final class Ui {

    // 深蓝系（与前端 :root 一致）
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

    private Ui() {}

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 圆角卡片背景 */
    public static GradientDrawable card(int radiusDp, int fill, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(App.ctx(), radiusDp));
        if (strokeDp > 0) {
            g.setStroke(dp(App.ctx(), strokeDp), strokeColor);
        }
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

    /** 占位 View */
    public static View spacer(Context c, int dpH) {
        View v = new View(c);
        v.setMinimumHeight(dp(c, dpH));
        return v;
    }
}
