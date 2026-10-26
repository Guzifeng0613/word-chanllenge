package com.guzifeng.worddungeon.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * 氛围背景（V11.3）：地下城沉浸底，Drawable 形式，任何 View 都可 setBackground（不占布局）。
 *  - 顶部 / 底部两团缓慢呼吸、漂移的径向辉光（青 / 金）
 *  - 基础纵向渐变，制造纵深
 *  - 若干缓慢上升、左右漂移的微光粒子（符文尘埃），青 / 金 / 紫随机
 *  纯 Canvas 绘制，零图片、零网络。
 */
public class AmbientBackground extends Drawable {

    private final List<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator anim;
    private float t = 0f;
    private boolean seeded = false;

    public AmbientBackground() {
        this(26);
    }

    public AmbientBackground(int particleCount) {
        for (int i = 0; i < particleCount; i++) particles.add(new Particle());
    }

    private void seed() {
        for (Particle p : particles) p.seedRandom();
        seeded = true;
    }

    @Override
    protected void onBoundsChange(android.graphics.Rect bounds) {
        super.onBoundsChange(bounds);
        if (!seeded) seed();
        if (anim == null) {
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(16000);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(a -> {
                t = (float) a.getAnimatedValue();
                invalidateSelf();
            });
            anim.start();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        int w = getBounds().width(), h = getBounds().height();
        if (w == 0 || h == 0) return;

        // 基础纵向渐变
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF0E1C30, 0xFF0B1526, 0xFF0A1220},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, bg);

        // 两团辉光
        drawGlow(canvas, w, h,
                w * (0.28f + 0.10f * sin(t * 6.28f)),
                h * (0.16f + 0.05f * sin(t * 6.28f + 1.2f)),
                Math.max(w, h) * 0.55f, 0x5538BDF8, 0.0038f,
                0.55f + 0.45f * sin(t * 6.28f));
        drawGlow(canvas, w, h,
                w * (0.78f + 0.08f * sin(t * 6.28f + 2.6f)),
                h * (0.92f + 0.05f * sin(t * 6.28f + 0.4f)),
                Math.max(w, h) * 0.48f, 0x40FBBF24, 0.0025f,
                0.45f + 0.35f * sin(t * 6.28f + 1.8f));

        // 粒子
        for (Particle p : particles) p.draw(canvas, w, h, t, paint);
    }

    private void drawGlow(Canvas canvas, int w, int h, float cx, float cy, float r,
                          int centerColor, float edgeFraction, float mul) {
        int edge = withAlpha(centerColor, edgeFraction);
        int center = withAlpha(centerColor, clamp01(mul));
        RadialGradient rg = new RadialGradient(cx, cy, r,
                new int[]{center, edge}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(rg);
        canvas.drawRect(0, 0, w, h, p);
    }

    private static float sin(float x) { return (float) Math.sin(x); }

    private static float clamp01(float x) { return Math.max(0f, Math.min(1f, x)); }

    private static int withAlpha(int color, float a) {
        int baseAlpha = (color >> 24) & 0xFF;
        int alpha = Math.round(baseAlpha * Math.max(0f, Math.min(1f, a)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** 停止动画：宿主 View onDetachedFromWindow 时调用，避免无限动画悬挂。 */
    public void stop() {
        if (anim != null) { anim.cancel(); anim = null; }
    }

    @Override
    public void setAlpha(int alpha) { paint.setAlpha(alpha); }

    @Override
    public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    /** 单个漂浮粒子。 */
    private static final class Particle {
        float x0, y0, size, rise, drift, phase, alpha;
        int color;

        private static final int[] COLORS = {0x6638BDF8, 0x55FBBF24, 0x4DC084FC, 0x402DD4BF};

        void seedRandom() {
            x0 = (float) Math.random();
            y0 = (float) Math.random();
            size = 1.5f + (float) Math.random() * 3.2f;
            rise = 0.04f + (float) Math.random() * 0.10f;
            drift = 0.02f + (float) Math.random() * 0.05f;
            phase = (float) (Math.random() * Math.PI * 2);
            alpha = 0.25f + (float) Math.random() * 0.55f;
            color = COLORS[(int) (Math.random() * COLORS.length)];
        }

        void draw(Canvas canvas, int w, int h, float t, Paint paint) {
            float y = y0 - rise * t;
            y = y - (float) Math.floor(y);
            float x = x0 + drift * (float) Math.sin(t * 6.28f * 1.5f + phase);
            float px = x * w;
            float py = y * h;
            float twinkle = 0.55f + 0.45f * (float) Math.sin(t * 6.28f * 2f + phase);
            paint.setColor(withAlpha(color, alpha * twinkle));
            canvas.drawCircle(px, py, size * (w / 420f), paint);
        }

        private static int withAlpha(int color, float a) {
            int baseAlpha = (color >> 24) & 0xFF;
            int alpha = Math.round(baseAlpha * Math.max(0f, Math.min(1f, a)));
            return (alpha << 24) | (color & 0x00FFFFFF);
        }
    }
}
