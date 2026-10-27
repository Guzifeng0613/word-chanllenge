package com.guzifeng.worddungeon.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 血量条：圆角背景 + 前景按比例绘制。R2-5：受击瞬间白闪（≤200ms），不干扰 setRatio 的 160ms 平滑。 */
public class HpBar extends View {

    private float ratio = 1f;
    private int fg = Ui.RED;
    private final int bg;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();          // R2-5：复用 RectF，避免每帧 onDraw 分配
    // R2-5：受击亮闪（白叠加）衰减量，独立于 ratio 动画
    private ValueAnimator flashAnim;
    private float flashAmt = 0f;

    public HpBar(Context c, int fg, int bg) {
        super(c);
        this.fg = fg;
        this.bg = bg;
    }

    private ValueAnimator anim;

    public void setRatio(float r) {
        float target = Math.max(0f, Math.min(1f, r));
        if (Math.abs(target - ratio) < 0.001f) return;
        if (anim != null) anim.cancel();
        final float from = ratio;
        anim = ValueAnimator.ofFloat(from, target);
        anim.setDuration(160);
        anim.addUpdateListener(a -> {
            ratio = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    public void setColor(int c) {
        fg = c;
        invalidate();
    }

    /** R2-5：受击瞬间白闪（高亮叠加，≤200ms 内衰减恢复）。怪物/玩家受击链路接入；与 setRatio 互不干扰。 */
    public void flashHit() {
        try {
            if (flashAnim != null) flashAnim.cancel();
            flashAnim = ValueAnimator.ofFloat(0.9f, 0f);
            flashAnim.setDuration(180);
            flashAnim.addUpdateListener(a -> {
                flashAmt = (float) a.getAnimatedValue();
                invalidate();
            });
            flashAnim.start();
        } catch (Throwable ignore) {}
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float w = getWidth();
        if (w <= 0 || h <= 0) return;
        rect.set(0, 0, w, h);
        p.setColor(bg);
        canvas.drawRoundRect(rect, h / 2, h / 2, p);
        float fw = w * ratio;
        if (fw > h) {
            p.setColor(fg);
            rect.set(0, 0, fw, h);
            canvas.drawRoundRect(rect, h / 2, h / 2, p);
            // R2-5：受击白闪——在 fg 上叠一层随衰减的白色高亮，180ms 内自动消退
            if (flashAmt > 0.01f) {
                int a = (int) (Math.min(1f, flashAmt) * 210f);
                p.setColor((a << 24) | 0x00FFFFFF);
                canvas.drawRoundRect(rect, h / 2, h / 2, p);
            }
        }
    }
}
