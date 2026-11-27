package com.tvautoclean.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets the user bind one remote key per installed app. Pressing that key
 * anywhere launches the app through the accessibility service.
 */
public class AppKeyActivity extends Activity {
    private static final long CAPTURE_TIMEOUT_MS = 15_000L;

    private TextView tvMappedCount;
    private TextView tvListCount;
    private TextView tvServiceStatus;
    private TextView tvServiceAction;
    private TextView tvLoading;
    private TextView btnClearAll;
    private LinearLayout appContainer;
    private View rowService;
    private View captureOverlay;
    private TextView tvCaptureTitle;
    private TextView tvCaptureHint;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppEntry> entries = new ArrayList<AppEntry>();
    private final Map<String, TextView> keyViews = new HashMap<String, TextView>();
    private final Map<String, String> labels = new HashMap<String, String>();
    private Map<Integer, String> keyMap = new LinkedHashMap<Integer, String>();

    private boolean capturing;
    private String capturePackage;
    private String captureLabel;
    private boolean receiverRegistered;

    private static final class AppEntry {
        String label;
        String packageName;
        boolean system;
        Drawable icon;
    }

    private final Runnable captureTimeout = new Runnable() {
        @Override
        public void run() {
            if (!capturing) {
                return;
            }
            cancelCapture();
            UiUtils.toast(AppKeyActivity.this, getString(R.string.key_capture_timeout));
        }
    };

    private final BroadcastReceiver keyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && KeyCleanAccessibilityService.ACTION_KEY_CAPTURED.equals(
                    intent.getAction())) {
                handleCapturedKey(intent.getIntExtra(
                        KeyCleanAccessibilityService.EXTRA_KEY_CODE, -1));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_keys);

        tvMappedCount = (TextView) findViewById(R.id.tvMappedCount);
        tvListCount = (TextView) findViewById(R.id.tvListCount);
        tvServiceStatus = (TextView) findViewById(R.id.tvServiceStatus);
        tvServiceAction = (TextView) findViewById(R.id.tvServiceAction);
        tvLoading = (TextView) findViewById(R.id.tvLoading);
        btnClearAll = (TextView) findViewById(R.id.btnClearAll);
        appContainer = (LinearLayout) findViewById(R.id.appContainer);
        rowService = findViewById(R.id.rowService);
        captureOverlay = findViewById(R.id.captureOverlay);
        tvCaptureTitle = (TextView) findViewById(R.id.tvCaptureTitle);
        tvCaptureHint = (TextView) findViewById(R.id.tvCaptureHint);

        keyMap = Prefs.getAppKeyMap(this);
        updateCounts();
        updateServiceStatus();

        UiUtils.attachFocusScale(findViewById(R.id.btnBack), 1.05f);
        UiUtils.attachFocusScale(rowService, 1.01f);
        UiUtils.attachFocusScale(btnClearAll, 1.02f);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        rowService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });
        btnClearAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearAll();
            }
        });

        tvLoading.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppEntry> loaded = loadApps();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        entries.clear();
                        entries.addAll(loaded);
                        renderApps();
                        tvLoading.setVisibility(View.GONE);
                        tvListCount.setText(getString(
                                R.string.app_keys_list_count, entries.size()));
                        if (entries.isEmpty()) {
                            tvLoading.setVisibility(View.VISIBLE);
                            tvLoading.setText(R.string.app_keys_empty);
                        }
                        findViewById(R.id.btnBack).requestFocus();
                    }
                });
            }
        }, "app-key-list-loader").start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            try {
                IntentFilter filter = new IntentFilter(
                        KeyCleanAccessibilityService.ACTION_KEY_CAPTURED);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(keyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(keyReceiver, filter);
                }
                receiverRegistered = true;
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        keyMap = Prefs.getAppKeyMap(this);
        for (String pkg : keyViews.keySet()) {
            bindKeyBadge(keyViews.get(pkg), pkg);
        }
        updateCounts();
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(keyReceiver);
            } catch (Throwable ignored) {
            }
            receiverRegistered = false;
        }
        cancelCapture();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        KeyCleanAccessibilityService.cancelCapture();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (capturing) {
            cancelCapture();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (capturing && event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK) {
                cancelCapture();
                return true;
            }
            if (KeyNames.isDisallowed(code)) {
                UiUtils.toast(this, getString(R.string.key_set_invalid));
                return true;
            }
            handleCapturedKey(code);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private List<AppEntry> loadApps() {
        List<AppEntry> result = new ArrayList<AppEntry>();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps;
        try {
            apps = pm.getInstalledApplications(0);
        } catch (Throwable t) {
            apps = Collections.emptyList();
        }
        if (apps == null) {
            return result;
        }
        String self = getPackageName();
        for (ApplicationInfo info : apps) {
            if (info == null || TextUtils.isEmpty(info.packageName)
                    || self.equals(info.packageName)) {
                continue;
            }
            if (launchIntentFor(pm, info.packageName) == null) {
                continue;
            }
            AppEntry entry = new AppEntry();
            entry.packageName = info.packageName;
            entry.system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            try {
                entry.label = pm.getApplicationLabel(info).toString();
            } catch (Throwable t) {
                entry.label = info.packageName;
            }
            if (TextUtils.isEmpty(entry.label)) {
                entry.label = info.packageName;
            }
            try {
                entry.icon = pm.getApplicationIcon(info);
            } catch (Throwable t) {
                entry.icon = getResources().getDrawable(R.drawable.ic_logo);
            }
            result.add(entry);
        }
        Collections.sort(result, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                if (a.system != b.system) {
                    return a.system ? 1 : -1;
                }
                int byName = a.label.compareToIgnoreCase(b.label);
                if (byName != 0) {
                    return byName;
                }
                return a.packageName.compareTo(b.packageName);
            }
        });
        return result;
    }

    private void renderApps() {
        appContainer.removeAllViews();
        keyViews.clear();
        labels.clear();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final AppEntry entry : entries) {
            View row = inflater.inflate(R.layout.item_app_key, appContainer, false);
            ImageView icon = (ImageView) row.findViewById(R.id.ivIcon);
            TextView name = (TextView) row.findViewById(R.id.tvName);
            TextView pkg = (TextView) row.findViewById(R.id.tvPackage);
            TextView key = (TextView) row.findViewById(R.id.tvShortcutKey);
            icon.setImageDrawable(entry.icon);
            name.setText(entry.label);
            pkg.setText(entry.packageName);
            keyViews.put(entry.packageName, key);
            labels.put(entry.packageName, entry.label);
            bindKeyBadge(key, entry.packageName);
            UiUtils.attachFocusScale(row, 1.01f);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startCapture(entry.packageName, entry.label);
                }
            });
            appContainer.addView(row);
        }
    }

    private void bindKeyBadge(TextView view, String pkg) {
        if (view == null) {
            return;
        }
        int key = keyForPackage(pkg);
        if (key > 0) {
            view.setText(KeyNames.label(key));
            view.setTextColor(getResources().getColor(R.color.accent));
        } else {
            view.setText(R.string.app_keys_unset);
            view.setTextColor(getResources().getColor(R.color.muted));
        }
    }

    private int keyForPackage(String pkg) {
        for (Map.Entry<Integer, String> entry : keyMap.entrySet()) {
            if (pkg.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private void startCapture(String pkg, String label) {
        if (!KeyCleanAccessibilityService.isEnabled(this)) {
            UiUtils.toast(this, getString(R.string.key_need_access));
            openAccessibilitySettings();
            return;
        }
        capturing = true;
        capturePackage = pkg;
        captureLabel = label;
        tvCaptureTitle.setText(getString(R.string.app_keys_capture_title, label));
        tvCaptureHint.setText(R.string.app_keys_capture_hint);
        captureOverlay.setVisibility(View.VISIBLE);
        captureOverlay.requestFocus();
        KeyCleanAccessibilityService.armCapture();
        handler.removeCallbacks(captureTimeout);
        handler.postDelayed(captureTimeout, CAPTURE_TIMEOUT_MS);
    }

    private void cancelCapture() {
        if (!capturing) {
            return;
        }
        capturing = false;
        capturePackage = null;
        captureLabel = null;
        handler.removeCallbacks(captureTimeout);
        KeyCleanAccessibilityService.cancelCapture();
        if (captureOverlay != null) {
            captureOverlay.setVisibility(View.GONE);
        }
    }

    private void handleCapturedKey(int code) {
        if (!capturing) {
            return;
        }
        if (KeyNames.isDisallowed(code)) {
            if (code == KeyEvent.KEYCODE_BACK) {
                cancelCapture();
            } else {
                UiUtils.toast(this, getString(R.string.key_set_invalid));
            }
            return;
        }
        if (code == Prefs.getCleanKeyCode(this)) {
            UiUtils.toast(this, getString(R.string.app_keys_conflict_clean));
            return;
        }
        String existing = keyMap.get(code);
        if (existing != null && !existing.equals(capturePackage)) {
            String existingLabel = labels.get(existing);
            if (TextUtils.isEmpty(existingLabel)) {
                existingLabel = existing;
            }
            UiUtils.toast(this, getString(R.string.app_keys_conflict_other, existingLabel));
            return;
        }

        String pkg = capturePackage;
        String label = captureLabel;
        Prefs.setAppKey(this, code, pkg);
        keyMap = Prefs.getAppKeyMap(this);
        bindKeyBadge(keyViews.get(pkg), pkg);
        updateCounts();
        capturing = false;
        capturePackage = null;
        captureLabel = null;
        handler.removeCallbacks(captureTimeout);
        KeyCleanAccessibilityService.cancelCapture();
        captureOverlay.setVisibility(View.GONE);
        UiUtils.toast(this, getString(R.string.app_keys_set_done,
                KeyNames.label(code), label));
    }

    private void updateCounts() {
        int count = keyMap.size();
        tvMappedCount.setText(getString(R.string.app_keys_count, count));
        btnClearAll.setClickable(count > 0);
        btnClearAll.setTextColor(getResources().getColor(
                count > 0 ? R.color.muted : R.color.muted2));
    }

    private void updateServiceStatus() {
        boolean enabled = KeyCleanAccessibilityService.isEnabled(this);
        tvServiceStatus.setText(enabled
                ? R.string.app_keys_service_on
                : R.string.app_keys_service_off);
        tvServiceStatus.setTextColor(getResources().getColor(
                enabled ? R.color.accent : R.color.muted));
        tvServiceAction.setText(enabled
                ? R.string.key_access_action_on
                : R.string.key_access_action_off);
        tvServiceAction.setTextColor(getResources().getColor(
                enabled ? R.color.accent : R.color.muted));
    }

    private void confirmClearAll() {
        if (Prefs.getAppKeyCount(this) == 0) {
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.app_keys_clear_all)
                .setMessage(R.string.app_keys_clear_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm_clear_ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Prefs.removeAppKeys(AppKeyActivity.this);
                                keyMap = Prefs.getAppKeyMap(AppKeyActivity.this);
                                for (String pkg : keyViews.keySet()) {
                                    bindKeyBadge(keyViews.get(pkg), pkg);
                                }
                                updateCounts();
                                UiUtils.toast(AppKeyActivity.this,
                                        getString(R.string.app_keys_cleared));
                            }
                        })
                .show();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent("android.settings.ACCESSIBILITY_TV_OEM_LINK"));
            return;
        } catch (Throwable ignored) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Intent details = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
                details.putExtra(Intent.EXTRA_COMPONENT_NAME, new ComponentName(
                        this, KeyCleanAccessibilityService.class));
                startActivity(details);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
            }
            UiUtils.toast(this, getString(R.string.key_need_access));
        }
    }

    private Intent launchIntentFor(PackageManager pm, String pkg) {
        Intent intent = pm.getLaunchIntentForPackage(pkg);
        if (intent == null) {
            try {
                intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            } catch (Throwable ignored) {
            }
        }
        return intent;
    }
}
