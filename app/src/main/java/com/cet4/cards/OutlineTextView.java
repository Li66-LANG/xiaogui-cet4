package com.cet4.cards;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * 带实心描边的文字。
 *
 * 为什么不用 TextView 的 shadowLayer：那是柔化阴影，遇到浅色或花画面会糊成一团。
 * 这里分两遍绘制——先用 STROKE 画一圈实心边，再画实心字，任何背景上都清晰。
 */
public class OutlineTextView extends View {

    private final TextPaint strokePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint fillPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private String text = "";
    private StaticLayout strokeLayout;
    private StaticLayout fillLayout;
    private float strokeWidth = 0f;
    private int padH;
    private int padV;
    private int maxWidthPx = 0;
    private float sizePx = 48f;

    public OutlineTextView(Context c) {
        this(c, null);
    }

    /** 布局文件里用到自定义 View 时，必须提供这个构造函数，否则 inflate 会直接崩 */
    public OutlineTextView(Context c, AttributeSet attrs) {
        super(c, attrs);
        density = c.getResources().getDisplayMetrics().density;
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        fillPaint.setTextSize(sizePx);
        strokePaint.setTextSize(sizePx);
        recomputePad();
    }

    public void setText(String s) {
        String v = s == null ? "" : s;
        if (v.equals(text)) return;
        text = v;
        requestLayout();
        invalidate();
    }

    public void setTextSizeSp(float sp) {
        sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                getResources().getDisplayMetrics());
        fillPaint.setTextSize(sizePx);
        strokePaint.setTextSize(sizePx);
        requestLayout();
        invalidate();
    }

    public void setBold(boolean bold) {
        fillPaint.setFakeBoldText(bold);
        strokePaint.setFakeBoldText(bold);
        requestLayout();
        invalidate();
    }

    public void setTextColor(int color) {
        fillPaint.setColor(color);
        invalidate();
    }

    /** widthPx 传 0 表示按字号自动（约字号的 11%） */
    public void setStroke(int color, float widthPx) {
        float w = widthPx > 0 ? widthPx : sizePx * 0.11f;
        strokePaint.setColor(color);
        strokePaint.setStrokeWidth(w);
        strokeWidth = w;
        recomputePad();
        requestLayout();
        invalidate();
    }

    public void setMaxWidthPx(int px) {
        maxWidthPx = px;
        requestLayout();
    }

    private void recomputePad() {
        int extra = (int) (strokeWidth / 2f) + Math.round(2 * density);
        padH = extra + Math.round(2 * density);
        padV = extra;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int avail = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED || avail <= 0) {
            avail = (int) (getResources().getDisplayMetrics().widthPixels * 0.9f);
        }
        int limit = Math.max(60, avail - padH * 2);
        if (maxWidthPx > 0) limit = Math.min(limit, maxWidthPx);

        // 短文本不让窗口撑满，否则透明区域会挡触摸
        int natural = (int) Math.ceil(fillPaint.measureText(text)) + (int) strokeWidth + 2;
        int textW = Math.min(Math.max(natural, 1), limit);

        fillLayout = build(fillPaint, textW);
        strokeLayout = build(strokePaint, textW);

        int w = textW + padH * 2;
        int h = fillLayout.getHeight() + padV * 2;
        setMeasuredDimension(resolveSize(w, widthSpec), resolveSize(h, heightSpec));
    }

    private StaticLayout build(TextPaint p, int w) {
        return StaticLayout.Builder
                .obtain(text, 0, text.length(), p, Math.max(1, w))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(0f, 1.06f)
                .build();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (fillLayout == null) return;
        canvas.save();
        canvas.translate(padH, padV);
        if (strokeWidth > 0) strokeLayout.draw(canvas);
        fillLayout.draw(canvas);
        canvas.restore();
    }
}
