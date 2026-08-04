package com.palmistry.paymentdiagnostic;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;

public class AlipayNotificationListenerService extends NotificationListenerService {

    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!ALIPAY_PACKAGE.equals(sbn.getPackageName())) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) {
            return;
        }

        Bundle extras = notification.extras;
        if (extras == null) {
            return;
        }

        String title = safeText(extras.getCharSequence(Notification.EXTRA_TITLE));
        String body = pickBody(extras);

        if (title.isEmpty() && body.isEmpty()) {
            return;
        }

        long postedAt = sbn.getPostTime() > 0L ? sbn.getPostTime() : System.currentTimeMillis();
        NotificationStorage.PaymentMatch match =
                NotificationStorage.append(getApplicationContext(), title, body, postedAt);
        RecordUpdateNotifier.notifyUpdated(getApplicationContext());
        if (match.matched && match.amount != null && !match.amount.isEmpty()) {
            PaymentNotifyClient.report(
                    getApplicationContext(),
                    match.amount,
                    title,
                    body,
                    postedAt
            );
        }
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

    private static String safeText(CharSequence value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().trim();
        return "null".equals(text) ? "" : text;
    }
}
