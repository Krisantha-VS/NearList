package com.sharma.nearlist.ui.places;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.sharma.nearlist.R;
import com.sharma.nearlist.util.Formatters;

/**
 * Map-free radius preview (screens 14–17): a pin at the centre, dashed reference rings at 500 m
 * and 1 km, and a translucent primary circle whose size follows the chosen radius.
 */
public class RadiusPreviewView extends View {

    private static final int REFERENCE_NEAR_M = 500;
    private static final int REFERENCE_FAR_M = 1000;
    private static final float FAR_RING_RADIUS_DP = 140f;
    private static final long ANIMATION_MS = 150L;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint radiusLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int outline;
    private final float density;
    private final Drawable pin;

    private float radiusM = 300f;
    private ValueAnimator animator;

    public RadiusPreviewView(@NonNull Context context) {
        this(context, null);
    }

    public RadiusPreviewView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        int primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        int primaryContainer = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer);
        outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline);
        int onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(MaterialColors.compositeARGBWithAlpha(primaryContainer, 128));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2 * density);
        strokePaint.setColor(primary);
        dashedPaint.setStyle(Paint.Style.STROKE);
        dashedPaint.setStrokeWidth(1.5f * density);
        dashedPaint.setPathEffect(new DashPathEffect(new float[]{4 * density, 3 * density}, 0));
        linePaint.setStrokeWidth(density);
        linePaint.setColor(MaterialColors.compositeARGBWithAlpha(outline, 102));

        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics());
        labelPaint.setTextSize(textSize);
        labelPaint.setColor(onSurfaceVariant);
        radiusLabelPaint.setTextSize(textSize);
        radiusLabelPaint.setColor(primary);
        radiusLabelPaint.setTypeface(Typeface.DEFAULT_BOLD);

        pin = ContextCompat.getDrawable(context, R.drawable.ic_location_pin_filled);
        if (pin != null) {
            pin.mutate().setTint(primary);
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Sets the radius in metres; animates unless {@code animate} is false (first bind, rotation). */
    public void setRadius(int meters, boolean animate) {
        if (animator != null) {
            animator.cancel();
        }
        if (!animate || !isLaidOut()) {
            radiusM = meters;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(radiusM, meters);
        animator.setDuration(ANIMATION_MS);
        animator.addUpdateListener(a -> {
            radiusM = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        // 1 km is drawn at 140 dp, or smaller when the view is narrow, so its label still fits.
        float farRadius = Math.min(FAR_RING_RADIUS_DP * density, cx - 8 * density);
        float pxPerMeter = farRadius / REFERENCE_FAR_M;

        drawReferenceRing(canvas, cx, cy, REFERENCE_FAR_M * pxPerMeter, 102);
        drawReferenceRing(canvas, cx, cy, REFERENCE_NEAR_M * pxPerMeter, 153);

        float r = radiusM * pxPerMeter;
        canvas.drawCircle(cx, cy, r, fillPaint);
        canvas.drawCircle(cx, cy, r, strokePaint);
        canvas.drawLine(cx, cy, getWidth(), cy, linePaint);

        float gap = 4 * density;
        float above = cy - gap - labelPaint.descent();
        drawLabelRightOf(canvas, Formatters.radius(REFERENCE_NEAR_M), cx + REFERENCE_NEAR_M * pxPerMeter + gap, above,
                labelPaint);
        drawLabelRightOf(canvas, Formatters.radius(REFERENCE_FAR_M), cx + farRadius + gap, above, labelPaint);
        float below = cy + gap - radiusLabelPaint.ascent();
        drawLabelRightOf(canvas, Formatters.radius(Math.round(radiusM / 50f) * 50), cx + r + gap, below,
                radiusLabelPaint);

        if (pin != null) {
            int size = Math.round(28 * density);
            int left = Math.round(cx - size / 2f);
            int bottom = Math.round(cy + 2 * density);
            pin.setBounds(left, bottom - size, left + size, bottom);
            pin.draw(canvas);
        }
    }

    private void drawReferenceRing(Canvas canvas, float cx, float cy, float radius, int alpha) {
        dashedPaint.setColor(MaterialColors.compositeARGBWithAlpha(outline, alpha));
        canvas.drawCircle(cx, cy, radius, dashedPaint);
    }

    /** Draws at x, or pulled left so the text stays inside the view. */
    private void drawLabelRightOf(Canvas canvas, String text, float x, float baseline, Paint paint) {
        float width = paint.measureText(text);
        float maxX = getWidth() - 4 * density - width;
        canvas.drawText(text, Math.min(x, maxX), baseline, paint);
    }
}
