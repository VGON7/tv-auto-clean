package com.tvautoclean.guard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Small, dependency-free preference store. */
public final class Prefs {
    private static final String FILE = "tv_clean_prefs";
    private static final String KEY_WHITELIST = "whitelist";
    private static final String KEY_DEEP = "deep_clean_system";
    private static final String KEY_NOTIFICATION_ASKED = "notification_asked";
    private static final String KEY_CLEAN_KEY = "clean_key_code";
    private static final String KEY_APP_KEYS = "app_keys";

    private Prefs() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static Set<String> getWhitelist(Context context) {
        Set<String> stored = sp(context).getStringSet(KEY_WHITELIST, null);
        if (stored == null || stored.isEmpty()) {
            return new HashSet<String>();
        }
        return new HashSet<String>(stored);
    }

    public static boolean isWhitelisted(Context context, String pkg) {
        return getWhitelist(context).contains(pkg);
    }

    public static void setWhitelisted(Context context, String pkg, boolean enabled) {
        Set<String> list = getWhitelist(context);
        if (enabled) {
            list.add(pkg);
        } else {
            list.remove(pkg);
        }
        sp(context).edit().putStringSet(KEY_WHITELIST, list).apply();
    }

    public static boolean isDeepCleanEnabled(Context context) {
        return sp(context).getBoolean(KEY_DEEP, false);
    }

    public static void setDeepCleanEnabled(Context context, boolean enabled) {
        sp(context).edit().putBoolean(KEY_DEEP, enabled).apply();
    }

    public static boolean wasNotificationAsked(Context context) {
        return sp(context).getBoolean(KEY_NOTIFICATION_ASKED, false);
    }

    public static void markNotificationAsked(Context context) {
        sp(context).edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply();
    }

    /** Key code used for the one-press clean action, or -1 when unset. */
    public static int getCleanKeyCode(Context context) {
        return sp(context).getInt(KEY_CLEAN_KEY, -1);
    }

    public static void setCleanKeyCode(Context context, int keyCode) {
        sp(context).edit().putInt(KEY_CLEAN_KEY, keyCode).apply();
    }

    /** Map of remote key code to package name for one-press app launch. */
    public static Map<Integer, String> getAppKeyMap(Context context) {
        Map<Integer, String> result = new LinkedHashMap<Integer, String>();
        Set<String> stored = sp(context).getStringSet(KEY_APP_KEYS, null);
        if (stored == null) {
            return result;
        }
        for (String item : stored) {
            if (item == null) {
                continue;
            }
            int split = item.indexOf(':');
            if (split <= 0 || split >= item.length() - 1) {
                continue;
            }
            try {
                int keyCode = Integer.parseInt(item.substring(0, split));
                String pkg = item.substring(split + 1);
                if (keyCode > 0 && pkg.length() > 0) {
                    result.put(keyCode, pkg);
                }
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    public static String getAppForKey(Context context, int keyCode) {
        return getAppKeyMap(context).get(keyCode);
    }

    public static int getKeyForApp(Context context, String pkg) {
        if (pkg == null) {
            return -1;
        }
        for (Map.Entry<Integer, String> entry : getAppKeyMap(context).entrySet()) {
            if (pkg.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * A key can launch only one app, and an app can have only one key. Existing
     * entries for the same key or same package are replaced.
     */
    public static void setAppKey(Context context, int keyCode, String pkg) {
        if (keyCode <= 0 || pkg == null || pkg.length() == 0) {
            return;
        }
        Map<Integer, String> map = getAppKeyMap(context);
        map.remove(keyCode);
        for (Integer existingKey : new HashSet<Integer>(map.keySet())) {
            if (pkg.equals(map.get(existingKey))) {
                map.remove(existingKey);
            }
        }
        map.put(keyCode, pkg);

        Set<String> stored = new HashSet<String>();
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            stored.add(entry.getKey() + ":" + entry.getValue());
        }
        sp(context).edit().putStringSet(KEY_APP_KEYS, stored).apply();
    }

    public static void removeAppKeys(Context context) {
        sp(context).edit().remove(KEY_APP_KEYS).apply();
    }

    public static int getAppKeyCount(Context context) {
        return getAppKeyMap(context).size();
    }

    /** Percentage of total RAM that counts as "low memory". */
    public static int thresholdPercent() {
        return 10;
    }
}
