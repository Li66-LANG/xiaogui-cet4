package com.cet4.cards;

import android.content.Context;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 把未捕获异常写进诊断日志。
 * 闪退后重新打开 App，点「复制诊断信息」就能看到崩在哪一行。
 */
public class Crash implements Thread.UncaughtExceptionHandler {

    private static boolean installed = false;

    private final Context ctx;
    private final Thread.UncaughtExceptionHandler prev;

    private Crash(Context c, Thread.UncaughtExceptionHandler prev) {
        this.ctx = c;
        this.prev = prev;
    }

    public static void install(Context c) {
        if (installed) return;
        installed = true;
        Thread.setDefaultUncaughtExceptionHandler(
                new Crash(c.getApplicationContext(), Thread.getDefaultUncaughtExceptionHandler()));
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String[] lines = sw.toString().split("\n");

            StringBuilder sb = new StringBuilder();
            int keep = 0;
            for (String line : lines) {
                if (keep >= 7) break;
                String s = line.trim();
                if (s.length() == 0) continue;
                sb.append(s).append(" | ");
                keep++;
            }
            Diag.put(ctx, "crash", System.currentTimeMillis() + " " + sb);
            Diag.log(ctx, "崩溃：" + e.getClass().getSimpleName());
        } catch (Throwable ignored) {
        }
        if (prev != null) prev.uncaughtException(t, e);
    }
}
