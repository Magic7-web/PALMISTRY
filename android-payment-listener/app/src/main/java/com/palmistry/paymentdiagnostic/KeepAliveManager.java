package com.palmistry.paymentdiagnostic;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/**
 * 统一启动前台保活与开机自启。
 */
public final class KeepAliveManager {

    private KeepAliveManager() {
    }

    /** 在已配置后端且通知监听权限开启时启动保活 */
    static void start(Context context) {
        Context appContext = context.getApplicationContext();
        if (!NotifyConfig.isConfigured(appContext)) {
            return;
        }
        if (!isNotificationListenerEnabled(appContext)) {
            return;
        }
        startForegroundService(appContext);
    }

    /** 强制尝试启动（配置保存、开机广播等场景） */
    static void startIfConfigured(Context context) {
        Context appContext = context.getApplicationContext();
        if (!NotifyConfig.isConfigured(appContext)) {
            return;
        }
        startForegroundService(appContext);
    }

    static void stop(Context context) {
        Context appContext = context.getApplicationContext();
        appContext.stopService(new Intent(appContext, ForegroundKeepAliveService.class));
    }

    static boolean isNotificationListenerEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners"
        );
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        String pkg = context.getPackageName();
        for (String name : enabled.split(":")) {
            if (name.contains(pkg)) {
                return true;
            }
        }
        return false;
    }

    private static void startForegroundService(Context appContext) {
        Intent intent = new Intent(appContext, ForegroundKeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent);
        } else {
            appContext.startService(intent);
        }
    }
}
