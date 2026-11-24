package com.cet4.cards;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collection;
import java.util.LinkedHashSet;

/** 用户设置 */
public class Prefs {
    private static final String F = "cet4_prefs";
    public static final String K_INTERVAL = "interval_min";
    public static final String K_ENABLED = "enabled";
    public static final String K_MODE = "mode";
    public static final String K_LAST = "last_word";
    public static final String K_TARGETS = "targets";
    public static final String K_SIMULATE = "simulate";

    /** 默认监控：几个常见短视频应用 */
    public static final String[] DEFAULT_TARGETS = {
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite",
            "com.zhiliaoapp.musically"
    };

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(F, Context.MODE_PRIVATE);
    }

    public static int interval(Context c) {
        return sp(c).getInt(K_INTERVAL, 5);
    }

    public static void setInterval(Context c, int min) {
        sp(c).edit().putInt(K_INTERVAL, min).apply();
    }

    public static boolean enabled(Context c) {
        return sp(c).getBoolean(K_ENABLED, false);
    }

    public static void setEnabled(Context c, boolean b) {
        sp(c).edit().putBoolean(K_ENABLED, b).apply();
    }

    /** 取词策略（v4 起废弃，保留字段只为兼容旧安装） */
    public static int mode(Context c) {
        return sp(c).getInt(K_MODE, 1);
    }

    public static void setMode(Context c, int m) {
        sp(c).edit().putInt(K_MODE, m).apply();
    }

    // ---- 词书选择 ----

    /** 要复习的词书位标，默认前三本（不含 4434 词的乱序全库） */
    public static int books(Context c) {
        return sp(c).getInt("books", Books.C4 | Books.READ | Books.FREQ);
    }

    public static void setBooks(Context c, int mask) {
        sp(c).edit().putInt("books", mask & Books.ALL).apply();
    }

    /** 只推送「我的生词」 */
    public static boolean mineOnly(Context c) {
        return sp(c).getBoolean("mine_only", false);
    }

    public static void setMineOnly(Context c, boolean b) {
        sp(c).edit().putBoolean("mine_only", b).apply();
    }

    /** 上一条推送的词（用于"连续 3 条不重复"） */
    public static String lastWord(Context c) {
        return sp(c).getString(K_LAST, "");
    }

    public static void setLastWord(Context c, String w) {
        sp(c).edit().putString(K_LAST, w).apply();
    }

    /** 上上条推送的词 */
    public static String prevWord(Context c) {
        return sp(c).getString("prev_word", "");
    }

    public static void setPrevWord(Context c, String w) {
        sp(c).edit().putString("prev_word", w).apply();
    }

    /** 当前监控的包名列表 */
    public static String[] targets(Context c) {
        String s = sp(c).getString(K_TARGETS, "");
        if (s == null || s.length() == 0) return DEFAULT_TARGETS;
        String[] arr = s.split(",");
        if (arr.length == 1 && arr[0].length() == 0) return DEFAULT_TARGETS;
        return arr;
    }

    public static void setTargets(Context c, Collection<String> pkgs) {
        LinkedHashSet<String> set = new LinkedHashSet<>(pkgs);
        StringBuilder sb = new StringBuilder();
        for (String p : set) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p);
        }
        sp(c).edit().putString(K_TARGETS, sb.toString()).apply();
    }

    /** 调试用：假装一直在监控应用里 */
    public static boolean simulate(Context c) {
        return sp(c).getBoolean(K_SIMULATE, false);
    }

    public static void setSimulate(Context c, boolean b) {
        sp(c).edit().putBoolean(K_SIMULATE, b).apply();
    }

    /** 是否用悬浮窗弹出（需要「显示在其他应用上层」权限） */
    public static boolean overlay(Context c) {
        return sp(c).getBoolean("overlay", true);
    }

    public static void setOverlay(Context c, boolean b) {
        sp(c).edit().putBoolean("overlay", b).apply();
    }

    // ---- 悬浮字外观 ----

    /** 字体颜色（不带 alpha） */
    public static int ovFg(Context c) {
        return sp(c).getInt("ov_fg", 0xFFFFFF);
    }

    public static void setOvFg(Context c, int rgb) {
        sp(c).edit().putInt("ov_fg", rgb & 0xFFFFFF).apply();
    }

    /** 背景颜色（不带 alpha） */
    public static int ovBg(Context c) {
        return sp(c).getInt("ov_bg", 0x000000);
    }

    public static void setOvBg(Context c, int rgb) {
        sp(c).edit().putInt("ov_bg", rgb & 0xFFFFFF).apply();
    }

    /** 背景不透明度 0-100，0 = 完全透明 */
    public static int ovBgAlpha(Context c) {
        return sp(c).getInt("ov_bg_a", 0);
    }

    public static void setOvBgAlpha(Context c, int pct) {
        int v = Math.max(0, Math.min(100, pct));
        sp(c).edit().putInt("ov_bg_a", v).apply();
    }

    /** 描边 0 = 自动，1 = 黑，2 = 白 */
    public static int ovStroke(Context c) {
        return sp(c).getInt("ov_stroke", 0);
    }

    public static void setOvStroke(Context c, int v) {
        sp(c).edit().putInt("ov_stroke", v).apply();
    }

    /** 字号百分比 60-200，同时决定卡片整体大小 */
    public static int ovScale(Context c) {
        return sp(c).getInt("ov_scale", 100);
    }

    public static void setOvScale(Context c, int v) {
        sp(c).edit().putInt("ov_scale", Math.max(60, Math.min(200, v))).apply();
    }

    /** 悬浮窗垂直位置：屏幕高度的百分比，2-88 */
    public static int ovPos(Context c) {
        return sp(c).getInt("ov_pos", 8);
    }

    public static void setOvPos(Context c, int v) {
        sp(c).edit().putInt("ov_pos", Math.max(2, Math.min(88, v))).apply();
    }

    /** 多少秒没操作自动收起，0 = 不自动消失，最大 12 秒 */
    public static int ovDismiss(Context c) {
        return sp(c).getInt("ov_dismiss", 12);
    }

    public static void setOvDismiss(Context c, int v) {
        sp(c).edit().putInt("ov_dismiss", Math.max(0, Math.min(12, v))).apply();
    }

    /** 背景图缩放：100 = 铺满，最大 300 */
    public static int ovImgScale(Context c) {
        return sp(c).getInt("ov_img_scale", 100);
    }

    public static void setOvImgScale(Context c, int v) {
        sp(c).edit().putInt("ov_img_scale", Math.max(100, Math.min(300, v))).apply();
    }

    /** 背景图偏移：-50 ~ 50，单位是可视区域的百分比 */
    public static int ovImgDx(Context c) {
        return sp(c).getInt("ov_img_dx", 0);
    }

    public static void setOvImgDx(Context c, int v) {
        sp(c).edit().putInt("ov_img_dx", Math.max(-50, Math.min(50, v))).apply();
    }

    public static int ovImgDy(Context c) {
        return sp(c).getInt("ov_img_dy", 0);
    }

    public static void setOvImgDy(Context c, int v) {
        sp(c).edit().putInt("ov_img_dy", Math.max(-50, Math.min(50, v))).apply();
    }

    // ---- 运行状态（落盘，进程重启后不丢） ----

    public static boolean sticky(Context c) {
        return sp(c).getBoolean("sticky", false);
    }

    public static void setSticky(Context c, boolean b) {
        sp(c).edit().putBoolean("sticky", b).apply();
    }

    public static int stickyMiss(Context c) {
        return sp(c).getInt("sticky_miss", 0);
    }

    public static void setStickyMiss(Context c, int v) {
        sp(c).edit().putInt("sticky_miss", v).apply();
    }

    public static long lastMissAt(Context c) {
        return sp(c).getLong("miss_at", 0L);
    }

    public static void setLastMissAt(Context c, long v) {
        sp(c).edit().putLong("miss_at", v).apply();
    }

    public static long lastNotify(Context c) {
        return sp(c).getLong("last_notify", 0L);
    }

    public static void setLastNotify(Context c, long v) {
        sp(c).edit().putLong("last_notify", v).apply();
    }

    public static long pollCursor(Context c) {
        return sp(c).getLong("poll_cursor", 0L);
    }

    public static void setPollCursor(Context c, long v) {
        sp(c).edit().putLong("poll_cursor", v).apply();
    }

    public static long alarmAt(Context c) {
        return sp(c).getLong("alarm_at", 0L);
    }

    public static void setAlarmAt(Context c, long v) {
        sp(c).edit().putLong("alarm_at", v).apply();
    }
}
