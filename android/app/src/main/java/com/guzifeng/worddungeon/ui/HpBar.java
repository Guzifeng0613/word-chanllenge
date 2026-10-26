package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 血量条：圆角背景 + 前景按比例绘制 */
public class HpBar extends View {

    private float ratio = 1f;
    private int fg = Ui.RED;
    private final int bg;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HpBar(Context c, int fg, int bg) {
        super(c);
        this.fg = fg;
        this.bg = bg;
    }

    public void setRatio(float r) {
        ratio = Math.max(0f, Math.min(1f, r));
        invalidate();
    }

    public void setColor(int c) {
        fg = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float w = getWidth();
        if (w <= 0 || h <= 0) return;
        RectF back = new RectF(0, 0, w, h);
        p.setColor(bg);
        canvas.drawRoundRect(back, h / 2, h / 2, p);
        float fw = w * ratio;
        if (fw > h) {
            p.setColor(fg);
            canvas.drawRoundRect(new RectF(0, 0, fw, h), h / 2, h / 2, p);
        }
    }
}
