package com.sur.player.util;

import java.util.Locale;

/** Turns milliseconds into 3:07 for the player and the song rows. */
public final class TimeUtil {

    private TimeUtil() {
    }

    public static String format(long millis) {
        if (millis <= 0) return "0:00";
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes >= 60) {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }
}
