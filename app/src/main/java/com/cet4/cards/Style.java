package com.cet4.cards;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;

/** 悬浮字外观的解析：把配置换算成真正要绘制的颜色 */
public class Style {

    /** 合成后的背景色（含 alpha） */
    public static int bg(Context c) {
        int pct = Math.max(0, Math.min(100, Prefs.ovBgAlpha(c)));
        int alpha = pct * 255 / 100;
        return (alpha << 24) | (Prefs.ovBg(c) & 0xFFFFFF);
    }

    public static int fg(Context c) {
        return 0xFF000000 | (Prefs.ovFg(c) & 0xFFFFFF);
    }

    /**
     * 描边颜色。默认按"对比"自动选：
     * 背景不透明时看背景，透明时看字色——亮的配黑边，暗的配白边。
     * 用户也可以手动锁定黑或白。
     */
    public static int strokeColor(Context c) {
        int mode = Prefs.ovStroke(c);
        if (mode == 1) return 0xFF000000;
        if (mode == 2) return 0xFFFFFFFF;

        int bg = bg(c);
        int ref = Color.alpha(bg) >= 90 ? bg : fg(c);
        return isLight(ref) ? 0xE6000000 : 0xE6FFFFFF;
    }

    /** 相对亮度判断（不做 sRGB 线性化，够用且快） */
    public static boolean isLight(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        double lum = 0.299 * r + 0.587 * g + 0.114 * b;
        return lum > 150;
    }

    /**
     * 给悬浮字的容器铺底：图片优先，其次纯色，都不设就保持透明。
     * 返回 true 表示铺了底（调用方要相应加内边距）。
     */
    public static boolean applyBg(Context c, View box) {
        int pct = Math.max(0, Math.min(100, Prefs.ovBgAlpha(c)));
        if (pct <= 0) {
            box.setBackground(null);
            return false;
        }

        if (BgImage.has(c)) {
            Bitmap bmp = BgImage.load(c);
            if (bmp != null && !bmp.isRecycled()) {
                CropDrawable cd = new CropDrawable(bmp);
                cd.setRadius(dp(c, 14));
                cd.setScale(Prefs.ovImgScale(c) / 100f);
                cd.setOffset(Prefs.ovImgDx(c) / 100f, Prefs.ovImgDy(c) / 100f);
                cd.setAlpha(pct * 255 / 100);
                box.setBackground(cd);
                return true;
            }
        }

        GradientDrawable g = new GradientDrawable();
        g.setColor(bg(c));
        g.setCornerRadius(dp(c, 14));
        box.setBackground(g);
        return true;
    }

    public static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }
}
