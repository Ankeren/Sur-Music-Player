package com.sur.player.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * The stand-in shown when a song has no album art.
 *
 * Plenty of music has no cover embedded in the file, which is especially
 * true of the regional songs this app was built for. A grey music note on
 * every one of those rows makes a library look broken, so instead each song
 * gets a coloured tile with the first letter of its title.
 *
 * The colour comes from the title itself, so the same song is always the
 * same colour, and two different songs are very unlikely to match. Only the
 * hue varies. Saturation and lightness are fixed, which keeps every tile
 * sitting at the same depth against the dark background instead of some
 * glaring and some invisible.
 */
public class LetterTile extends Drawable {

    private static final float BACKGROUND_SATURATION = 0.32f;
    private static final float BACKGROUND_LIGHTNESS = 0.28f;
    private static final float LETTER_LIGHTNESS = 0.78f;

    /** The letter fills this share of the tile's height. */
    private static final float LETTER_SCALE = 0.42f;

    private final String letter;
    private final float cornerRadius;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();
    private final RectF tile = new RectF();

    public static Drawable forTitle(Context context, @Nullable String title) {
        return new LetterTile(context, title);
    }

    private LetterTile(Context context, @Nullable String title) {
        this.letter = firstLetterOf(title);
        this.cornerRadius = 10f * context.getResources().getDisplayMetrics().density;

        float hue = hueFor(title);
        backgroundPaint.setColor(ColorUtils.HSLToColor(
                new float[]{hue, BACKGROUND_SATURATION, BACKGROUND_LIGHTNESS}));

        letterPaint.setColor(ColorUtils.HSLToColor(
                new float[]{hue, BACKGROUND_SATURATION, LETTER_LIGHTNESS}));
        letterPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        letterPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        tile.set(getBounds());
        canvas.drawRoundRect(tile, cornerRadius, cornerRadius, backgroundPaint);

        letterPaint.setTextSize(tile.height() * LETTER_SCALE);

        // Centring text vertically needs the glyph's own bounds. Using the
        // font metrics instead would leave it looking slightly high, because
        // they allow room for descenders this letter may not have.
        letterPaint.getTextBounds(letter, 0, letter.length(), textBounds);
        float baseline = tile.centerY() + textBounds.height() / 2f;

        canvas.drawText(letter, tile.centerX(), baseline, letterPaint);
    }

    private static String firstLetterOf(@Nullable String title) {
        if (title == null) return "♪";
        String trimmed = title.trim();
        if (trimmed.isEmpty()) return "♪";

        String first = trimmed.substring(0, 1).toUpperCase();
        // A title starting with punctuation would show a meaningless glyph.
        return Character.isLetterOrDigit(first.charAt(0)) ? first : "♪";
    }

    /** A stable hue from 0 to 360, derived from the title. */
    private static float hueFor(@Nullable String title) {
        int hash = title == null ? 0 : title.hashCode();
        return Math.abs(hash % 360);
    }

    // ---- Drawable requires these, but a solid tile has nothing to do ----

    @Override
    public void setAlpha(int alpha) {
        backgroundPaint.setAlpha(alpha);
        letterPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        backgroundPaint.setColorFilter(colorFilter);
        letterPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
