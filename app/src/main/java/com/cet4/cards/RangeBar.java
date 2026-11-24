package com.cet4.cards;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 区间筛选条：两个滑钮，左端是小龟，用来按「连续记住次数」筛词。
 * 刻度 0~3，右侧圆点。拖动时回调 onRange。
 */
public class RangeBar extends View {

    public interface OnRange {
        void onRange(int lo, int hi);
    }

    private static final int STEPS = 3;

    private final Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteRing = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;
    private int lo = 0, hi = STEPS;
    private int dragging = 0;   // 0 无，1 左，2 右
    private Bitmap turtle;
    private BitmapShader shader;
    private Matrix matrix;
    private final Paint turtlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnRange listener;

    public RangeBar(Context c) {
        this(c, null);
    }

    public RangeBar(Context c, @Nullable AttributeSet a) {
        super(c, a);
        density = c.getResources().getDisplayMetrics().density;
        railPaint.setColor(0xFFF0EADA);
        selPaint.setColor(0xFF2F7F86);
        dotPaint.setColor(0xFFFFFFFF);
        tickPaint.setColor(0xFFB3B4A6);
        tickPaint.setTextSize(sp(9.5f));
        tickPaint.setTextAlign(Paint.Align.CENTER);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(3));
        ringPaint.setColor(0xFF2F7F86);
        whiteRing.setStyle(Paint.Style.STROKE);
        whiteRing.setStrokeWidth(dp(2));
        whiteRing.setColor(0xFFFFFFFF);
        try {
            turtle = BitmapFactory.decodeResource(c.getResources(), R.drawable.ic_turtle);
        } catch (Throwable ignored) {
        }
    }

    private float dp(float v) {
        return v * density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }

    public void setOnRange(OnRange l) {
        listener = l;
    }

    public int lo() {
        return lo;
    }

    public int hi() {
        return hi;
    }

    private float pad() {
        return dp(14);
    }

    private float x(int step) {
        float p = pad();
        return p + (getWidth() - p * 2) * step / (float) STEPS;
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, (int) dp(44));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float cy = dp(16);
        float r = getWidth() - pad() * 2;
        RectF rail = new RectF(pad(), cy - dp(4), pad() + r, cy + dp(4));
        cv.drawRoundRect(rail, dp(4), dp(4), railPaint);

        float a = x(lo), b = x(hi);
        RectF sel = new RectF(a, cy - dp(4), b, cy + dp(4));
        cv.drawRoundRect(sel, dp(4), dp(4), selPaint);

        // 刻度
        String[] labels = {"0", "1", "2", "3"};
        for (int i = 0; i <= STEPS; i++) {
            cv.drawText(labels[i], x(i), dp(40), tickPaint);
        }

        // 左滑钮：小龟（圆形裁切，避免方形奶油底露边）
        float ts = dp(28);
        if (turtle != null && !turtle.isRecycled()) {
            float r2 = ts / 2;
            if (shader == null) {
                shader = new BitmapShader(turtle, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                matrix = new Matrix();
                matrix.setScale(ts / turtle.getWidth(), ts / turtle.getHeight());
                shader.setLocalMatrix(matrix);
                turtlePaint.setShader(shader);
            }
            cv.drawCircle(a, cy, r2, turtlePaint);
        } else {
            cv.drawCircle(a, cy, ts / 2, selPaint);
        }
        cv.drawCircle(a, cy, ts / 2, whiteRing);

        // 右滑钮：圆点
        cv.drawCircle(b, cy, dp(9), dotPaint);
        cv.drawCircle(b, cy, dp(9), ringPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float px = e.getX();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dragging = Math.abs(px - x(lo)) <= Math.abs(px - x(hi)) ? 1 : 2;
                    if (Math.abs(px - x(lo)) > dp(30) && Math.abs(px - x(hi)) > dp(30)) {
                        dragging = px < (x(lo) + x(hi)) / 2 ? 1 : 2;
                    }
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                int step = stepOf(px);
                if (dragging == 1) lo = Math.min(step, hi);
                else if (dragging == 2) hi = Math.max(step, lo);
                invalidate();
                if (listener != null) listener.onRange(lo, hi);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = 0;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return true;
    }

    private int stepOf(float px) {
        float p = pad();
        float w = getWidth() - p * 2;
        if (w <= 0) return 0;
        float t = (px - p) / w;
        t = Math.max(0f, Math.min(1f, t));
        return Math.round(t * STEPS);
    }
}
