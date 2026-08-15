package com.palmistry.paymentdiagnostic;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQUEST_POST_NOTIFICATIONS = 2001;

    private TextView statusView;
    private TextView summaryView;
    private TextView batteryStatusView;
    private TextView notifyStatusView;
    private TextView recordsView;
    private EditText backendUrlInput;
    private EditText notifySecretInput;

    private final BroadcastReceiver recordsUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    private boolean receiverRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
        loadNotifyConfig();
        requestPostNotificationsIfNeeded();
        refreshUi();
        maybePromptBatteryWhitelist(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerRecordsUpdateReceiver();
    }

    @Override
    protected void onStop() {
        unregisterRecordsUpdateReceiver();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
        KeepAliveManager.start(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void registerRecordsUpdateReceiver() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(RecordUpdateNotifier.ACTION_RECORDS_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recordsUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(recordsUpdateReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void unregisterRecordsUpdateReceiver() {
        if (!receiverRegistered) {
            return;
        }
        unregisterReceiver(recordsUpdateReceiver);
        receiverRegistered = false;
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 24);

        TextView title = new TextView(this);
        title.setText(R.string.main_title);
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 0, 0, 24);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setPadding(0, 0, 0, 8);
        root.addView(statusView);

        summaryView = new TextView(this);
        summaryView.setTextSize(14);
        summaryView.setTextColor(Color.DKGRAY);
        summaryView.setPadding(0, 0, 0, 8);
        root.addView(summaryView);

        batteryStatusView = new TextView(this);
        batteryStatusView.setTextSize(13);
        batteryStatusView.setTextColor(Color.DKGRAY);
        batteryStatusView.setPadding(0, 0, 0, 8);
        root.addView(batteryStatusView);

        root.addView(createButton(R.string.request_battery_whitelist, v -> requestBatteryWhitelist()));

        TextView notifyTitle = new TextView(this);
        notifyTitle.setText(R.string.notify_config_title);
        notifyTitle.setTextSize(16);
        notifyTitle.setTextColor(Color.BLACK);
        notifyTitle.setPadding(0, 8, 0, 8);
        root.addView(notifyTitle);

        TextView backendLabel = new TextView(this);
        backendLabel.setText(R.string.notify_backend_url_label);
        backendLabel.setTextSize(14);
        backendLabel.setTextColor(Color.DKGRAY);
        root.addView(backendLabel);
        backendUrlInput = new EditText(this);
        backendUrlInput.setInputType(InputType.TYPE_CLASS_TEXT);
        backendUrlInput.setSingleLine(true);
        root.addView(backendUrlInput);

        TextView secretLabel = new TextView(this);
        secretLabel.setText(R.string.notify_secret_label);
        secretLabel.setTextSize(14);
        secretLabel.setTextColor(Color.DKGRAY);
        secretLabel.setPadding(0, 8, 0, 0);
        root.addView(secretLabel);
        notifySecretInput = new EditText(this);
        notifySecretInput.setInputType(InputType.TYPE_CLASS_TEXT);
        notifySecretInput.setSingleLine(true);
        root.addView(notifySecretInput);

        root.addView(createButton(R.string.save_notify_config, v -> saveNotifyConfig()));
        notifyStatusView = new TextView(this);
        notifyStatusView.setTextSize(13);
        notifyStatusView.setTextColor(Color.DKGRAY);
        notifyStatusView.setPadding(0, 8, 0, 16);
        root.addView(notifyStatusView);

        root.addView(createButton(R.string.open_listener_settings, this::openListenerSettings));
        root.addView(createButton(R.string.refresh_status, v -> refreshListenerAndUi(false)));
        root.addView(createButton(R.string.rescan_and_report, v -> refreshListenerAndUi(true)));
        root.addView(createButton(R.string.retry_last_report, v -> retryLastMatchedReport()));
        root.addView(createButton(R.string.clear_records, this::clearRecords));

        TextView listTitle = new TextView(this);
        listTitle.setText(R.string.recent_records_title);
        listTitle.setTextSize(16);
        listTitle.setTextColor(Color.BLACK);
        listTitle.setPadding(0, 24, 0, 8);
        root.addView(listTitle);

        recordsView = new TextView(this);
        recordsView.setTextSize(14);
        recordsView.setTextIsSelectable(true);
        recordsView.setLineSpacing(4, 1.1f);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(recordsView);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        root.addView(scrollView, scrollParams);

        return root;
    }

    private Button createButton(int textRes, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(textRes);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = 12;
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        return button;
    }

    private void loadNotifyConfig() {
        backendUrlInput.setText(NotifyConfig.getBackendUrl(this));
        notifySecretInput.setText(NotifyConfig.getNotifySecret(this));
    }

    private void saveNotifyConfig() {
        NotifyConfig.save(
                this,
                backendUrlInput.getText().toString(),
                notifySecretInput.getText().toString()
        );
        Toast.makeText(this, R.string.notify_config_saved, Toast.LENGTH_SHORT).show();
        KeepAliveManager.start(this);
        refreshUi();
    }

    private void openListenerSettings(View view) {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void clearRecords(View view) {
        NotificationStorage.clear(this);
        refreshUi();
    }

    private void refreshListenerAndUi(boolean reportMatched) {
        if (isNotificationListenerEnabled()) {
            AlipayNotificationListenerService.scanActiveNotificationsFromApp(this, reportMatched);
        }
        refreshUi();
        statusView.postDelayed(this::refreshUi, 600);
        statusView.postDelayed(this::refreshUi, 1500);
    }

    private void retryLastMatchedReport() {
        List<org.json.JSONObject> records = NotificationStorage.loadRecords(this);
        for (org.json.JSONObject record : records) {
            if (!record.optBoolean("matched", false) || !record.has("amount")) {
                continue;
            }
            PaymentNotifyClient.reportForce(
                    this,
                    record.optString("amount", ""),
                    record.optString("title", ""),
                    record.optString("body", ""),
                    record.optLong("time", System.currentTimeMillis())
            );
            Toast.makeText(this, "已重试上报最近一条收款记录", Toast.LENGTH_SHORT).show();
            statusView.postDelayed(this::refreshUi, 800);
            return;
        }
        Toast.makeText(this, "没有可重试的收款记录", Toast.LENGTH_SHORT).show();
    }

    private void refreshUi() {
        boolean enabled = isNotificationListenerEnabled();
        boolean connected = NotifyConfig.isListenerConnected(this);
        if (enabled && connected) {
            statusView.setText("监听状态：已开启且服务已连接");
            statusView.setTextColor(Color.parseColor("#1B7F3A"));
        } else if (enabled) {
            statusView.setText("监听状态：权限已开，但服务未连接（请点「重新扫描并上报」）");
            statusView.setTextColor(Color.parseColor("#C0392B"));
        } else {
            statusView.setText(R.string.status_disabled);
            statusView.setTextColor(Color.parseColor("#C0392B"));
        }

        int total = NotificationStorage.loadRecords(this).size();
        int matched = countMatchedRecords();
        summaryView.setText(getString(R.string.summary_format, total, matched));

        if (isIgnoringBatteryOptimizations()) {
            batteryStatusView.setText(R.string.battery_status_whitelisted);
            batteryStatusView.setTextColor(Color.parseColor("#1B7F3A"));
        } else {
            batteryStatusView.setText(R.string.battery_status_restricted);
            batteryStatusView.setTextColor(Color.parseColor("#C0392B"));
        }

        String lastStatus = NotifyConfig.getLastNotifyStatus(this);
        long lastAt = NotifyConfig.getLastNotifyAt(this);
        if (lastStatus.isEmpty()) {
            notifyStatusView.setText(getString(R.string.notify_backend_url_label) + ": "
                    + NotifyConfig.getBackendUrl(this));
        } else {
            String timeText = lastAt > 0L
                    ? new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(lastAt))
                    : "";
            notifyStatusView.setText(getString(R.string.notify_last_result, lastStatus + " " + timeText));
        }

        String display = NotificationStorage.formatForDisplay(this);
        recordsView.setText(display.isEmpty() ? getString(R.string.empty_records_hint) : display);
    }

    private int countMatchedRecords() {
        int count = 0;
        for (org.json.JSONObject record : NotificationStorage.loadRecords(this)) {
            if (record.optBoolean("matched", false)) {
                count++;
            }
        }
        return count;
    }

    private boolean isNotificationListenerEnabled() {
        return KeepAliveManager.isNotificationListenerEnabled(this);
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestBatteryWhitelist() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, R.string.battery_status_whitelisted, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isIgnoringBatteryOptimizations()) {
            Toast.makeText(this, R.string.battery_status_whitelisted, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "无法打开电池优化设置: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 首次进入且未加白名单时提示一次 */
    private void maybePromptBatteryWhitelist(boolean force) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        if (isIgnoringBatteryOptimizations()) {
            return;
        }
        if (!force && !isNotificationListenerEnabled()) {
            return;
        }
        requestBatteryWhitelist();
    }

    private void requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_POST_NOTIFICATIONS
        );
    }
}
