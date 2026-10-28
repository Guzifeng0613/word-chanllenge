package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

import java.util.Random;

/**
 * V13.0 彩蛋「圣者觉醒」全屏动画：紫金圣辉脉冲 + 扩散环 + 上升光粒 + 标题，约 2s，播完自移除。
 * 纯 Canvas 绘制，不引第三方库；只在称号/火花双满首次触发时由结算页调用。
 */
public class SaintAwakeningView extends View {
    private static final int PURPLE = 0xFFA78BFA;
    private static final int GOLD = 0xFFFBBF24;
    private static final int TXT_C = 0xFFEAF2FC;

    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();

    private static final int N = 42;
    private float[] ppx, ppy, psp, psz;
    private ValueAnimator anim;
    private float frac;
    private boolean done = false;

    public SaintAwakeningView(Context c) {
        super(c);
        setClickable(true);
        title.setTextAlign(Paint.Align.CENTER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(Ui.dp(c, 44));
        sub.setTextAlign(Paint.Align.CENTER);
        sub.setTextSize(Ui.dp(c, 13));
        ring.setStyle(Paint.Style.STROKE);
    }

    /** 挂到根 FrameLayout 并开始播放。 */
    public void play(FrameLayout host) {
        host.addView(this, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(2000);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addUpdateListener(a -> {
            frac = (float) a.getAnimatedValue();
            invalidate();
            if (frac >= 1f && !done) {
                done = true;
                finish();
            }
        });
        anim.start();
    }

    private void initParticles() {
        ppx = new float[N];
        ppy = new float[N];
        psp = new float[N];
        psz = new float[N];
        for (int i = 0; i < N; i++) {
            ppx[i] = rnd.nextFloat() * getWidth();
            ppy[i] = getHeight() * (0.5f + rnd.nextFloat() * 0.55f);
            psp[i] = 0.4f + rnd.nextFloat() * 1.4f;
            psz[i] = 1.5f + rnd.nextInt(2);
        }
    }

    private void finish() {
        animate().alpha(0f).setDuration(350).withEndAction(() -> {
            if (getParent() instanceof FrameLayout) ((FrameLayout) getParent()).removeView(this);
        }).start();
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h * 0.42f;
        float min = Math.min(w, h);

        // 背景暗化：淡入后淡出
        int bgA = (int) (165 * (frac < 0.5f ? frac * 2f : (1f - frac) * 2f));
        canvas.drawColor(withAlpha(0x000000, bgA));

        // 中心径向圣辉
        float glowR = min * (0.32f + 0.55f * frac);
        glow.setShader(new RadialGradient(cx, cy, glowR,
                new int[]{0x90A78BFA, 0x45FBBF24, 0x00000000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, glowR, glow);

        // 两道扩散环（紫金交错）
        for (int i = 0; i < 2; i++) {
            float rt = (frac + i * 0.42f) % 1f;
            float rr = min * 0.18f + rt * min * 0.72f;
            ring.setColor(withAlpha(i == 0 ? PURPLE : GOLD, (int) (180 * (1f - rt))));
            ring.setStrokeWidth(Ui.dp(getContext(), 2.5f));
            canvas.drawCircle(cx, cy, rr, ring);
        }

        // 上升光粒
        if (ppx == null) initParticles();
        for (int i = 0; i < N; i++) {
            float y = ppy[i] - frac * h * 0.5f * psp[i];
            if (y < 0) y += h;
            particle.setColor(withAlpha(GOLD, (int) (205 * (1f - frac * 0.55f))));
            canvas.drawCircle(ppx[i], y, Ui.dp(getContext(), psz[i]), particle);
        }

        // 标题紫金渐变（淡入）+ 轻微呼吸
        int ta = (int) (255 * Math.min(1f, frac * 2.2f));
        title.setShader(new LinearGradient(0, cy - 45, 0, cy + 45,
                withAlpha(PURPLE, ta), withAlpha(GOLD, ta), Shader.TileMode.CLAMP));
        float scale = 1f + 0.05f * (float) Math.sin(frac * Math.PI * 4);
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        canvas.drawText("圣者觉醒", cx, cy, title);
        canvas.restore();

        // 副标题
        sub.setColor(withAlpha(TXT_C, ta));
        canvas.drawText("词灵圣者 · 连续打卡 30 天", cx, cy + Ui.dp(getContext(), 42), sub);
    }
}
