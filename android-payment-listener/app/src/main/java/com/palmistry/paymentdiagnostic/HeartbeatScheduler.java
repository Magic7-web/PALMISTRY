package com.palmistry.paymentdiagnostic;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

/**
 * 后台定时心跳：App 切到后台后仍保持上报，避免 H5 误判收款通道离线。
 */
public final class HeartbeatScheduler {

    private static final long HEARTBEAT_INTERVAL_MS = 10000L;

    private static HandlerThread workerThread;
    private static Handler workerHandler;
    private static Runnable heartbeatTask;
    private static boolean running;

    private HeartbeatScheduler() {
    }

    static synchronized void start(Context context) {
        if (!NotifyConfig.isConfigured(context)) {
            return;
        }
        ensureWorker();
        if (running) {
            PaymentNotifyClient.sendHeartbeat(context.getApplicationContext());
            return;
        }
        running = true;
        final Context appContext = context.getApplicationContext();
        heartbeatTask = new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }
                PaymentNotifyClient.sendHeartbeat(appContext);
                if (workerHandler != null) {
                    workerHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
                }
            }
        };
        PaymentNotifyClient.sendHeartbeat(appContext);
        workerHandler.postDelayed(heartbeatTask, HEARTBEAT_INTERVAL_MS);
    }

    static synchronized void stop() {
        running = false;
        if (workerHandler != null && heartbeatTask != null) {
            workerHandler.removeCallbacks(heartbeatTask);
        }
    }

    private static void ensureWorker() {
        if (workerThread != null && workerThread.isAlive() && workerHandler != null) {
            return;
        }
        workerThread = new HandlerThread("payment-heartbeat");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }
}
