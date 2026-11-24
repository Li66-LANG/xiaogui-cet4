package com.cet4.cards;

import android.content.Context;
import android.content.SharedPreferences;

/** 诊断信息：把服务内部状态写到 SharedPreferences，首页实时读取显示 */
public class Diag {
    private static final String F = "diag";
    private static final String K_LOG = "log";
    private static final int MAX_LOG = 40;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(F, Context.MODE_PRIVATE);
    }

    public static void put(Context c, String k, String v) {
        sp(c).edit().putString(k, v).apply();
    }

    public static String get(Context c, String k) {
        return sp(c).getString(k, "");
    }

    public static long getLong(Context c, String k) {
        try {
            return Long.parseLong(get(c, k));
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 每次轮询写一次心跳 */
    public static void heartbeat(Context c, String evPkg, String stPkg, boolean inApp,
                                 long remainMs, String source) {
        sp(c).edit()
                .putString("tick", String.valueOf(System.currentTimeMillis()))
                .putString("ev", evPkg == null ? "" : evPkg)
                .putString("st", stPkg == null ? "" : stPkg)
                .putString("pkg", evPkg != null ? evPkg : (stPkg == null ? "" : stPkg))
                .putString("inapp", inApp ? "1" : "0")
                .putString("src", source == null ? "" : source)
                .putString("remain", String.valueOf(remainMs))
                .apply();
    }

    public static void note(Context c, String k, long v) {
        sp(c).edit().putString(k, String.valueOf(v)).apply();
    }

    public static void log(Context c, String line) {
        String old = get(c, K_LOG);
        String[] lines = old.length() == 0 ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder();
        int start = lines.length >= MAX_LOG ? lines.length - MAX_LOG + 1 : 0;
        for (int i = start; i < lines.length; i++) {
            if (lines[i].length() > 0) sb.append(lines[i]).append('\n');
        }
        String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date());
        sb.append(t).append(' ').append(line);
        sp(c).edit().putString(K_LOG, sb.toString()).apply();
    }

    public static void clearLog(Context c) {
        sp(c).edit().remove(K_LOG).apply();
    }

    /** 导出给用户复制的整段诊断文本 */
    public static String dump(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("== 小龟过四级 诊断 ==\n");
        sb.append("时间：").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.CHINA).format(new java.util.Date())).append('\n');
        sb.append("监控应用：").append(join(Prefs.targets(c))).append('\n');
        sb.append("间隔：").append(Prefs.interval(c)).append(" 分钟\n");
        sb.append("服务启动：").append(fmt(getLong(c, "svc_start"))).append('\n');
        sb.append("最后心跳：").append(fmt(getLong(c, "tick"))).append('\n');
        sb.append("当前前台：").append(orDash(get(c, "pkg"))).append('\n');
        sb.append("事件识别：").append(orDash(get(c, "ev"))).append('\n');
        sb.append("统计识别：").append(orDash(get(c, "st"))).append('\n');
        sb.append("是否在监控应用：").append("1".equals(get(c, "inapp")) ? "是" : "否").append('\n');
        sb.append("触发来源：").append(orDash(get(c, "src"))).append('\n');
        sb.append("上次提醒：").append(fmt(getLong(c, "last_notify"))).append('\n');
        sb.append("精确闹钟：").append(fmt(getLong(c, "alarm_fire"))).append('\n');
        sb.append("巡检闹钟：").append(fmt(getLong(c, "alarm_resync"))).append('\n');
        sb.append("悬浮窗权限：").append(OverlayWord.canDraw(c) ? "有" : "无").append('\n');
        sb.append("无障碍服务：").append(WordAccessibility.ready() ? "已连接" : "未连接").append('\n');
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                android.app.NotificationChannel ch = nm.getNotificationChannel(Notifier.CH_WORD);
                sb.append("单词通知渠道重要级：")
                        .append(ch == null ? "未创建" : String.valueOf(ch.getImportance()))
                        .append("（4=横幅 3=无声 2=静默）").append('\n');
            }
            sb.append("通知总开关：")
                    .append(androidx.core.app.NotificationManagerCompat.from(c)
                            .areNotificationsEnabled() ? "开" : "关").append('\n');
        } catch (Throwable ignored) {
        }
        String crash = get(c, "crash");
        if (crash.length() > 0) {
            sb.append("最近崩溃：").append(crash).append('\n');
        }
        try {
            WordDb db = WordDb.get(c);
            sb.append("词库条数：").append(db.total()).append('\n');
            int mask = Prefs.books(c);
            StringBuilder bs = new StringBuilder();
            for (int b : Books.all()) {
                if ((mask & b) == 0) continue;
                if (bs.length() > 0) bs.append(", ");
                bs.append(Books.name(b));
            }
            sb.append("复习词书：").append(bs.length() == 0 ? "未选" : bs.toString())
                    .append("（").append(db.countInBooks(mask)).append(" 词）").append('\n');
            sb.append("只推生词：").append(Prefs.mineOnly(c) ? "是" : "否").append('\n');
            sb.append("我的生词：").append(db.countMine()).append(" 条")
                    .append("　待巩固：").append(db.countStatus(2)).append(" 条")
                    .append("　已掌握：").append(db.countStatus(3)).append(" 条").append('\n');
            sb.append("今日提醒：").append(db.todayCount()).append('\n');
        } catch (Throwable t) {
            sb.append("词库读取失败：").append(t.getClass().getSimpleName()).append('\n');
        }
        sb.append("--- 日志 ---\n").append(get(c, K_LOG)).append('\n');
        return sb.toString();
    }

    private static String join(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static String orDash(String s) {
        return s == null || s.length() == 0 ? "—" : s;
    }

    private static String fmt(long ts) {
        if (ts <= 0) return "—";
        long sec = (System.currentTimeMillis() - ts) / 1000;
        String when = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date(ts));
        if (sec < 0) return when + "（" + (-sec) + " 秒后）";
        return when + "（" + sec + " 秒前）";
    }
}
