package com.cet4.cards;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationManagerCompat;

/**
 * 常驻服务：只负责"高频轮询"这一件事，真正的判断和弹窗都在 {@link Engine} 里。
 *
 * 为什么要这么拆：国产 ROM 会把后台进程冻结，进程内定时器随之停摆。
 * 现在即使本服务被杀，系统闹钟也会直接调用 Engine，功能不受影响。
 */
public class MonitorService extends Service {

    public static final String ACTION_START = "com.cet4.cards.action.START";
    public static final String ACTION_STOP = "com.cet4.cards.action.STOP";
    public static final String ACTION_TICK = "com.cet4.cards.action.TICK";

    private static final long POLL_MS = 5000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private String lastSvcText = "";

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            Engine.tick(MonitorService.this, "poll");
            updateServiceNotification();
            if (running) handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Notifier.ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            Prefs.setEnabled(this, false);
            AlarmScheduler.cancelAll(this);
            OverlayWord.dismiss();
            stopSelf();
            return START_NOT_STICKY;
        }

        Diag.note(this, "svc_start", System.currentTimeMillis());
        startForeground(Notifier.ID_SERVICE, Notifier.serviceNotification(
                this, getString(R.string.svc_title), getString(R.string.svc_waiting)));

        if (!running) {
            running = true;
            Diag.log(this, "服务启动");
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } else if (ACTION_TICK.equals(action)) {
            handler.removeCallbacks(ticker);
            Engine.tick(this, "tick");
            updateServiceNotification();
            if (running) handler.postDelayed(ticker, POLL_MS);
        }

        AlarmScheduler.scheduleResync(this);
        return START_STICKY;
    }

    private void updateServiceNotification() {
        boolean inApp = Prefs.sticky(this);
        String text = inApp
                ? getString(R.string.svc_active2_fmt, WordDb.get(this).todayCount())
                : getString(R.string.svc_waiting_desc);
        if (text.equals(lastSvcText)) return;
        lastSvcText = text;
        try {
            NotificationManagerCompat.from(this).notify(Notifier.ID_SERVICE,
                    Notifier.serviceNotification(this, getString(R.string.svc_title), text));
        } catch (Throwable ignored) {
        }
    }

    /** 立即弹一条（首页测试按钮） */
    public static void testNow(Context c) {
        Engine.push(c, true);
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        Diag.log(this, "服务销毁");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
