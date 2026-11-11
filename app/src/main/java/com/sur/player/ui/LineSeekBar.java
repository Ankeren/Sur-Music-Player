package com.sur.player.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * WEEK 9 TOPIC: a custom view.
 *
 * The thin progress line under the artwork on the player screen. Android
 * does have a SeekBar, but it comes with padding and a thumb that cannot be
 * made this small without fighting it, so this one is drawn by hand.
 *
 * It is a plain subclass of View doing three things:
 *
 *   onMeasure()     - decide how tall it wants to be
 *   onDraw()        - draw a grey line, a coloured line over the played
 *                     part, and a small knob where the two meet
 *   onTouchEvent()  - turn a finger position into a percentage along the
 *                     line and report it to the listener
 *
 * Everything is drawn with Canvas and Paint. Nothing here is a built-in
 * widget, which is the point of the topic.
 */
public class LineSeekBar extends View {

    /** All in pixels, worked out from dp in init(). */
    private float lineHeight;
    private float knobRadius;
    private float knobRadiusDragging;
    private float wantedHeight;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 0f .. 1f */
    private float progress = 0f;
    private boolean dragging = false;

    private OnSeekListener listener;

    /** Told when the user lets go, with the fraction they chose. */
    public interface OnSeekListener {
        void onSeek(float fraction);
    }

    // Three constructors: from code, and the two the XML inflater uses.
    public LineSeekBar(Context context) {
        super(context);
        init();
    }

    public LineSeekBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        lineHeight = 4f * density;
        knobRadius = 6f * density;
        knobRadiusDragging = 9f * density;
        // A 4dp line is far too thin to hit with a finger, so the view
        // itself is tall enough to touch and the line is drawn in the middle.
        wantedHeight = 32f * density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(lineHeight);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(Color.parseColor("#40FFFFFF"));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(lineHeight);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(Color.parseColor("#3A6EA5"));

        knobPaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(Color.parseColor("#3A6EA5"));
    }

    public void setListener(OnSeekListener listener) {
        this.listener = listener;
    }

    /** Called by the player twice a second. Ignored while the user drags. */
    public void setProgress(long positionMs, long durationMs) {
        if (dragging) return;
        float value = durationMs <= 0 ? 0f : (float) positionMs / (float) durationMs;
        progress = clamp(value);
        invalidate();   // asks Android to call onDraw() again
    }

    public void setAccentColor(int color) {
        progressPaint.setColor(color);
        knobPaint.setColor(color);
        invalidate();
    }

    // -----------------------------------------------------------------
    // MEASURE
    // -----------------------------------------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Take whatever width we are given. For height, ask for the touch
        // target unless the parent insists on an exact size.
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = resolveSize((int) wantedHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    // -----------------------------------------------------------------
    // DRAW
    // -----------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // The knob must not be cut off at either end, so the line stops
        // one knob radius in from each edge.
        float left = getPaddingLeft() + knobRadiusDragging;
        float right = getWidth() - getPaddingRight() - knobRadiusDragging;
        if (right <= left) return;

        float middle = getHeight() / 2f;
        float playedTo = left + (right - left) * progress;

        canvas.drawLine(left, middle, right, middle, trackPaint);
        if (playedTo > left) {
            canvas.drawLine(left, middle, playedTo, middle, progressPaint);
        }
        canvas.drawCircle(playedTo, middle,
                dragging ? knobRadiusDragging : knobRadius, knobPaint);
    }

    // -----------------------------------------------------------------
    // TOUCH
    // -----------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                // The player screen sits in a scrolling parent on some
                // phones. This tells the parent to leave the touch alone
                // so a sideways drag seeks instead of scrolling.
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                moveTo(event.getX());
                return true;

            case MotionEvent.ACTION_MOVE:
                moveTo(event.getX());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                moveTo(event.getX());
                dragging = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                invalidate();
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    performClick();
                    if (listener != null) listener.onSeek(progress);
                }
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    /** Turns an x position into a percentage along the line. */
    private void moveTo(float x) {
        float left = getPaddingLeft() + knobRadiusDragging;
        float right = getWidth() - getPaddingRight() - knobRadiusDragging;
        if (right <= left) return;

        progress = clamp((x - left) / (right - left));
        invalidate();
    }

    /**
     * Android asks for this whenever a view handles its own touches, so
     * that screen readers and accessibility services still work.
     */
    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private static float clamp(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }
}
