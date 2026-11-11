package com.sur.player.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * WEEK 9 TOPIC: a second custom view, alongside LineSeekBar.
 *
 * Four little bars that bounce up and down, drawn on the row of whichever
 * song is playing. There is no real audio analysis behind it. The bars
 * follow a sine wave, each one a quarter of a cycle behind the one before,
 * which is enough to read as "this one is playing".
 *
 * One ValueAnimator drives all four bars instead of one each. It loops
 * forever from 0 to 1, and every frame calls invalidate(), which asks
 * Android to call onDraw again.
 *
 * Because RecyclerView recycles rows, this view gets attached and detached
 * as the user scrolls. The animator is started in onAttachedToWindow and
 * stopped in onDetachedFromWindow, so a row that has scrolled away is not
 * burning battery off screen.
 */
public class EqualizerBarsView extends View {

    private static final int BAR_COUNT = 4;
    private static final float BAR_WIDTH_DP = 3f;
    private static final float BAR_GAP_DP = 2f;
    private static final float BLOCK_HEIGHT_DP = 16f;
    private static final float MIN_FRACTION = 0.25f;   // shortest bar height

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final float barWidth;
    private final float barGap;
    private final float blockHeight;

    @Nullable
    private ValueAnimator animator;

    /** Where in the loop we are, from 0 to 1. */
    private float phase;

    public EqualizerBarsView(Context context) {
        this(context, null);
    }

    public EqualizerBarsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        float density = getResources().getDisplayMetrics().density;
        barWidth = BAR_WIDTH_DP * density;
        barGap = BAR_GAP_DP * density;
        blockHeight = BLOCK_HEIGHT_DP * density;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorPrimary));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float totalWidth = BAR_COUNT * barWidth + (BAR_COUNT - 1) * barGap;
        float startX = (getWidth() - totalWidth) / 2f;
        float bottom = (getHeight() + blockHeight) / 2f;
        float radius = barWidth / 2f;

        for (int i = 0; i < BAR_COUNT; i++) {
            // Each bar is a quarter of a cycle behind the previous one.
            double angle = 2 * Math.PI * (phase + i * 0.25f);
            float wave = (float) (0.5 + 0.5 * Math.sin(angle));
            float height = blockHeight * (MIN_FRACTION + (1 - MIN_FRACTION) * wave);

            float left = startX + i * (barWidth + barGap);
            bar.set(left, bottom - height, left + barWidth, bottom);
            canvas.drawRoundRect(bar, radius, radius, paint);
        }
    }

    // ---- starting and stopping ------------------------------------------

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            if (isAttachedToWindow()) start();
        } else {
            stop();
        }
    }

    private void start() {
        if (animator != null) return;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(900);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stop() {
        if (animator == null) return;
        animator.cancel();
        animator = null;
    }
}
