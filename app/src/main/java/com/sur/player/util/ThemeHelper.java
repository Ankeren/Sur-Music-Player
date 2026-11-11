package com.sur.player.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.sur.player.R;

/**
 * WEEK 9 TOPIC: themes, and saving a small user setting.
 *
 * The app has four accent colors. The user's choice is a single number
 * (0 to 3) kept in SharedPreferences, which is Android's simple key-value
 * storage. Every Activity asks this class to apply the saved theme in
 * onCreate, before it inflates its layout. A theme cannot be changed after
 * the layout is drawn, which is why Settings calls recreate() afterwards.
 */
public final class ThemeHelper {

    public static final String[] NAMES = {"Green", "Purple", "Orange", "Blue"};

    private static final int[] STYLES = {
            R.style.Theme_Sur,          // green is the base theme
            R.style.Theme_Sur_Purple,
            R.style.Theme_Sur_Orange,
            R.style.Theme_Sur_Blue
    };

    private static final String PREFS = "sur_prefs";
    private static final String KEY_ACCENT = "accent";

    private ThemeHelper() {
    }

    /** Call in onCreate BEFORE super.onCreate() and before setContentView(). */
    public static void apply(Activity activity) {
        activity.setTheme(STYLES[getAccent(activity)]);
    }

    public static int getAccent(Context context) {
        int saved = prefs(context).getInt(KEY_ACCENT, 0);
        // Guard against a bad value, so the app can never crash on it.
        return (saved >= 0 && saved < STYLES.length) ? saved : 0;
    }

    public static void setAccent(Context context, int index) {
        prefs(context).edit().putInt(KEY_ACCENT, index).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
