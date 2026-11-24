package com.cet4.cards;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * 背景图：铺满裁切 + 可缩放 / 平移 + 圆角。
 * RoundedBitmapDrawable 只能等比铺，做不了缩放和移动，所以自己画。
 */
public class CropDrawable extends Drawable {

    private final Bitmap bmp;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF area = new RectF();
    private final RectF dst = new RectF();

    private float radius = 0f;
    private float scale = 1f;
    private float dx = 0f;
    private float dy = 0f;
    private int alpha = 255;

    public CropDrawable(Bitmap b) {
        bmp = b;
    }

    public void setRadius(float r) {
        radius = r;
        invalidateSelf();
    }

    public void setScale(float s) {
        scale = Math.max(0.2f, s);
        invalidateSelf();
    }

    public void setOffset(float x, float y) {
        dx = x;
        dy = y;
        invalidateSelf();
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void draw(Canvas canvas) {
        if (bmp == null || bmp.isRecycled()) return;
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        canvas.save();
        if (radius > 0) {
            path.reset();
            area.set(b);
            path.addRoundRect(area, radius, radius, Path.Direction.CW);
            canvas.clipPath(path);
        }

        float vw = b.width();
        float vh = b.height();
        float cover = Math.max(vw / bmp.getWidth(), vh / bmp.getHeight());
        float s = cover * scale;
        float dw = bmp.getWidth() * s;
        float dh = bmp.getHeight() * s;
        float left = b.left + (vw - dw) / 2f + dx * vw;
        float top = b.top + (vh - dh) / 2f + dy * vh;
        dst.set(left, top, left + dw, top + dh);

        paint.setAlpha(alpha);
        canvas.drawBitmap(bmp, null, dst, paint);
        canvas.restore();
    }
}
