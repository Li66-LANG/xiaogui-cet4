package com.cet4.cards;

import android.content.Context;
import android.os.PowerManager;

import java.util.List;

/**
 * 核心引擎：判断当前是否在受监控应用里、是否该弹、以及排下一次闹钟。
 *
 * 全部状态都落盘（SharedPreferences），所以：
 *   - 服务存活时由服务每 3 秒调一次
 *   - 进程被杀/被冻结时由系统闹钟唤醒后直接调（不依赖前台服务）
 */
public class Engine {

    /** source 仅用于日志 */
    public static void tick(Context c, String source) {
        if (!Prefs.enabled(c)) return;
        try {
            long now = System.currentTimeMillis();
            String[] targets = Prefs.targets(c);

            Detection d = detect(c, now);
            boolean raw = contains(targets, d.evPkg) || contains(targets, d.stPkg) || Prefs.simulate(c);

            // 迟滞：识别到一次就认为"在里面"。
            // 判"离开"要连续 6 次、且每次间隔 ≥4 秒（约 30 秒），
            // 因为 poll 与 alarm 会在一两秒内连打两次，不能让它把计时清零。
            boolean sticky = Prefs.sticky(c);
            int miss = Prefs.stickyMiss(c);
            if (raw) {
                if (!sticky) Diag.log(c, "[" + source + "] 判定进入监控应用");
                sticky = true;
                miss = 0;
                Prefs.setLastMissAt(c, 0L);
            } else {
                if (now - Prefs.lastMissAt(c) >= 4000L) {
                    miss++;
                    Prefs.setLastMissAt(c, now);
                }
                if (sticky && miss >= 6) {
                    Diag.log(c, "[" + source + "] 判定离开监控应用");
                    sticky = false;
                    miss = 0;
                }
            }
            Prefs.setSticky(c, sticky);
            Prefs.setStickyMiss(c, miss);

            boolean screenOn = screenOn(c);
            long intervalMs = Math.max(1, Prefs.interval(c)) * 60_000L;
            long lastNotify = Prefs.lastNotify(c);
            long remain = -1L;

            if (!screenOn) {
                lastNotify = 0L;
            } else if (sticky) {
                if (lastNotify == 0L) {
                    lastNotify = now;
                    Diag.log(c, "开始计时：" + (d.pkg() == null ? "模拟" : d.pkg()));
                }
                long elapsed = now - lastNotify;
                if (elapsed >= intervalMs) {
                    lastNotify = now;
                    elapsed = 0L;
                    push(c, false);
                    Diag.note(c, "last_notify", System.currentTimeMillis());
                }
                remain = intervalMs - elapsed;
            } else {
                lastNotify = 0L;
            }
            Prefs.setLastNotify(c, lastNotify);

            Diag.heartbeat(c, d.evPkg, d.stPkg, sticky, remain, source);

            // 下一次该弹的时刻 -> 精确闹钟（进程死了也能被唤醒）
            long fireAt = lastNotify > 0L ? lastNotify + intervalMs : 0L;
            if (fireAt != Prefs.alarmAt(c)) {
                Prefs.setAlarmAt(c, fireAt);
                if (fireAt > 0L) {
                    AlarmScheduler.scheduleFire(c, fireAt);
                } else {
                    AlarmScheduler.cancelFire(c);
                }
            }
        } catch (Throwable t) {
            Diag.log(c, "tick 异常：" + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    /** 弹一条单词：通知 + 悬浮窗（有权限时） */
    public static void push(Context c, boolean force) {
        final WordDb db = WordDb.get(c);
        // 连续 3 条弹窗内不重复：排除最近两条
        Word w = db.pickReview(Prefs.books(c), Prefs.mineOnly(c),
                new String[]{Prefs.lastWord(c), Prefs.prevWord(c)});
        if (w == null) {
            Diag.log(c, "所选词书没有可复习的词了");
            if (force) {
                try {
                    android.widget.Toast.makeText(c, "所选词书已经全部掌握，换一本试试",
                            android.widget.Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {
                }
            }
            return;
        }
        db.bumpToday();
        db.bumpShown(w.id);
        db.logPush(w);
        Prefs.setPrevWord(c, Prefs.lastWord(c));
        Prefs.setLastWord(c, w.w);
        Diag.note(c, "last_notify", System.currentTimeMillis());
        Diag.log(c, (force ? "强制推送：" : "推送：") + w.w
                + "（第 " + (w.shown + 1) + " 次）");
        Notifier.showWord(c, w, db.todayCount());
        // 卡片自动消失、全程没点按钮 → 说明这个词不认识，收进「我的生词」
        boolean shown = Prefs.overlay(c) && OverlayWord.show(c, w, () -> {
            WordDb.get(c).markIgnored(w.id);
            Diag.log(c, "未点击，自动收起并加入我的生词：" + w.w);
        });
        Diag.log(c, "卡片" + (shown ? "已显示" : "未显示(无权限或失败)"));
        vibrate(c);
    }

    private static void vibrate(Context c) {
        try {
            android.os.Vibrator v = (android.os.Vibrator)
                    c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                v.vibrate(android.os.VibrationEffect.createOneShot(
                        220, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(220);
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 前台识别 ----------------

    static class Detection {
        String evPkg;
        String stPkg;

        String pkg() {
            return evPkg != null ? evPkg : stPkg;
        }
    }

    private static Detection detect(Context c, long now) {
        Detection d = new Detection();
        d.evPkg = byEvent(c, now);
        d.stPkg = byStats(c, now);
        return d;
    }

    /** 路径 1：前台切换事件（部分 ROM 会过滤） */
    private static String byEvent(Context c, long now) {
        try {
            android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager)
                    c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long from = Prefs.pollCursor(c);
            if (from == 0L || from > now) from = now - 60_000L;
            Prefs.setPollCursor(c, now);

            android.app.usage.UsageEvents events = usm.queryEvents(from, now);
            if (events == null) return null;
            android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
            String last = null;
            while (events.getNextEvent(e)) {
                if (e.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    last = e.getPackageName();
                }
            }
            return last;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 路径 2：谁最近一次被使用，谁就是前台（不依赖事件流） */
    private static String byStats(Context c, long now) {
        try {
            android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager)
                    c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            List<android.app.usage.UsageStats> list = usm.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 24 * 3600_000L, now);
            if (list == null || list.isEmpty()) return null;
            String pkg = null;
            long best = 0L;
            for (android.app.usage.UsageStats s : list) {
                long used = s.getLastTimeUsed();
                if (used > best && used <= now + 60_000L) {
                    best = used;
                    pkg = s.getPackageName();
                }
            }
            return pkg;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean screenOn(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    private static boolean contains(String[] arr, String v) {
        if (v == null) return false;
        for (String s : arr) {
            if (s != null && s.equals(v)) return true;
        }
        return false;
    }
}
