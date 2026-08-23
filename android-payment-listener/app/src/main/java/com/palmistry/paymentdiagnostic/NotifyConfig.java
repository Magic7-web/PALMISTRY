package com.palmistry.paymentdiagnostic;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 后端上报地址与密钥配置。
 */
public final class NotifyConfig {

    private static final String PREFS = "notify_config";
    private static final String KEY_BACKEND_URL = "backend_url";
    private static final String KEY_NOTIFY_SECRET = "notify_secret";
    private static final String KEY_LAST_STATUS = "last_notify_status";
    private static final String KEY_LAST_AT = "last_notify_at";
    private static final String KEY_LAST_SCAN = "last_scan_status";
    private static final String KEY_LAST_REPORT_SIGNATURE = "last_report_signature";
    private static final String KEY_LISTENER_CONNECTED = "listener_connected";

    private NotifyConfig() {
    }

    static String getBackendUrl(Context context) {
        return prefs(context).getString(
                KEY_BACKEND_URL,
                context.getString(R.string.default_notify_backend_url)
        ).trim();
    }

    static String getNotifySecret(Context context) {
        return prefs(context).getString(
                KEY_NOTIFY_SECRET,
                context.getString(R.string.default_notify_secret)
        ).trim();
    }

    static void save(Context context, String backendUrl, String notifySecret) {
        prefs(context).edit()
                .putString(KEY_BACKEND_URL, backendUrl.trim())
                .putString(KEY_NOTIFY_SECRET, notifySecret.trim())
                .apply();
    }

    static void saveLastNotifyResult(Context context, String status) {
        prefs(context).edit()
                .putString(KEY_LAST_STATUS, status)
                .putLong(KEY_LAST_AT, System.currentTimeMillis())
                .apply();
    }

    static void saveLastScanResult(Context context, String status) {
        prefs(context).edit()
                .putString(KEY_LAST_SCAN, status)
                .apply();
    }

    static String getLastScanResult(Context context) {
        return prefs(context).getString(KEY_LAST_SCAN, "");
    }

    static String getLastNotifyStatus(Context context) {
        return prefs(context).getString(KEY_LAST_STATUS, "");
    }

    static long getLastNotifyAt(Context context) {
        return prefs(context).getLong(KEY_LAST_AT, 0L);
    }

    static boolean isConfigured(Context context) {
        return !getBackendUrl(context).isEmpty() && !getNotifySecret(context).isEmpty();
    }

    static void saveListenerConnected(Context context, boolean connected) {
        prefs(context).edit().putBoolean(KEY_LISTENER_CONNECTED, connected).apply();
    }

    static boolean isListenerConnected(Context context) {
        return prefs(context).getBoolean(KEY_LISTENER_CONNECTED, false);
    }

    static boolean shouldReportAmount(Context context, String amountWithSymbol, long postedAt) {
        String signature = buildReportSignature(amountWithSymbol, postedAt);
        String last = prefs(context).getString(KEY_LAST_REPORT_SIGNATURE, "");
        return !signature.equals(last);
    }

    static void markReportAttempt(Context context, String amountWithSymbol, long postedAt) {
        prefs(context).edit()
                .putString(KEY_LAST_REPORT_SIGNATURE, buildReportSignature(amountWithSymbol, postedAt))
                .apply();
    }

    static void clearReportSignature(Context context) {
        prefs(context).edit().remove(KEY_LAST_REPORT_SIGNATURE).apply();
    }

    private static String buildReportSignature(String amountWithSymbol, long postedAt) {
        return amountWithSymbol + "@" + postedAt;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
