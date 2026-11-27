package com.tvautoclean.guard;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The only screen users see when they deliberately open the app. */
public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 31;

    private TextView tvStatusPill;
    private TextView tvAvailable;
    private TextView tvTotal;
    private TextView tvThreshold;
    private TextView tvLogCount;
    private TextView btnClean;
    private TextView btnSettings;
    private TextView tvHomeKeyStatus;
    private TextView tvHomeAppKeysStatus;
    private TextView tvEmptyTitle;
    private TextView tvEmptyDesc;
    private View llEmpty;
    private LinearLayout logContainer;
    private ProgressBar memBar;
    private MemoryRingView memoryRing;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private boolean receiverRegistered;

    private final BroadcastReceiver cleanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            int count = intent.getIntExtra(GuardService.EXTRA_COUNT, -1);
            long before = intent.getLongExtra(GuardService.EXTRA_BEFORE, 0L);
            long after = intent.getLongExtra(GuardService.EXTRA_AFTER, 0L);
            boolean manual = intent.getBooleanExtra(GuardService.EXTRA_MANUAL, false);
            boolean silent = intent.getBooleanExtra(GuardService.EXTRA_SILENT, false);
            resetCleanButton();
            if (!silent) {
                if (count > 0) {
                    if (after > before) {
                        UiUtils.toast(MainActivity.this, getString(
                                R.string.toast_clean_done,
                                count,
                                UiUtils.formatMbValue(after)));
                    } else {
                        UiUtils.toast(MainActivity.this, getString(
                                R.string.toast_clean_done_short, count));
                    }
                } else if (count == 0 && manual) {
                    UiUtils.toast(MainActivity.this, getString(R.string.toast_clean_none));
                } else if (count < 0 && manual) {
                    UiUtils.toast(MainActivity.this, getString(R.string.toast_clean_running));
                }
            }
            refreshAll();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatusPill = (TextView) findViewById(R.id.tvStatusPill);
        tvAvailable = (TextView) findViewById(R.id.tvAvailable);
        tvTotal = (TextView) findViewById(R.id.tvTotal);
        tvThreshold = (TextView) findViewById(R.id.tvThreshold);
        tvLogCount = (TextView) findViewById(R.id.tvLogCount);
        btnClean = (TextView) findViewById(R.id.btnClean);
        btnSettings = (TextView) findViewById(R.id.btnSettings);
        tvHomeKeyStatus = (TextView) findViewById(R.id.tvHomeKeyStatus);
        tvHomeAppKeysStatus = (TextView) findViewById(R.id.tvHomeAppKeysStatus);
        llEmpty = findViewById(R.id.llEmpty);
        tvEmptyTitle = (TextView) llEmpty.findViewById(R.id.tvEmptyTitle);
        tvEmptyDesc = (TextView) llEmpty.findViewById(R.id.tvEmptyDesc);
        logContainer = (LinearLayout) findViewById(R.id.logContainer);
        memBar = (ProgressBar) findViewById(R.id.memBar);
        memoryRing = (MemoryRingView) findViewById(R.id.memoryRing);

        UiUtils.attachFocusScale(btnClean, 1.03f);
        UiUtils.attachFocusScale(btnSettings, 1.03f);
        UiUtils.attachFocusScale(findViewById(R.id.cardCleanKey), 1.01f);
        UiUtils.attachFocusScale(findViewById(R.id.cardAppKeys), 1.01f);

        btnClean.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startManualClean();
            }
        });
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        findViewById(R.id.cardCleanKey).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, KeyCleanActivity.class));
            }
        });
        findViewById(R.id.cardAppKeys).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AppKeyActivity.class));
            }
        });

        GuardService.start(this);
        requestNotificationPermissionIfNeeded();
        btnClean.requestFocus();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshAll();
            }
        }, 900L);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            try {
                IntentFilter filter = new IntentFilter(GuardService.ACTION_CLEAN_DONE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(cleanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(cleanReceiver, filter);
                }
                receiverRegistered = true;
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!GuardService.isRunning()) {
            GuardService.start(this);
        }
        refreshAll();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshAll();
            }
        }, 700L);
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(cleanReceiver);
            } catch (Throwable ignored) {
            }
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void startManualClean() {
        btnClean.setText(R.string.cleaning);
        btnClean.setClickable(false);
        UiUtils.toast(this, getString(R.string.toast_clean_running));
        GuardService.requestCleanNow(this);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                resetCleanButton();
            }
        }, 12000L);
    }

    private void resetCleanButton() {
        if (btnClean == null) {
            return;
        }
        btnClean.setText(R.string.clean_now);
        btnClean.setClickable(true);
    }

    private void refreshAll() {
        updateMemory();
        renderLogs();
        updateServiceState();
        updateHomeShortcuts();
    }

    private void updateHomeShortcuts() {
        boolean serviceOn = KeyCleanAccessibilityService.isEnabled(this);
        int keyCode = Prefs.getCleanKeyCode(this);
        if (!serviceOn) {
            tvHomeKeyStatus.setText(R.string.home_clean_key_service_off);
            tvHomeKeyStatus.setTextColor(getResources().getColor(R.color.muted));
        } else if (keyCode > 0) {
            tvHomeKeyStatus.setText(KeyNames.label(keyCode));
            tvHomeKeyStatus.setTextColor(getResources().getColor(R.color.accent));
        } else {
            tvHomeKeyStatus.setText(R.string.home_clean_key_unset);
            tvHomeKeyStatus.setTextColor(getResources().getColor(R.color.muted));
        }

        int appCount = Prefs.getAppKeyCount(this);
        if (!serviceOn) {
            tvHomeAppKeysStatus.setText(R.string.home_app_keys_service_off);
            tvHomeAppKeysStatus.setTextColor(getResources().getColor(R.color.muted));
        } else if (appCount > 0) {
            tvHomeAppKeysStatus.setText(getString(R.string.home_app_keys_count, appCount));
            tvHomeAppKeysStatus.setTextColor(getResources().getColor(R.color.accent));
        } else {
            tvHomeAppKeysStatus.setText(R.string.home_app_keys_none);
            tvHomeAppKeysStatus.setTextColor(getResources().getColor(R.color.muted));
        }
    }

    private void updateMemory() {
        ActivityManager.MemoryInfo info = CleanEngine.readMemory(this);
        long total = info.totalMem;
        long avail = info.availMem;
        int usedPercent = 0;
        if (total > 0L) {
            usedPercent = (int) ((total - avail) * 100L / total);
            if (usedPercent < 0) {
                usedPercent = 0;
            }
            if (usedPercent > 100) {
                usedPercent = 100;
            }
        }
        memoryRing.setMemory(avail, total);
        tvAvailable.setText(UiUtils.formatMb(avail));
        tvTotal.setText(getString(R.string.memory_total, UiUtils.formatMb(total), usedPercent));
        memBar.setProgress(usedPercent);
        tvThreshold.setText(getString(R.string.memory_threshold, Prefs.thresholdPercent()));
    }

    private void updateServiceState() {
        boolean running = GuardService.isRunning();
        tvStatusPill.setText(running ? R.string.status_running : R.string.status_stopped);
        tvStatusPill.setBackgroundResource(running
                ? R.drawable.bg_status_pill
                : R.drawable.bg_status_pill_off);
        tvStatusPill.setTextColor(getResources().getColor(
                running ? R.color.accent : R.color.danger));
    }

    private void renderLogs() {
        List<CleanRecord> records = CleanLogStore.readToday(this);
        int totalApps = 0;
        for (CleanRecord record : records) {
            totalApps += record.count;
        }
        tvLogCount.setText(getString(R.string.today_summary, records.size(), totalApps));

        logContainer.removeAllViews();
        if (records.isEmpty()) {
            llEmpty.setVisibility(View.VISIBLE);
            return;
        }
        llEmpty.setVisibility(View.GONE);
        int limit = Math.min(records.size(), 30);
        for (int i = 0; i < limit; i++) {
            logContainer.addView(buildLogCard(records.get(i)));
        }
    }

    private View buildLogCard(CleanRecord record) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_log_item);
        int pad = UiUtils.dp(this, 16f);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = UiUtils.dp(this, 8f);
        card.setLayoutParams(cardParams);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setBackgroundResource(R.drawable.bg_log_dot);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                UiUtils.dp(this, 7f), UiUtils.dp(this, 7f));
        top.addView(dot, dotParams);

        TextView time = new TextView(this);
        time.setText(timeFormat.format(new Date(record.time)));
        time.setTextColor(getResources().getColor(R.color.text));
        time.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        UiUtils.setTextSizeSp(time, 16f);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        timeParams.leftMargin = UiUtils.dp(this, 9f);
        top.addView(time, timeParams);

        TextView count = new TextView(this);
        count.setText(getString(R.string.log_count, record.count));
        count.setTextColor(getResources().getColor(R.color.accent));
        UiUtils.setTextSizeSp(count, 12f);
        top.addView(count);

        card.addView(top);

        TextView memory = new TextView(this);
        if (record.afterMb > record.beforeMb + 4L) {
            memory.setText(getString(R.string.log_memory,
                    UiUtils.formatMbValue(record.beforeMb),
                    UiUtils.formatMbValue(record.afterMb)));
        } else if (record.afterMb > 0L) {
            memory.setText(getString(R.string.log_memory_same,
                    UiUtils.formatMbValue(record.afterMb)));
        } else {
            memory.setText(getString(R.string.log_memory_unknown,
                    UiUtils.formatMbValue(record.beforeMb)));
        }
        memory.setTextColor(getResources().getColor(R.color.muted));
        UiUtils.setTextSizeSp(memory, 12f);
        LinearLayout.LayoutParams memoryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        memoryParams.topMargin = UiUtils.dp(this, 6f);
        card.addView(memory, memoryParams);

        if (!record.apps.isEmpty()) {
            String names = TextUtils.join("、", record.apps);
            if (record.count > record.apps.size()) {
                names = getString(R.string.log_more, names, record.count);
            }
            TextView apps = new TextView(this);
            apps.setText(getString(R.string.log_apps, names));
            apps.setTextColor(getResources().getColor(R.color.muted));
            UiUtils.setTextSizeSp(apps, 12f);
            apps.setMaxLines(2);
            apps.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams appsParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            appsParams.topMargin = UiUtils.dp(this, 5f);
            card.addView(apps, appsParams);
        }
        return card;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (Prefs.wasNotificationAsked(this)) {
            return;
        }
        Prefs.markNotificationAsked(this);
        try {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.length > 0
                    && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                UiUtils.toast(this, getString(R.string.toast_permission_denied));
            }
        }
    }
}
