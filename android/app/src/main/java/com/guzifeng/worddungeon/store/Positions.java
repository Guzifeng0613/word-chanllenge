package com.guzifeng.worddungeon.store;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 提示框位置自定义（V14.0）。
 * 以每个提示框的「正中心」为标点，用相对屏幕的百分比定位：
 * x=0..100 表示中心横向位置（左→右），y=0..100 表示中心纵向位置（上→下）。
 * 三个场景（远征 / 快刷 / 错词复习）各记一套，共 3 × 7 个提示框。
 */
public final class Positions {

    public static final String PREFS = "wd_positions";

    // ── 场景 ─────────────────────────────────────
    public static final String SCENE_EXPEDITION = "expedition";
    public static final String SCENE_QUICK = "quick";
    public static final String SCENE_REVIEW = "review";

    public static final String[] SCENES = {
            SCENE_EXPEDITION, SCENE_QUICK, SCENE_REVIEW
    };
    public static final String[] SCENE_LABELS = {
            "远征", "快刷", "错词复习"
    };

    // ── 提示框 ───────────────────────────────────
    public static final String ANCHOR_DMG = "dmg";
    public static final String ANCHOR_SUMMON = "summon";
    public static final String ANCHOR_FLOW = "flow";
    public static final String ANCHOR_COMBO_HINT = "combo_hint";
    public static final String ANCHOR_FEEDBACK = "feedback";
    public static final String ANCHOR_TOAST = "toast";
    public static final String ANCHOR_CONFIRM = "confirm";

    public static final String[] ANCHORS = {
            ANCHOR_DMG, ANCHOR_SUMMON, ANCHOR_FLOW, ANCHOR_COMBO_HINT,
            ANCHOR_FEEDBACK, ANCHOR_TOAST, ANCHOR_CONFIRM
    };
    public static final String[] ANCHOR_LABELS = {
            "伤害飘字", "召唤横幅", "心流横幅", "心流引导",
            "答题反馈", "顶部提示", "退出确认框"
    };

    private Positions() {}

    /** 由 MainActivity.mode（campaign/endless/quick）映射到场景。 */
    public static String sceneFromMode(String mode) {
        if ("quick".equals(mode)) return SCENE_QUICK;
        return SCENE_EXPEDITION;   // campaign 与 endless 共用远征布局
    }

    private static String key(String scene, String anchor, String axis) {
        return scene + "_" + anchor + "_" + axis;
    }

    // ── 默认值：对齐各提示框历史位置 ──────────────
    public static int defaultX(String anchor) {
        return 50;
    }

    public static int defaultY(String anchor) {
        switch (anchor) {
            case ANCHOR_DMG: return 20;
            case ANCHOR_SUMMON: return 34;
            case ANCHOR_FLOW: return 12;
            case ANCHOR_COMBO_HINT: return 12;
            case ANCHOR_FEEDBACK: return 34;
            case ANCHOR_TOAST: return 5;
            case ANCHOR_CONFIRM: return 50;
            default: return 50;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getX(Context ctx, String scene, String anchor) {
        return clamp(prefs(ctx).getInt(key(scene, anchor, "x"), defaultX(anchor)));
    }

    public static int getY(Context ctx, String scene, String anchor) {
        return clamp(prefs(ctx).getInt(key(scene, anchor, "y"), defaultY(anchor)));
    }

    public static void setX(Context ctx, String scene, String anchor, int x) {
        prefs(ctx).edit().putInt(key(scene, anchor, "x"), clamp(x)).apply();
    }

    public static void setY(Context ctx, String scene, String anchor, int y) {
        prefs(ctx).edit().putInt(key(scene, anchor, "y"), clamp(y)).apply();
    }

    public static void setBoth(Context ctx, String scene, String anchor, int x, int y) {
        prefs(ctx).edit()
                .putInt(key(scene, anchor, "x"), clamp(x))
                .putInt(key(scene, anchor, "y"), clamp(y))
                .apply();
    }

    /** 单个提示框恢复默认。 */
    public static void reset(Context ctx, String scene, String anchor) {
        prefs(ctx).edit()
                .putInt(key(scene, anchor, "x"), defaultX(anchor))
                .putInt(key(scene, anchor, "y"), defaultY(anchor))
                .apply();
    }

    /** 当前场景全部提示框恢复默认。 */
    public static void resetScene(Context ctx, String scene) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        for (String a : ANCHORS) {
            e.putInt(key(scene, a, "x"), defaultX(a));
            e.putInt(key(scene, a, "y"), defaultY(a));
        }
        e.apply();
    }

    private static int clamp(int v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }
}
