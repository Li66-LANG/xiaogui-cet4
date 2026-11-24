package com.cet4.cards;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * 空壳无障碍服务：唯一用途是让 App 获得 TYPE_ACCESSIBILITY_OVERLAY 窗口权限。
 *
 * 为什么需要它：国产 ROM 会拦截后台应用的普通悬浮窗（addView 成功但不渲染），
 * 而由无障碍框架托管的覆盖层不受这个限制，是唯一能真正浮在应用上面的方式。
 *
 * 不读取屏幕内容（canRetrieveWindowContent=false），也不监听具体事件。
 */
public class WordAccessibility extends AccessibilityService {

    private static WordAccessibility inst;

    public static boolean ready() {
        return inst != null;
    }

    public static WordAccessibility get() {
        return inst;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        inst = this;
        Diag.log(this, "无障碍服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不使用
    }

    @Override
    public void onInterrupt() {
        // 不使用
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        inst = null;
        Diag.log(this, "无障碍服务已断开");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        inst = null;
        super.onDestroy();
    }
}
