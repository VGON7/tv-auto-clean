package com.tvautoclean.guard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/** A small custom memory gauge; no image assets or libraries. */
public class MemoryRingView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private float fraction;
    private float density;
    private int ringColor = Color.rgb(69, 224, 176);
    private String availableText;
    private String memoryText;

    public MemoryRingView(Context context) {
        super(context);
        init();
    }

    public MemoryRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MemoryRingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        availableText = getContext().getString(R.string.ring_available);
        memoryText = getContext().getString(R.string.ring_memory);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(10f));
        trackPaint.setColor(Color.rgb(27, 48, 59));

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(18f));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setColor(ringColor);
        glowPaint.setAlpha(30);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(10f));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(ringColor);

        labelPaint.setColor(Color.rgb(143, 166, 179));
        labelPaint.setTextSize(UiUtils.sp(getContext(), 11f));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        valuePaint.setTextSize(UiUtils.sp(getContext(), 29f));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        valuePaint.setColor(ringColor);

        unitPaint.setColor(Color.rgb(143, 166, 179));
        unitPaint.setTextSize(UiUtils.sp(getContext(), 10f));
        unitPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    public void setMemory(long availableBytes, long totalBytes) {
        if (totalBytes > 0L) {
            fraction = Math.max(0f, Math.min(1f, availableBytes / (float) totalBytes));
        } else {
            fraction = 0f;
        }
        if (fraction < 0.10f) {
            ringColor = Color.rgb(255, 112, 112);
        } else if (fraction < 0.20f) {
            ringColor = Color.rgb(245, 196, 81);
        } else {
            ringColor = Color.rgb(69, 224, 176);
        }
        glowPaint.setColor(ringColor);
        arcPaint.setColor(ringColor);
        valuePaint.setColor(ringColor);
        setContentDescription(getContext().getString(R.string.available_memory)
                + " " + Math.round(fraction * 100f) + "%");
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float cx = width / 2f;
        float cy = height / 2f;
        float stroke = dp(10f);
        float radius = Math.min(width, height) / 2f - stroke * 1.8f;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        canvas.drawArc(oval, -90f, 360f, false, trackPaint);
        if (fraction > 0.001f) {
            canvas.drawArc(oval, -90f, 360f * fraction, false, glowPaint);
            canvas.drawArc(oval, -90f, 360f * fraction, false, arcPaint);
        }

        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setStyle(Paint.Style.FILL);
        inner.setColor(Color.rgb(12, 23, 30));
        canvas.drawCircle(cx, cy, radius - stroke * 0.9f, inner);

        int percent = Math.round(fraction * 100f);
        canvas.drawText(availableText, cx, cy - dp(27f), labelPaint);
        canvas.drawText(percent + "%", cx, cy + dp(9f), valuePaint);
        canvas.drawText(memoryText, cx, cy + dp(33f), unitPaint);
    }

    private float dp(float value) {
        return value * density;
    }
}
