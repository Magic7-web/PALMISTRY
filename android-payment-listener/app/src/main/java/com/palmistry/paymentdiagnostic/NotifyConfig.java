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

    static String getLastNotifyStatus(Context context) {
        return prefs(context).getString(KEY_LAST_STATUS, "");
    }

    static long getLastNotifyAt(Context context) {
        return prefs(context).getLong(KEY_LAST_AT, 0L);
    }

    static boolean isConfigured(Context context) {
        return !getBackendUrl(context).isEmpty() && !getNotifySecret(context).isEmpty();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
