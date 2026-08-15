package com.palmistry.paymentdiagnostic;

import android.app.Notification;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;

public class AlipayNotificationListenerService extends NotificationListenerService {

    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";
    private static volatile boolean reportOnNextScan = false;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
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

    /** 主动扫描通知栏；reportMatched=true 时会尝试上报收款匹配通知 */
    static void scanActiveNotificationsFromApp(android.content.Context context, boolean reportMatched) {
        reportOnNextScan = reportMatched;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.content.ComponentName component = new android.content.ComponentName(
                    context,
                    AlipayNotificationListenerService.class
            );
            NotificationListenerService.requestRebind(component);
        }
    }

    private void scanActiveNotifications(boolean reportIfMatched) {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) {
                return;
            }
            for (StatusBarNotification sbn : active) {
                handleNotification(sbn, reportIfMatched);
            }
        } catch (SecurityException ignored) {
            // 监听尚未完全绑定时可能无权限
        }
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
        return ALIPAY_PACKAGE.equals(packageName)
                || packageName.startsWith("com.eg.android.AlipayGphone");
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
