package com.cet4.cards;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 把计时交给系统闹钟：进程被 OEM 冻结时，闹钟依然会唤醒我们。
 *  fire   —— 精确到"下一次该弹的时刻"
 *  resync —— 每 5 分钟一次，用于在冻结状态下重新评估前台应用
 */
public class AlarmScheduler {

    private static final int REQ_FIRE = 21;
    private static final int REQ_RESYNC = 22;
    private static final int REQ_TEST = 23;

    private static PendingIntent pi(Context c, int req) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(AlarmReceiver.ACTION_ALARM);
        return PendingIntent.getBroadcast(c, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static AlarmManager am(Context c) {
        return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    }

    private static boolean canExact(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager m = am(c);
                return m != null && m.canScheduleExactAlarms();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void scheduleFire(Context c, long atMs) {
        AlarmManager m = am(c);
        if (m == null) return;
        long when = Math.max(atMs, System.currentTimeMillis() + 1000L);
        setAlarm(m, when, pi(c, REQ_FIRE), c);
        Diag.note(c, "alarm_fire", when);
    }

    /**
     * 排一个"尽量准时"的闹钟：
     * 1) 优先 setExactAndAllowWhileIdle（需要精确闹钟权限）
     * 2) 权限被拒时退到 setExact —— 这个不需要权限，亮屏时同样准时
     * 3) 最后才用 setAndAllowWhileIdle（会被系统推迟几分钟）
     */
    private static void setAlarm(AlarmManager m, long when, PendingIntent p, Context c) {
        try {
            m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
            Diag.log(c, "排闹钟(exact+idle)");
            return;
        } catch (SecurityException ignored) {
        } catch (Throwable ignored) {
        }
        try {
            m.setExact(AlarmManager.RTC_WAKEUP, when, p);
            Diag.log(c, "排闹钟(exact)");
            return;
        } catch (Throwable ignored) {
        }
        try {
            m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
            Diag.log(c, "排闹钟(不精确)");
        } catch (Throwable t) {
            Diag.log(c, "排闹钟失败：" + t.getClass().getSimpleName());
        }
    }

    public static void cancelFire(Context c) {
        AlarmManager m = am(c);
        if (m == null) return;
        try {
            m.cancel(pi(c, REQ_FIRE));
            Diag.note(c, "alarm_fire", 0);
        } catch (Throwable ignored) {
        }
    }

    /** 每 5 分钟醒一次，防止冻结期间错过"进入/离开应用" */
    public static void scheduleResync(Context c) {
        AlarmManager m = am(c);
        if (m == null) return;
        long when = System.currentTimeMillis() + 5 * 60_000L;
        try {
            m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi(c, REQ_RESYNC));
            Diag.note(c, "alarm_resync", when);
        } catch (Throwable ignored) {
        }
    }

    public static void cancelAll(Context c) {
        AlarmManager m = am(c);
        if (m == null) return;
        try {
            m.cancel(pi(c, REQ_FIRE));
            m.cancel(pi(c, REQ_RESYNC));
            m.cancel(PendingIntent.getBroadcast(c, REQ_TEST,
                    new Intent(c, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_TEST),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } catch (Throwable ignored) {
        }
    }

    /** 延迟若干毫秒强制弹一条：用来验证"后台状态到底弹不弹得出来" */
    public static void scheduleTest(Context c, long delayMs) {
        AlarmManager m = am(c);
        if (m == null) return;
        PendingIntent p = PendingIntent.getBroadcast(c, REQ_TEST,
                new Intent(c, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_TEST),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long when = System.currentTimeMillis() + Math.max(1000L, delayMs);
        setAlarm(m, when, p, c);
        Diag.log(c, "已排测试弹窗：" + (delayMs / 1000) + " 秒后");
    }
}
