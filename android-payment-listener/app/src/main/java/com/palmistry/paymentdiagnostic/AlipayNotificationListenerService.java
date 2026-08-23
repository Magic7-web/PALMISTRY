package com.palmistry.paymentdiagnostic;

import android.app.Notification;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlipayNotificationListenerService extends NotificationListenerService {

    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";
    private static volatile boolean reportOnNextScan = false;
    private static volatile AlipayNotificationListenerService activeInstance;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        activeInstance = this;
        NotifyConfig.saveListenerConnected(getApplicationContext(), true);
        HeartbeatScheduler.start(getApplicationContext());
        PaymentNotifyClient.sendHeartbeat(getApplicationContext());
        scanActiveNotifications(reportOnNextScan);
        reportOnNextScan = false;
        RecordUpdateNotifier.notifyUpdated(getApplicationContext());
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (activeInstance == this) {
            activeInstance = null;
        }
        NotifyConfig.saveListenerConnected(getApplicationContext(), false);
        PaymentNotifyClient.sendHeartbeat(getApplicationContext());
        RecordUpdateNotifier.notifyUpdated(getApplicationContext());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            NotifyConfig.saveListenerConnected(getApplicationContext(), true);
            handleNotification(sbn, true);
        } catch (Exception error) {
            NotifyConfig.saveLastNotifyResult(
                    getApplicationContext(),
                    "处理通知异常: " + error.getMessage()
            );
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // 部分 vivo 机型只在通知被移除时才回调，补扫一次当前通知栏
        if (isAlipayPackage(sbn.getPackageName())) {
            scanActiveNotifications(true);
        }
    }

    /**
     * 主动扫描通知栏。已连接时直接扫，避免 requestRebind 在 vivo 上不触发 onListenerConnected。
     */
    static void scanActiveNotificationsFromApp(android.content.Context context, boolean reportMatched) {
        AlipayNotificationListenerService instance = activeInstance;
        if (instance != null) {
            instance.scanActiveNotifications(reportMatched);
            RecordUpdateNotifier.notifyUpdated(context.getApplicationContext());
            return;
        }

        reportOnNextScan = reportMatched;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.content.ComponentName component = new android.content.ComponentName(
                    context,
                    AlipayNotificationListenerService.class
            );
            NotificationListenerService.requestRebind(component);
        }
    }

    /** 前台 Service 定时调用：主动扫通知栏，弥补 onNotificationPosted 未回调 */
    static void periodicScanFromKeepAlive(android.content.Context context) {
        AlipayNotificationListenerService instance = activeInstance;
        if (instance == null) {
            return;
        }
        instance.scanActiveNotifications(true);
    }

    static boolean isListenerBound() {
        return activeInstance != null;
    }

    private void scanActiveNotifications(boolean reportIfMatched) {
        int total = 0;
        int alipay = 0;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) {
                saveScanDiagnostic(0, 0, "通知栏为空");
                return;
            }
            total = active.length;
            for (StatusBarNotification sbn : active) {
                if (isAlipayPackage(sbn.getPackageName())) {
                    alipay += 1;
                }
                handleNotification(sbn, reportIfMatched);
            }
            saveScanDiagnostic(total, alipay, "扫描完成");
        } catch (SecurityException error) {
            saveScanDiagnostic(total, alipay, "扫描失败: " + error.getMessage());
        } catch (Exception error) {
            saveScanDiagnostic(total, alipay, "扫描异常: " + error.getMessage());
        }
    }

    private void saveScanDiagnostic(int total, int alipayCount, String status) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
        NotifyConfig.saveLastScanResult(
                getApplicationContext(),
                time + " " + status + "（通知栏 " + total + " 条，支付宝 " + alipayCount + " 条）"
        );
    }

    private void handleNotification(StatusBarNotification sbn, boolean reportIfMatched) {
        if (!isAlipayPackage(sbn.getPackageName())) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) {
            return;
        }

        Bundle extras = notification.extras;
        if (extras == null) {
            extras = new Bundle();
        }

        String title = pickTitle(extras);
        String body = pickBody(extras);
        if (title.isEmpty() && body.isEmpty()) {
            body = pickFallbackBody(extras);
        }
        if (title.isEmpty() && body.isEmpty()) {
            title = "支付宝通知";
            body = "(系统未返回可读正文，key=" + sbn.getKey() + ")";
        }

        long postedAt = sbn.getPostTime() > 0L ? sbn.getPostTime() : System.currentTimeMillis();
        String notifyKey = sbn.getKey();
        NotificationStorage.PaymentMatch match = NotificationStorage.append(
                getApplicationContext(),
                title,
                body,
                postedAt,
                notifyKey
        );
        if (!match.appended && !match.updated) {
            return;
        }
        RecordUpdateNotifier.notifyUpdated(getApplicationContext());
        if (reportIfMatched && match.matched && match.amount != null && !match.amount.isEmpty()) {
            if (match.updated || NotifyConfig.shouldReportAmount(getApplicationContext(), match.amount, postedAt)) {
                PaymentNotifyClient.report(
                        getApplicationContext(),
                        match.amount,
                        title,
                        body,
                        postedAt
                );
            }
        }
    }

    private static boolean isAlipayPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        String normalized = packageName.toLowerCase(Locale.ROOT);
        return ALIPAY_PACKAGE.equals(packageName)
                || packageName.startsWith("com.eg.android.AlipayGphone")
                || normalized.contains("alipay");
    }

    private static String pickTitle(Bundle extras) {
        String title = safeText(extras.getCharSequence(Notification.EXTRA_TITLE));
        if (!title.isEmpty()) {
            return title;
        }
        return safeText(extras.getCharSequence("android.title"));
    }

    private static String pickBody(Bundle extras) {
        String bigText = safeText(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (!bigText.isEmpty()) {
            return bigText;
        }
        String text = safeText(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (!text.isEmpty()) {
            return text;
        }
        String subText = safeText(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        if (!subText.isEmpty()) {
            return subText;
        }
        String infoText = safeText(extras.getCharSequence(Notification.EXTRA_INFO_TEXT));
        if (!infoText.isEmpty()) {
            return infoText;
        }
        String androidText = safeText(extras.getCharSequence("android.text"));
        if (!androidText.isEmpty()) {
            return androidText;
        }
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0) {
            StringBuilder builder = new StringBuilder();
            for (CharSequence line : lines) {
                String part = safeText(line);
                if (part.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(part);
            }
            return builder.toString();
        }
        return "";
    }

    /** 部分机型支付宝只把文案放在自定义 extras 里 */
    private static String pickFallbackBody(Bundle extras) {
        StringBuilder builder = new StringBuilder();
        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            if (!(value instanceof CharSequence)) {
                continue;
            }
            String text = safeText((CharSequence) value);
            if (text.isEmpty() || text.length() > 200) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(text);
        }
        return builder.toString().trim();
    }

    private static String safeText(CharSequence value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().trim();
        return "null".equals(text) ? "" : text;
    }
}
