package com.tvautoclean.guard;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

/**
 * TV-friendly screen for choosing the dedicated remote-control key. It supports
 * two capture paths: the accessibility service (preferred, works globally) and
 * this Activity's own key dispatch (fallback when accessibility is unavailable).
 */
public class KeyCleanActivity extends Activity {
    private static final long CAPTURE_TIMEOUT_MS = 15_000L;

    private TextView tvAccessStatus;
    private TextView tvAccessAction;
    private TextView tvKeyName;
    private TextView tvKeyHint;
    private TextView btnCapture;
    private TextView btnTest;
    private TextView btnClear;
    private View rowAccess;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean capturing;
    private boolean receiverRegistered;

    private final Runnable captureTimeout = new Runnable() {
        @Override
        public void run() {
            if (!capturing) {
                return;
            }
            capturing = false;
            KeyCleanAccessibilityService.cancelCapture();
            btnCapture.setText(R.string.key_capture);
            btnCapture.setClickable(true);
            tvKeyHint.setText(R.string.key_capture_timeout);
            UiUtils.toast(KeyCleanActivity.this, getString(R.string.key_capture_timeout));
        }
    };

    private final BroadcastReceiver keyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !KeyCleanAccessibilityService.ACTION_KEY_CAPTURED.equals(
                    intent.getAction())) {
                return;
            }
            handleCapturedKey(intent.getIntExtra(
                    KeyCleanAccessibilityService.EXTRA_KEY_CODE, -1));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_clean);

        tvAccessStatus = (TextView) findViewById(R.id.tvAccessStatus);
        tvAccessAction = (TextView) findViewById(R.id.tvAccessAction);
        tvKeyName = (TextView) findViewById(R.id.tvKeyName);
        tvKeyHint = (TextView) findViewById(R.id.tvKeyHint);
        btnCapture = (TextView) findViewById(R.id.btnCapture);
        btnTest = (TextView) findViewById(R.id.btnTest);
        btnClear = (TextView) findViewById(R.id.btnClear);
        rowAccess = findViewById(R.id.rowAccess);

        showKeyLabel(Prefs.getCleanKeyCode(this));
        updateAccessibilityStatus();

        UiUtils.attachFocusScale(findViewById(R.id.btnBack), 1.05f);
        UiUtils.attachFocusScale(rowAccess, 1.01f);
        UiUtils.attachFocusScale(btnCapture, 1.02f);
        UiUtils.attachFocusScale(btnTest, 1.02f);
        UiUtils.attachFocusScale(btnClear, 1.02f);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        rowAccess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!KeyCleanAccessibilityService.isEnabled(KeyCleanActivity.this)) {
                    UiUtils.toast(KeyCleanActivity.this, getString(R.string.key_need_access));
                    openAccessibilitySettings();
                    return;
                }
                startCapture();
            }
        });
        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GuardService.requestCleanNow(KeyCleanActivity.this, true);
            }
        });
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setCleanKeyCode(KeyCleanActivity.this, -1);
                KeyCleanAccessibilityService.setKey(-1);
                showKeyLabel(-1);
                tvKeyHint.setText(R.string.key_hint);
                UiUtils.toast(KeyCleanActivity.this, getString(R.string.key_cleared));
            }
        });

        if (KeyCleanAccessibilityService.isEnabled(this)) {
            btnCapture.requestFocus();
        } else {
            rowAccess.requestFocus();
        }
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
        updateAccessibilityStatus();
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
                finish();
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

    private void startCapture() {
        capturing = true;
        KeyCleanAccessibilityService.armCapture();
        btnCapture.setText(R.string.key_capturing);
        btnCapture.setClickable(false);
        tvKeyHint.setText(R.string.key_capture_started);
        handler.removeCallbacks(captureTimeout);
        handler.postDelayed(captureTimeout, CAPTURE_TIMEOUT_MS);
    }

    private void cancelCapture() {
        if (!capturing) {
            return;
        }
        capturing = false;
        handler.removeCallbacks(captureTimeout);
        KeyCleanAccessibilityService.cancelCapture();
        if (btnCapture != null) {
            btnCapture.setText(R.string.key_capture);
            btnCapture.setClickable(true);
        }
        if (tvKeyHint != null) {
            tvKeyHint.setText(R.string.key_hint);
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
        capturing = false;
        handler.removeCallbacks(captureTimeout);
        KeyCleanAccessibilityService.cancelCapture();
        Prefs.setCleanKeyCode(this, code);
        KeyCleanAccessibilityService.setKey(code);
        showKeyLabel(code);
        btnCapture.setText(R.string.key_capture);
        btnCapture.setClickable(true);
        tvKeyHint.setText(R.string.key_set_hint);
        UiUtils.toast(this, getString(R.string.key_set_done, KeyNames.label(code)));
    }

    private void showKeyLabel(int code) {
        if (code > 0) {
            tvKeyName.setText(KeyNames.label(code));
            tvKeyName.setTextColor(getResources().getColor(R.color.accent));
        } else {
            tvKeyName.setText(R.string.key_unset);
            tvKeyName.setTextColor(getResources().getColor(R.color.muted));
        }
        boolean hasKey = code > 0;
        btnClear.setClickable(hasKey);
        btnClear.setTextColor(getResources().getColor(hasKey ? R.color.text : R.color.muted2));
    }

    private void updateAccessibilityStatus() {
        boolean enabled = KeyCleanAccessibilityService.isEnabled(this);
        tvAccessStatus.setText(enabled ? R.string.key_access_on : R.string.key_access_off);
        tvAccessStatus.setTextColor(getResources().getColor(
                enabled ? R.color.accent : R.color.muted));
        tvAccessAction.setText(enabled
                ? R.string.key_access_action_on
                : R.string.key_access_action_off);
        tvAccessAction.setTextColor(getResources().getColor(
                enabled ? R.color.accent : R.color.muted));
    }

    private void openAccessibilitySettings() {
        // Android TV settings uses an OEM link instead of the phone accessibility
        // action. TCL exposes ...AccessibilitySettingsActivity through this action.
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
}
