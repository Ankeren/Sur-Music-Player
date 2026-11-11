package com.sur.player.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

/**
 * Builds the player screen's look from whatever album art is playing.
 *
 * Two things come out of one image:
 *   1. an ACCENT colour, picked by the Palette library, used for the seek
 *      ring and the song title;
 *   2. a BACKGROUND, which is the same artwork blurred and darkened.
 *
 * How the blur works, since there is no blur call that works on every
 * Android version: the bitmap is shrunk to 24 by 24 pixels, which throws
 * away all the detail, and then stretched back to full screen size with
 * smoothing switched on. The smoothing fills in the gaps between those few
 * pixels with soft gradients, and the result looks blurred. It costs almost
 * nothing and needs no extra library, unlike RenderEffect, which only exists
 * from Android 12 while this app supports Android 7.
 */
public final class ArtworkTheme {

    /** How small the image gets before it is stretched back up. */
    private static final int BLUR_SIZE = 24;

    private ArtworkTheme() {
    }

    /**
     * Picks a colour that stands out on a dark screen.
     *
     * Palette sorts an image into swatches: vibrant, muted, dark, light. We
     * try the bright ones first and fall back to the theme's own accent when
     * the artwork has no usable colour, for example a black and white cover.
     */
    public static int accentFrom(@Nullable Palette palette, int fallback) {
        if (palette == null) return fallback;

        int colour = palette.getVibrantColor(
                palette.getLightVibrantColor(
                        palette.getLightMutedColor(
                                palette.getMutedColor(fallback))));
        if (colour == fallback) return fallback;

        // Photographs of grey or rainy scenes give back a colour that is
        // almost grey. Painting the seek ring grey just looks broken, so in
        // that case we keep the accent the user picked in Settings.
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(colour, hsl);
        if (hsl[1] < 0.20f) return fallback;

        // A very dark colour would disappear against the background, so
        // brighten it until it is readable.
        return lighten(colour);
    }

    /**
     * The blurred artwork, with a dark gradient over it so that white text
     * stays readable no matter how bright the cover is.
     */
    public static Drawable backgroundFrom(Resources resources, Bitmap art,
                                          int accent, int base) {
        Bitmap tiny = Bitmap.createScaledBitmap(art, BLUR_SIZE, BLUR_SIZE, true);

        BitmapDrawable blurred = new BitmapDrawable(resources, tiny);
        blurred.setFilterBitmap(true);   // smooth the stretch, which is the blur
        blurred.setGravity(Gravity.FILL);

        // The scrim is a dark version of the accent, not the accent itself.
        // The accent colours are deliberately pale so they show up on a dark
        // screen, and a pale scrim over a bright cover leaves the top of the
        // screen too light to read the back arrow against.
        int tint = darken(accent);

        GradientDrawable scrim = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        withAlpha(tint, 0xB3),
                        withAlpha(tint, 0xD9),
                        withAlpha(base, 0xF2)
                });

        return new LayerDrawable(new Drawable[]{blurred, scrim});
    }

    /** The plain background, for radio and anything with no artwork. */
    public static Drawable plainBackground(int base) {
        return new ColorDrawable(base);
    }

    // ---- small colour helpers -------------------------------------------

    private static int withAlpha(int colour, int alpha) {
        return Color.argb(alpha, Color.red(colour), Color.green(colour), Color.blue(colour));
    }

    /** Mixes the colour with white until it is bright enough to read. */
    private static int lighten(int colour) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(colour, hsl);
        if (hsl[2] < 0.55f) hsl[2] = 0.70f;   // lightness, 0 is black and 1 is white
        if (hsl[1] > 0.85f) hsl[1] = 0.70f;   // tone down colours that are too strong
        return ColorUtils.HSLToColor(hsl);
    }

    /** The same hue, pushed down to almost black, for the background wash. */
    private static int darken(int colour) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(colour, hsl);
        hsl[2] = 0.11f;
        return ColorUtils.HSLToColor(hsl);
    }
}
