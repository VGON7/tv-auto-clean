package com.tvautoclean.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Remote-friendly whitelist and background-permission settings. */
public class SettingsActivity extends Activity {
    private static final int REQUEST_USAGE = 22;

    private TextView tvSelectedTop;
    private TextView tvSelectedCount;
    private TextView tvFooterCount;
    private TextView tvLoading;
    private LinearLayout appContainer;
    private View rowUsage;
    private TextView tvUsageDesc;
    private TextView tvUsageAction;
    private View rowDeep;
    private TextView tvDeepDesc;
    private TextView tvDeepState;
    private View rowBattery;
    private TextView tvBatteryDesc;
    private TextView tvBatteryAction;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppEntry> entries = new ArrayList<AppEntry>();
    private Set<String> whitelist = new HashSet<String>();
    private boolean deepClean;

    private static final class AppEntry {
        String label;
        String packageName;
        boolean system;
        android.graphics.drawable.Drawable icon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        tvSelectedTop = (TextView) findViewById(R.id.tvSelectedTop);
        tvSelectedCount = (TextView) findViewById(R.id.tvSelectedCount);
        tvFooterCount = (TextView) findViewById(R.id.tvFooterCount);
        tvLoading = (TextView) findViewById(R.id.tvLoading);
        appContainer = (LinearLayout) findViewById(R.id.appContainer);
        rowUsage = findViewById(R.id.rowUsage);
        tvUsageDesc = (TextView) findViewById(R.id.tvUsageDesc);
        tvUsageAction = (TextView) findViewById(R.id.tvUsageAction);
        rowDeep = findViewById(R.id.rowDeep);
        tvDeepDesc = (TextView) findViewById(R.id.tvDeepDesc);
        tvDeepState = (TextView) findViewById(R.id.tvDeepState);
        rowBattery = findViewById(R.id.rowBattery);
        tvBatteryDesc = (TextView) findViewById(R.id.tvBatteryDesc);
        tvBatteryAction = (TextView) findViewById(R.id.tvBatteryAction);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnDone).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        UiUtils.attachFocusScale(rowUsage, 1.01f);
        UiUtils.attachFocusScale(rowDeep, 1.01f);
        UiUtils.attachFocusScale(rowBattery, 1.01f);
        UiUtils.attachFocusScale(findViewById(R.id.btnBack), 1.05f);
        UiUtils.attachFocusScale(findViewById(R.id.btnDone), 1.03f);

        rowUsage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUsageAccess();
            }
        });
        rowDeep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDeepClean();
            }
        });
        rowBattery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBatterySettings();
            }
        });

        whitelist = Prefs.getWhitelist(this);
        deepClean = Prefs.isDeepCleanEnabled(this);
        updateSettingsRows();
        updateSelectionCount();

        tvLoading.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppEntry> loaded;
                try {
                    loaded = loadApps();
                } catch (Throwable t) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            tvLoading.setText(R.string.load_failed);
                        }
                    });
                    return;
                }
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
                        findViewById(R.id.btnBack).requestFocus();
                    }
                });
            }
        }, "app-list-loader").start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSettingsRows();
    }

    private void updateSettingsRows() {
        boolean usage = UsageAccess.hasAccess(this);
        tvUsageDesc.setText(usage ? R.string.usage_desc_on : R.string.usage_desc_off);
        tvUsageAction.setText(usage ? R.string.usage_opened : R.string.usage_open);
        tvUsageAction.setTextColor(getResources().getColor(
                usage ? R.color.accent : R.color.muted));

        tvDeepDesc.setText(deepClean ? R.string.deep_desc_on : R.string.deep_desc_off);
        tvDeepState.setText(deepClean ? R.string.deep_on : R.string.deep_off);
        tvDeepState.setTextColor(getResources().getColor(
                deepClean ? R.color.accent : R.color.muted));

        boolean battery = isIgnoringBatteryOptimizations();
        tvBatteryDesc.setText(battery ? R.string.battery_desc_on : R.string.battery_desc_off);
        tvBatteryAction.setText(battery ? R.string.battery_opened : R.string.battery_open);
        tvBatteryAction.setTextColor(getResources().getColor(
                battery ? R.color.accent : R.color.muted));
    }

    private void updateSelectionCount() {
        String text = getString(R.string.selected_count, whitelist.size());
        tvSelectedTop.setText(text);
        tvSelectedCount.setText(text);
        tvFooterCount.setText(text);
    }

    private List<AppEntry> loadApps() {
        List<AppEntry> result = new ArrayList<AppEntry>();
        android.content.pm.PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> apps;
        try {
            apps = pm.getInstalledApplications(0);
        } catch (Throwable t) {
            apps = Collections.emptyList();
        }
        String self = getPackageName();
        if (apps == null) {
            return result;
        }
        for (android.content.pm.ApplicationInfo info : apps) {
            if (info == null || TextUtils.isEmpty(info.packageName)
                    || self.equals(info.packageName)) {
                continue;
            }
            boolean system = (info.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    && (info.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (system && pm.getLaunchIntentForPackage(info.packageName) == null) {
                continue;
            }
            AppEntry entry = new AppEntry();
            entry.packageName = info.packageName;
            entry.system = system;
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
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final AppEntry entry : entries) {
            View row = inflater.inflate(R.layout.item_app, appContainer, false);
            ImageView icon = (ImageView) row.findViewById(R.id.ivIcon);
            TextView name = (TextView) row.findViewById(R.id.tvName);
            TextView pkg = (TextView) row.findViewById(R.id.tvPackage);
            TextView sys = (TextView) row.findViewById(R.id.tvSystem);
            final ImageView check = (ImageView) row.findViewById(R.id.ivCheck);

            icon.setImageDrawable(entry.icon);
            name.setText(entry.label);
            pkg.setText(entry.packageName);
            sys.setVisibility(entry.system ? View.VISIBLE : View.GONE);
            bindCheck(check, whitelist.contains(entry.packageName));
            UiUtils.attachFocusScale(row, 1.01f);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean selected = !whitelist.contains(entry.packageName);
                    if (selected) {
                        whitelist.add(entry.packageName);
                    } else {
                        whitelist.remove(entry.packageName);
                    }
                    Prefs.setWhitelisted(SettingsActivity.this, entry.packageName, selected);
                    bindCheck(check, selected);
                    updateSelectionCount();
                }
            });
            appContainer.addView(row);
        }
    }

    private void bindCheck(ImageView check, boolean selected) {
        check.setImageResource(R.drawable.ic_check);
        check.setBackgroundResource(selected
                ? R.drawable.bg_checkbox_checked
                : R.drawable.bg_checkbox_unchecked);
        check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    private void openUsageAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                UiUtils.toast(this, getString(R.string.toast_usage_unavailable));
            } catch (ActivityNotFoundException ignored) {
                UiUtils.toast(this, getString(R.string.toast_usage_unavailable));
            }
        }
    }

    private void toggleDeepClean() {
        if (!deepClean) {
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(R.string.deep_title)
                    .setMessage(R.string.confirm_deep)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm_deep_ok,
                            new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface dialog, int which) {
                                    deepClean = true;
                                    Prefs.setDeepCleanEnabled(SettingsActivity.this, true);
                                    updateSettingsRows();
                                }
                            })
                    .show();
        } else {
            deepClean = false;
            Prefs.setDeepCleanEnabled(this, false);
            updateSettingsRows();
        }
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private void openBatterySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (ActivityNotFoundException e2) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (ActivityNotFoundException ignored) {
                }
                UiUtils.toast(this, getString(R.string.toast_battery_unavailable));
            }
        } catch (Throwable t) {
            UiUtils.toast(this, getString(R.string.toast_battery_unavailable));
        }
    }
}
