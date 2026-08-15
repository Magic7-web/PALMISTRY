package com.palmistry.paymentdiagnostic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * 前台 Service + WakeLock：熄屏/后台时维持进程并每 10 秒上报心跳。
 */
public class ForegroundKeepAliveService extends Service {

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "payment_keep_alive";
    private static final long HEARTBEAT_INTERVAL_MS = 10000L;
    private static final String WAKE_LOCK_TAG = "palmistry:payment_keep_alive";

    private HandlerThread workerThread;
    private Handler workerHandler;
    private Runnable heartbeatTask;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        acquireWakeLock();
        ensureWorker();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        promoteToForeground();
        startHeartbeatLoop();
        PaymentNotifyClient.sendHeartbeat(getApplicationContext());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopHeartbeatLoop();
        releaseWakeLock();
        if (workerThread != null) {
            workerThread.quitSafely();
            workerThread = null;
            workerHandler = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 用户划掉最近任务时尽量重新拉起
        if (NotifyConfig.isConfigured(this) && KeepAliveManager.isNotificationListenerEnabled(this)) {
            KeepAliveManager.startIfConfigured(getApplicationContext());
        }
        super.onTaskRemoved(rootIntent);
    }

    private void promoteToForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setContentTitle(getString(R.string.keep_alive_notification_title))
                .setContentText(getString(R.string.keep_alive_notification_text))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.keep_alive_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.keep_alive_channel_desc));
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private void ensureWorker() {
        if (workerThread != null && workerThread.isAlive() && workerHandler != null) {
            return;
        }
        workerThread = new HandlerThread("foreground-heartbeat");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }

    private void startHeartbeatLoop() {
        ensureWorker();
        if (heartbeatTask != null) {
            workerHandler.removeCallbacks(heartbeatTask);
        }
        heartbeatTask = new Runnable() {
            @Override
            public void run() {
                if (!NotifyConfig.isConfigured(getApplicationContext())) {
                    return;
                }
                PaymentNotifyClient.sendHeartbeat(getApplicationContext());
                if (workerHandler != null) {
                    workerHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
                }
            }
        };
        workerHandler.postDelayed(heartbeatTask, HEARTBEAT_INTERVAL_MS);
    }

    private void stopHeartbeatLoop() {
        if (workerHandler != null && heartbeatTask != null) {
            workerHandler.removeCallbacks(heartbeatTask);
        }
        heartbeatTask = null;
    }
}
