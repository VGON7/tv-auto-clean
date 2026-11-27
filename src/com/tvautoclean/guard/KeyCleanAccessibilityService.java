package com.tvautoclean.guard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import java.util.List;

/**
 * Receives remote-control key events before the foreground app, so a dedicated
 * (often otherwise dead) TV button can start a cleanup from any screen.
 *
 * It never reads window content; the only capability requested is key filtering.
 */
public class KeyCleanAccessibilityService extends AccessibilityService {
    public static final String ACTION_KEY_CAPTURED =
            "com.tvautoclean.guard.action.KEY_CAPTURED";
    public static final String EXTRA_KEY_CODE = "key_code";

    private static final long CAPTURE_TIMEOUT_MS = 15_000L;
    private static final long TRIGGER_THROTTLE_MS = 800L;

    private static volatile KeyCleanAccessibilityService instance;
    private static volatile boolean captureArmed;
    private static volatile long captureDeadline;

    private volatile int savedKey = -1;
    private long lastTriggerAt;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        savedKey = Prefs.getCleanKeyCode(this);
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(info);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Key filtering does not need window events.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) {
            return false;
        }
        int code = event.getKeyCode();

        if (captureArmed) {
            if (SystemClock.uptimeMillis() > captureDeadline) {
                captureArmed = false;
            } else if (event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0
                    && !KeyNames.isDisallowed(code)) {
                captureArmed = false;
                Intent captured = new Intent(ACTION_KEY_CAPTURED);
                captured.setPackage(getPackageName());
                captured.putExtra(EXTRA_KEY_CODE, code);
                sendBroadcast(captured);
                return true;
            }
        }

        if (savedKey > 0 && code == savedKey) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastTriggerAt >= TRIGGER_THROTTLE_MS) {
                    lastTriggerAt = now;
                    GuardService.requestCleanNow(this, true);
                }
            }
            // Swallow both down and up so the key does not also reach the TV app.
            return true;
        }

        final String appPackage = Prefs.getAppForKey(this, code);
        if (appPackage != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                launchApp(appPackage);
            }
            return true;
        }
        return false;
    }

    private void launchApp(String pkg) {
        String label = pkg;
        try {
            PackageManager pm = getPackageManager();
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                CharSequence name = pm.getApplicationLabel(info);
                if (name != null && name.length() > 0) {
                    label = name.toString();
                }
            } catch (Throwable ignored) {
            }
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) {
                launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            }
            if (launch == null) {
                Toast.makeText(this, getString(R.string.toast_app_unavailable, label),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
        } catch (Throwable t) {
            Toast.makeText(this, getString(R.string.toast_app_launch_failed, label),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean armCapture() {
        if (instance == null) {
            return false;
        }
        captureArmed = true;
        captureDeadline = SystemClock.uptimeMillis() + CAPTURE_TIMEOUT_MS;
        return true;
    }

    public static void cancelCapture() {
        captureArmed = false;
    }

    public static void setKey(int keyCode) {
        KeyCleanAccessibilityService service = instance;
        if (service != null) {
            service.savedKey = keyCode;
        }
    }

    public static boolean isConnected() {
        return instance != null;
    }

    /** Whether the user has enabled this accessibility service in system settings. */
    public static boolean isEnabled(Context context) {
        if (instance != null) {
            return true;
        }
        try {
            AccessibilityManager manager = (AccessibilityManager) context.getSystemService(
                    Context.ACCESSIBILITY_SERVICE);
            if (manager == null || !manager.isEnabled()) {
                return false;
            }
            List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if (services == null) {
                return false;
            }
            ComponentName wanted = new ComponentName(
                    context.getPackageName(), KeyCleanAccessibilityService.class.getName());
            for (AccessibilityServiceInfo info : services) {
                if (info == null || info.getResolveInfo() == null
                        || info.getResolveInfo().serviceInfo == null) {
                    continue;
                }
                ComponentName name = new ComponentName(
                        info.getResolveInfo().serviceInfo.packageName,
                        info.getResolveInfo().serviceInfo.name);
                if (wanted.equals(name)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        captureArmed = false;
        super.onDestroy();
    }
}
