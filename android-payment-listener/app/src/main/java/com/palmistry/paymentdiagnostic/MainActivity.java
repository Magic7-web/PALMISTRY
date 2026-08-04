package com.palmistry.paymentdiagnostic;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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
import java.util.Locale;

public class MainActivity extends Activity {

    private TextView statusView;
    private TextView summaryView;
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
        refreshUi();
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
        summaryView.setPadding(0, 0, 0, 16);
        root.addView(summaryView);

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
        root.addView(createButton(R.string.refresh_status, v -> refreshUi()));
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
        refreshUi();
    }

    private void openListenerSettings(View view) {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void clearRecords(View view) {
        NotificationStorage.clear(this);
        refreshUi();
    }

    private void refreshUi() {
        boolean enabled = isNotificationListenerEnabled();
        statusView.setText(enabled ? R.string.status_enabled : R.string.status_disabled);
        statusView.setTextColor(enabled ? Color.parseColor("#1B7F3A") : Color.parseColor("#C0392B"));

        int total = NotificationStorage.loadRecords(this).size();
        int matched = countMatchedRecords();
        summaryView.setText(getString(R.string.summary_format, total, matched));

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
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        String pkg = getPackageName();
        for (String name : enabled.split(":")) {
            if (name.contains(pkg)) {
                return true;
            }
        }
        return false;
    }
}
