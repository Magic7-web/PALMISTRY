package com.palmistry.paymentdiagnostic;

import android.content.Context;
import android.content.Intent;

/**
 * 通知记录更新时的 app 内广播，供主界面自动刷新。
 */
public final class RecordUpdateNotifier {

    public static final String ACTION_RECORDS_UPDATED =
            "com.palmistry.paymentdiagnostic.RECORDS_UPDATED";

    private RecordUpdateNotifier() {
    }

    static void notifyUpdated(Context context) {
        Intent intent = new Intent(ACTION_RECORDS_UPDATED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
