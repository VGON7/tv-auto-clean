package com.tvautoclean.guard;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

/** Checks the optional "usage access" permission used to identify the foreground app. */
public final class UsageAccess {
    private UsageAccess() {
    }

    public static boolean hasAccess(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) {
                return false;
            }
            int mode = ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
