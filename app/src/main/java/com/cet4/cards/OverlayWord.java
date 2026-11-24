package com.cet4.cards;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 悬浮窗单词卡片。
 *
 * 为什么不只靠通知：国产系统默认不允许后台应用"弹出"通知横幅，
 * 通知会静静躺在通知栏里不打扰你——那就失去意义了。
 * 有了「显示在其他应用上层」权限后，用悬浮窗可以确保真的浮在应用上面。
 */
public class OverlayWord {

    private static View current;
    private static WindowManager wm;
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static Runnable timeoutTask;

    public static boolean canDraw(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return Settings.canDrawOverlays(c);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static void dismiss() {
        if (timeoutTask != null) {
            UI.removeCallbacks(timeoutTask);
            timeoutTask = null;
        }
        try {
            if (current != null && wm != null) wm.removeView(current);
        } catch (Throwable ignored) {
        }
        current = null;
    }

    public static boolean show(Context c, Word w) {
        return show(c, w, null);
    }

    /**
     * @param onTimeout 自动消失且用户没点任何按钮时回调（用来把词收进「我的生词」）
     */
    public static boolean show(Context c, Word w, Runnable onTimeout) {
        boolean acc = WordAccessibility.ready();
        if (w == null || (!acc && !canDraw(c))) return false;
        dismiss();

        Context app = c.getApplicationContext();
        try {
            wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return false;

            DisplayMetrics dm = app.getResources().getDisplayMetrics();
            final int screenW = dm.widthPixels;
            final int screenH = dm.heightPixels;
            int maxTextW = (int) (screenW * 0.84f);

            int fg = Style.fg(c);
            int stroke = Style.strokeColor(c);
            float k = Prefs.ovScale(c) / 100f;

            // 背景优先级：自定义图片 > 纯色 > 全透明（只有文字浮在画面上）
            LinearLayout box = new LinearLayout(app);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            if (Style.applyBg(c, box)) {
                box.setPadding(dp(app, 18), dp(app, 14), dp(app, 18), dp(app, 14));
            } else {
                box.setPadding(dp(app, 20), dp(app, 16), dp(app, 20), dp(app, 14));
            }

            OutlineTextView word = new OutlineTextView(app);
            word.setTextSizeSp(30 * k);
            word.setBold(true);
            word.setTextColor(fg);
            word.setStroke(stroke, 0);
            word.setMaxWidthPx(maxTextW);
            word.setText(w.w);
            // 必须显式 WRAP_CONTENT：竖直 LinearLayout 的默认是 MATCH_PARENT，
            // 那样单词会被拉满整行、贴到左边，其它行却是居中的。
            box.addView(word, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            if (w.ph != null && w.ph.length() > 0) {
                OutlineTextView ph = new OutlineTextView(app);
                ph.setTextSizeSp(14 * k);
                ph.setTextColor(fg);
                ph.setStroke(stroke, 0);
                ph.setMaxWidthPx(maxTextW);
                ph.setText(w.ph);
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                plp.topMargin = dp(app, 2);
                box.addView(ph, plp);
            }

            OutlineTextView meaning = new OutlineTextView(app);
            meaning.setTextSizeSp(17 * k);
            meaning.setTextColor(fg);
            meaning.setStroke(stroke, 0);
            meaning.setMaxWidthPx(maxTextW);
            meaning.setText(w.meaningLine());
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            mlp.topMargin = dp(app, 3);
            box.addView(meaning, mlp);

            LinearLayout row = new LinearLayout(app);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setPadding(0, dp(app, 6), 0, 0);
            row.setBaselineAligned(false);

            TextView close = pill(app, c.getString(R.string.act_close), 0xE6FFFFFF, 0x4D000000);
            close.setOnClickListener(v -> dismiss());
            row.addView(close);

            TextView unknown = pill(app, c.getString(R.string.act_unknown), Color.WHITE, 0x66E5484D);
            unknown.setOnClickListener(v -> {
                WordDb.get(app).rate(w.id, 0);
                dismiss();
            });
            row.addView(unknown);

            TextView know = pill(app, c.getString(R.string.act_know), Color.WHITE, 0x6600A97A);
            know.setOnClickListener(v -> {
                WordDb.get(app).rate(w.id, 1);
                dismiss();
            });
            row.addView(know);

            box.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            // 按钮那一排按自然宽度单独量一次。
            // 竖直 LinearLayout 里子 View 默认是 MATCH_PARENT，会被父宽压窄，
            // 结果"记住了"被挤出卡片右边缘剪掉——宽度必须由它兜底。
            row.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int rowW = row.getMeasuredWidth();
            Diag.log(app, "按钮排宽度：" + rowW + "px");

            // 首选无障碍覆盖层：国产 ROM 拦不到它，能真正浮在全屏视频上面。
            // 没有开无障碍时退回普通悬浮窗（在部分系统上会被后台拦截不渲染）。
            int type;
            boolean useAcc = WordAccessibility.ready() && WordAccessibility.get() != null;
            if (useAcc) {
                WindowManager awm = (WindowManager) WordAccessibility.get()
                        .getSystemService(Context.WINDOW_SERVICE);
                if (awm != null) wm = awm;
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                type = WindowManager.LayoutParams.TYPE_PHONE;
            }
            Diag.log(app, useAcc ? "用无障碍覆盖层显示" : "用普通悬浮窗显示");

            // 宽度跟着文字走：透明窗口仍会吃掉触摸，收窄后才能滑视频
            // 尺寸自己量：交给系统 WRAP_CONTENT 时，个别 ROM 第一次测量会偏矮，
            // 结果最下面那排按钮被截掉半截（"显示不全"就是它）。
            int ws = View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST);
            int hs = View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST);
            box.measure(ws, hs);
            int needW = rowW + box.getPaddingLeft() + box.getPaddingRight();
            int bw = Math.min(Math.max(box.getMeasuredWidth(), needW), screenW - dp(app, 6));
            int bh = Math.max(1, box.getMeasuredHeight());

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    bw,
                    bh + dp(app, 4),
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            int y = (int) (screenH * Prefs.ovPos(c) / 100f);
            // 卡片比屏幕还高，或贴太靠下时往上挪，别让底部超出屏幕
            int maxY = screenH - lp.height - dp(app, 8);
            if (maxY > 0 && y > maxY) y = maxY;
            if (y < dp(app, 4)) y = dp(app, 4);
            lp.y = y;

            wm.addView(box, lp);
            current = box;

            // 挂上去之后再量一次：字体度量、换行结果可能在第一次测量后才有变化，
            // 只要新尺寸比窗口大，就按新尺寸校正一次，保证内容完整可见。
            final WindowManager.LayoutParams flp = lp;
            final int rowNatural = rowW;
            box.post(() -> {
                try {
                    if (current != box) return;
                    box.measure(
                            View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST));
                    int bw2 = Math.max(box.getMeasuredWidth(),
                            rowNatural + box.getPaddingLeft() + box.getPaddingRight());
                    int bh2 = box.getMeasuredHeight();
                    if (bw2 > flp.width || bh2 + dp(app, 4) > flp.height) {
                        flp.width = Math.min(Math.max(flp.width, bw2), screenW - dp(app, 6));
                        flp.height = Math.max(flp.height, bh2 + dp(app, 4));
                        wm.updateViewLayout(box, flp);
                        Diag.log(app, "校正窗口尺寸：" + flp.width + "x" + flp.height);
                    }
                } catch (Throwable ignored) {
                }
            });

            // 自动收起：0 表示不自动消失，只能点关闭。
            // 到点自己消失说明用户既没看会也没点，按规则收进「我的生词」。
            int sec = Prefs.ovDismiss(c);
            if (sec > 0) {
                timeoutTask = () -> {
                    timeoutTask = null;
                    if (onTimeout != null) {
                        try {
                            onTimeout.run();
                        } catch (Throwable ignored) {
                        }
                    }
                    dismiss();
                };
                UI.postDelayed(timeoutTask, sec * 1000L);
            }
            return true;
        } catch (Throwable t) {
            current = null;
            Diag.log(app, "悬浮窗失败：" + t.getClass().getSimpleName()
                    + " " + t.getMessage());
            return false;
        }
    }

    private static TextView pill(Context c, String text, int textColor, int bgColor) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextColor(textColor);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bgColor);
        g.setCornerRadius(dp(c, 14));
        tv.setBackground(g);
        tv.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, 8);
        tv.setLayoutParams(lp);
        return tv;
    }
}
