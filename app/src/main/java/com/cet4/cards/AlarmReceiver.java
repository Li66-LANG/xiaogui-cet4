package com.cet4.cards;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 系统闹钟到点。
 * 注意：这里**不**去启动前台服务——Android 12+ 禁止从后台启动前台服务，
 * 那样闹钟会静默失败。改为直接在本进程里完成判断和弹窗。
 */
public class AlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_ALARM = "com.cet4.cards.action.ALARM";
    public static final String ACTION_TEST = "com.cet4.cards.action.TEST";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        try {
            if (ACTION_TEST.equals(action)) {
                Diag.log(context, "测试闹钟触发");
                Engine.push(context, true);
                return;
            }
            if (!Prefs.enabled(context)) return;
            Diag.log(context, "闹钟触发");
            Engine.tick(context, "alarm");
            AlarmScheduler.scheduleResync(context);
        } catch (Throwable t) {
            Diag.log(context, "闹钟处理异常：" + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }
}
