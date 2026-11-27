package com.tvautoclean.guard;

import android.content.Context;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class UiUtils {
    private UiUtils() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static float sp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().scaledDensity;
    }

    public static String formatMb(long bytes) {
        if (bytes <= 0L) {
            return "—";
        }
        long mb = bytes / (1024L * 1024L);
        if (mb >= 1024L) {
            return String.format(Locale.CHINA, "%.1f GB", mb / 1024.0f);
        }
        return mb + " MB";
    }

    public static String formatMbValue(long mb) {
        if (mb <= 0L) {
            return "—";
        }
        if (mb >= 1024L) {
            return String.format(Locale.CHINA, "%.1f GB", mb / 1024.0f);
        }
        return mb + " MB";
    }

    public static void setTextSizeSp(TextView view, float size) {
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size);
    }

    public static void attachFocusScale(final View view, final float scale) {
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.animate()
                        .scaleX(hasFocus ? scale : 1f)
                        .scaleY(hasFocus ? scale : 1f)
                        .setDuration(hasFocus ? 140L : 110L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        });
    }

    public static void toast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }
}
