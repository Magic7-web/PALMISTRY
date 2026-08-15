package com.palmistry.paymentdiagnostic;

import android.content.Context;

/**
 * 兼容旧调用：统一委托给 KeepAliveManager（前台 Service 保活）。
 */
public final class HeartbeatScheduler {

    private HeartbeatScheduler() {
    }

    static synchronized void start(Context context) {
        KeepAliveManager.start(context);
    }

    /** 不再停止保活，避免熄屏/后台时心跳中断 */
    static synchronized void stop() {
        // 保留空实现以兼容 AlipayNotificationListenerService 旧调用
    }
}
