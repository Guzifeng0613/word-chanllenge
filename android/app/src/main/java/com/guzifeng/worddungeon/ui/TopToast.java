package com.guzifeng.worddungeon.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * V12.5 顶部 Toast 系统：固定在屏幕顶部、不遮挡内容；
 * 支持 success / error / info 类型，自动排队、相同内容合并，逐个淡出。
 * 零权限，直接叠加在 Activity 的 content 根层。
 */
public final class TopToast {

    private static final int TOP_DP = 12;
    private static final int HOLD_MS = 2200;

    private static LinearLayout bar;
    private static final List<Item> queue = new ArrayList<>();
    private static boolean active;

    private TopToast() {}

    private static final class Item {
        final String msg, type;
        Item(String msg, String type) { this.msg = msg; this.type = type; }
    }

    public static void show(Activity act, String msg, String type) {
        if (act == null || msg == null) return;
        try {
            ensureBar(act);
            synchronized (queue) {
                for (Item it : queue) { if (it.msg.equals(msg)) return; }  // 合并
                queue.add(new Item(msg, type == null ? "info" : type));
                if (!active) next();
            }
        } catch (Throwable ignore) {}
    }

    public static void success(Activity act, String msg) { show(act, msg, "success"); }
    public static void error(Activity act, String msg) { show(act, msg, "error"); }
    public static void info(Activity act, String msg) { show(act, msg, "info"); }

    private static void ensureBar(Activity act) {
        if (bar != null && bar.getParent() != null) return;
        ViewGroup root = act.findViewById(android.R.id.content);
        if (root == null) return;
        bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(act, TOP_DP);
        lp.leftMargin = dp(act, 14);
        lp.rightMargin = dp(act, 14);
        bar.setLayoutParams(lp);
        bar.setClickable(false);
        bar.setFocusable(false);
        root.addView(bar);
    }

    private static void next() {
        if (bar == null) { active = false; return; }
        final Item it;
        synchronized (queue) {
            if (queue.isEmpty()) { active = false; return; }
            active = true;
            it = queue.remove(0);
        }
        final Activity act = (Activity) bar.getContext();
        final TextView t = new TextView(act);
        t.setText(it.msg);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(2, 1.15f);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        int pad = dp(act, 14);
        t.setPadding(pad, dp(act, 9), pad, dp(act, 9));
        t.setBackground(Ui.card(11, bg(it.type), 1, border(it.type)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        t.setLayoutParams(lp);
        t.setTranslationY(-dp(act, 14));
        t.setScaleX(0.94f);
        t.setAlpha(0f);
        t.animate().translationY(0).scaleX(1f).alpha(1f).setDuration(280).start();
        bar.addView(t);
        t.postDelayed(() -> {
            t.animate().alpha(0f).translationY(-dp(act, 10)).setDuration(300)
                    .withEndAction(() -> {
                        try { bar.removeView(t); } catch (Throwable ignore) {}
                        next();
                    }).start();
        }, HOLD_MS);
    }

    private static int bg(String type) {
        switch (type) {
            case "success": return 0xFF0F766E;
            case "error":   return 0xFFB91C1C;
            default:        return 0xFF1D3255;
        }
    }

    private static int border(String type) {
        switch (type) {
            case "success": return 0xFF14B8A6;
            case "error":   return 0xFFEF4444;
            default:        return 0xFF3B5D8F;
        }
    }

    private static int dp(Activity act, int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f);
    }
}
