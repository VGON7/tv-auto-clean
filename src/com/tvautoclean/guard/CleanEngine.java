package com.tvautoclean.guard;

import android.app.ActivityManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Uses Android's public background-process API. It never force-stops a foreground
 * app and never touches protected packages.
 */
public final class CleanEngine {
    private static final int MAX_LOG_NAMES = 8;

    public static final class Result {
        public final int count;
        public final List<String> names;
        public final long beforeMb;
        public final long afterMb;

        Result(int count, List<String> names, long beforeMb, long afterMb) {
            this.count = count;
            this.names = names;
            this.beforeMb = beforeMb;
            this.afterMb = afterMb;
        }
    }

    private CleanEngine() {
    }

    public static Result clean(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = context.getPackageManager();
        long beforeBytes = readMemory(context).availMem;

        Set<String> protectedPackages = new HashSet<String>();
        protectedPackages.add(context.getPackageName());
        protectedPackages.addAll(getHomePackages(pm));
        protectedPackages.addAll(getCurrentForegroundPackages(context));
        addVisibleProcessPackages(context, protectedPackages);
        protectedPackages.addAll(Prefs.getWhitelist(context));
        addDefaultInputMethod(context, protectedPackages);

        boolean deep = Prefs.isDeepCleanEnabled(context);
        List<String> labels = new ArrayList<String>();
        int count = 0;

        List<ApplicationInfo> installed;
        try {
            installed = pm.getInstalledApplications(0);
        } catch (Throwable t) {
            installed = Collections.emptyList();
        }

        for (ApplicationInfo info : installed) {
            if (info == null || TextUtils.isEmpty(info.packageName)) {
                continue;
            }
            String pkg = info.packageName;
            if (protectedPackages.contains(pkg)) {
                continue;
            }
            if (!info.enabled || (info.flags & ApplicationInfo.FLAG_STOPPED) != 0) {
                continue;
            }
            boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (system && !deep) {
                continue;
            }
            if (system && (isCoreSystemPackage(pkg)
                    || pm.getLaunchIntentForPackage(pkg) == null)) {
                continue;
            }
            try {
                am.killBackgroundProcesses(pkg);
                count++;
                if (labels.size() < MAX_LOG_NAMES) {
                    String label;
                    try {
                        label = pm.getApplicationLabel(info).toString();
                    } catch (Throwable t) {
                        label = pkg;
                    }
                    if (TextUtils.isEmpty(label)) {
                        label = pkg;
                    }
                    if (!labels.contains(label)) {
                        labels.add(label);
                    }
                }
            } catch (Throwable ignored) {
                // Some OEM firmware blocks this for certain packages.
            }
        }

        if (count > 0) {
            // Give the system a moment to reclaim the processes before measuring.
            SystemClock.sleep(900L);
        }
        long afterBytes = readMemory(context).availMem;
        sortLabels(labels);
        return new Result(count, labels, beforeBytes / (1024L * 1024L),
                afterBytes / (1024L * 1024L));
    }

    public static ActivityManager.MemoryInfo readMemory(Context context) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.getMemoryInfo(info);
            }
        } catch (Throwable ignored) {
        }
        return info;
    }

    public static long lowThresholdBytes(ActivityManager.MemoryInfo info) {
        long ratio = info.totalMem / 100L * Prefs.thresholdPercent();
        return Math.max(info.threshold, ratio);
    }

    public static boolean isLowMemory(ActivityManager.MemoryInfo info) {
        if (info.totalMem <= 0L) {
            return info.lowMemory;
        }
        return info.lowMemory || info.availMem < lowThresholdBytes(info);
    }

    private static Set<String> getHomePackages(PackageManager pm) {
        Set<String> result = new HashSet<String>();
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        try {
            List<ResolveInfo> list = pm.queryIntentActivities(home, 0);
            if (list != null) {
                for (ResolveInfo ri : list) {
                    if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                        result.add(ri.activityInfo.packageName);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /**
     * Optional accuracy: with usage-access permission the most recently resumed
     * activity is protected explicitly. killBackgroundProcesses never kills a
     * foreground process anyway, so this is a safety belt rather than a hard
     * requirement.
     */
    private static Set<String> getCurrentForegroundPackages(Context context) {
        Set<String> result = new HashSet<String>();
        if (!UsageAccess.hasAccess(context)) {
            return result;
        }
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(
                    Context.USAGE_STATS_SERVICE);
            if (usm == null) {
                return result;
            }
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 10L * 60L * 1000L, now);
            if (events == null) {
                return result;
            }
            UsageEvents.Event event = new UsageEvents.Event();
            String lastResume = null;
            String lastBackground = null;
            long lastResumeAt = 0L;
            long lastBackgroundAt = 0L;
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                // 1 == MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
                // 2 == MOVE_TO_BACKGROUND / ACTIVITY_PAUSED
                if (type == 1) {
                    lastResume = event.getPackageName();
                    lastResumeAt = event.getTimeStamp();
                } else if (type == 2) {
                    lastBackground = event.getPackageName();
                    lastBackgroundAt = event.getTimeStamp();
                }
            }
            if (lastResume != null && lastResumeAt >= lastBackgroundAt) {
                result.add(lastResume);
            } else if (lastBackground != null && lastBackgroundAt > lastResumeAt) {
                // The app has just gone to the background; no foreground app is
                // known from this short event window.
                result.clear();
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /**
     * Best-effort safety net for Android 5/6/7 where running processes are still
     * visible to third-party apps. On newer versions this normally returns only
     * our own process, which is harmless.
     */
    private static void addVisibleProcessPackages(Context context, Set<String> out) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(
                    Context.ACTIVITY_SERVICE);
            if (am == null) {
                return;
            }
            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    if (process == null || process.pkgList == null) {
                        continue;
                    }
                    if (process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                        for (String pkg : process.pkgList) {
                            if (!TextUtils.isEmpty(pkg)) {
                                out.add(pkg);
                            }
                        }
                    }
                }
            }
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty() && tasks.get(0) != null
                    && tasks.get(0).topActivity != null) {
                out.add(tasks.get(0).topActivity.getPackageName());
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addDefaultInputMethod(Context context, Set<String> out) {
        try {
            String ime = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (!TextUtils.isEmpty(ime) && ime.contains("/")) {
                out.add(ime.substring(0, ime.indexOf('/')));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Core packages are never touched, even in "deep" mode. Removing these can
     * break the TV's input, settings, or playback stack.
     */
    private static boolean isCoreSystemPackage(String pkg) {
        if (pkg.equals("android")
                || pkg.equals("com.android.systemui")
                || pkg.equals("com.android.settings")
                || pkg.equals("com.android.tv.settings")
                || pkg.equals("com.android.packageinstaller")
                || pkg.equals("com.google.android.packageinstaller")
                || pkg.equals("com.android.phone")
                || pkg.equals("com.android.shell")
                || pkg.equals("com.android.bluetooth")
                || pkg.equals("com.android.externalstorage")
                || pkg.equals("com.android.documentsui")
                || pkg.equals("com.android.vending")
                || pkg.equals("com.google.android.gms")
                || pkg.equals("com.google.android.gsf")
                || pkg.equals("com.google.android.tvlauncher")
                || pkg.equals("com.google.android.leanbacklauncher")) {
            return true;
        }
        return pkg.startsWith("com.android.providers.")
                || pkg.startsWith("com.android.server.")
                || pkg.startsWith("com.android.inputmethod.")
                || pkg.startsWith("com.google.android.inputmethod.")
                || pkg.startsWith("com.android.tv.")
                || pkg.startsWith("com.google.android.tv.");
    }

    private static void sortLabels(List<String> labels) {
        try {
            final Collator collator = Collator.getInstance(Locale.CHINA);
            Collections.sort(labels, collator);
        } catch (Throwable ignored) {
        }
    }
}
