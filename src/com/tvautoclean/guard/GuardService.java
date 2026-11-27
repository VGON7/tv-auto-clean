package com.tvautoclean.guard;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A lightweight foreground service that has no UI. It wakes up periodically,
 * checks available RAM, and asks Android to kill background processes when RAM
 * is low. The foreground notification is deliberately low importance.
 */
public class GuardService extends Service {
    public static final String ACTION_CLEAN_NOW = "com.tvautoclean.guard.action.CLEAN_NOW";
    public static final String ACTION_CLEAN_DONE = "com.tvautoclean.guard.action.CLEAN_DONE";
    public static final String EXTRA_COUNT = "count";
    public static final String EXTRA_BEFORE = "before";
    public static final String EXTRA_AFTER = "after";
    public static final String EXTRA_MANUAL = "manual";
    public static final String EXTRA_SILENT = "silent";
    public static final String EXTRA_SHOW_TOAST = "show_toast";

    private static final String CHANNEL_ID = "guard";
    private static final int NOTIFICATION_ID = 1001;
    private static final long CHECK_INTERVAL_MS = 30L * 1000L;
    private static final long FIRST_CHECK_DELAY_MS = 45L * 1000L;
    private static final long CLEAN_COOLDOWN_MS = 3L * 60L * 1000L;

    private static volatile boolean running = false;
    private static volatile long lastCleanAt = 0L;

    private final AtomicBoolean cleaning = new AtomicBoolean(false);
    private Handler handler;
    private ExecutorService executor;
    private PowerManager powerManager;
    private int lastPruneDay = -1;

    private final Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            int day = currentDay();
            if (day != lastPruneDay) {
                lastPruneDay = day;
                try {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            CleanLogStore.prune(GuardService.this);
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
            requestClean(false, false);
            if (handler != null) {
                handler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        }
    };

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context context) {
        try {
            Intent intent = new Intent(context, GuardService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable ignored) {
            // OEM firmware can block background starts. The UI will show the
            // service as stopped and the user can open the app to retry.
        }
    }

    public static void requestCleanNow(Context context) {
        requestCleanNow(context, false);
    }

    /**
     * @param showToast true for the remote-key action: the service itself shows
     *                  a result toast, even when no Activity is open.
     */
    public static void requestCleanNow(Context context, boolean showToast) {
        try {
            Intent intent = new Intent(context, GuardService.class);
            intent.setAction(ACTION_CLEAN_NOW);
            intent.putExtra(EXTRA_SHOW_TOAST, showToast);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        handler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        createNotificationChannel();
        try {
            startForeground(NOTIFICATION_ID, buildNotification(currentNotificationText()));
        } catch (Throwable t) {
            running = false;
            stopSelf();
            return;
        }

        lastPruneDay = currentDay();
        handler.postDelayed(checkRunnable, FIRST_CHECK_DELAY_MS);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                CleanLogStore.prune(GuardService.this);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CLEAN_NOW.equals(intent.getAction())) {
            requestClean(true, intent.getBooleanExtra(EXTRA_SHOW_TOAST, false));
        } else {
            updateNotification();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }

    private int currentDay() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR);
    }

    private void requestClean(boolean manual, boolean toastResult) {
        if (!cleaning.compareAndSet(false, true)) {
            if (toastResult) {
                showToast(getString(R.string.toast_clean_running));
            } else if (manual) {
                broadcastResult(-1, 0L, 0L, true, false);
            }
            return;
        }
        final boolean isManual = manual;
        final boolean toastAfter = toastResult;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    performClean(isManual, toastAfter);
                } finally {
                    cleaning.set(false);
                }
            }
        });
    }

    private void performClean(boolean manual, boolean toastAfter) {
        if (!manual) {
            if (powerManager != null && !powerManager.isInteractive()) {
                return;
            }
            ActivityManager.MemoryInfo info = CleanEngine.readMemory(this);
            if (!CleanEngine.isLowMemory(info)) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (lastCleanAt > 0L && now - lastCleanAt < CLEAN_COOLDOWN_MS) {
                return;
            }
        }

        CleanEngine.Result result = CleanEngine.clean(this);
        if (result.count > 0) {
            lastCleanAt = SystemClock.elapsedRealtime();
            CleanLogStore.add(this, new CleanRecord(
                    System.currentTimeMillis(),
                    result.beforeMb,
                    result.afterMb,
                    result.count,
                    result.names));
            updateNotification();
        }

        if (toastAfter) {
            String memory = result.afterMb > 0L
                    ? UiUtils.formatMbValue(result.afterMb)
                    : UiUtils.formatMb(CleanEngine.readMemory(this).availMem);
            if (result.count > 0) {
                showToast(getString(R.string.key_clean_done, memory));
            } else {
                showToast(getString(R.string.key_clean_none, memory));
            }
        }

        if (result.count > 0 || manual) {
            broadcastResult(result.count, result.beforeMb, result.afterMb, manual, toastAfter);
        }
    }

    private void showToast(String text) {
        if (handler != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(GuardService.this, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void broadcastResult(int count, long before, long after, boolean manual,
                                 boolean silent) {
        Intent intent = new Intent(ACTION_CLEAN_DONE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_COUNT, count);
        intent.putExtra(EXTRA_BEFORE, before);
        intent.putExtra(EXTRA_AFTER, after);
        intent.putExtra(EXTRA_MANUAL, manual);
        intent.putExtra(EXTRA_SILENT, silent);
        sendBroadcast(intent);
    }

    private String currentNotificationText() {
        ActivityManager.MemoryInfo info = CleanEngine.readMemory(this);
        return getString(R.string.notification_text, UiUtils.formatMb(info.availMem));
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, buildNotification(currentNotificationText()));
            }
        } catch (Throwable ignored) {
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            NotificationManager nm = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.app_subtitle));
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            nm.createNotificationChannel(channel);
        } catch (Throwable ignored) {
        }
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openIntent, pendingFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(Notification.PRIORITY_LOW);
        try {
            builder.setColor(getResources().getColor(R.color.accent));
        } catch (Throwable ignored) {
        }
        return builder.build();
    }
}
