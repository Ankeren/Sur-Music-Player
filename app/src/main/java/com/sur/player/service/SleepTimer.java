package com.sur.player.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * "Stop playing in 20 minutes."
 *
 * Done with AlarmManager rather than a background thread, because the alarm
 * must survive the app being closed - and, thanks to BootReceiver, the phone
 * being restarted.
 *
 * The time it should fire is saved in SharedPreferences, which is what makes
 * re-arming after a reboot possible.
 */
public final class SleepTimer {

    private static final String PREFS = "sur_prefs";
    private static final String KEY_STOP_AT = "sleep_timer_stop_at";
    private static final int REQUEST_CODE = 7001;

    private SleepTimer() {
    }

    /** Schedules the stop, and remembers when it is due. */
    public static void start(Context context, int minutes) {
        long stopAt = System.currentTimeMillis() + minutes * 60_000L;
        prefs(context).edit().putLong(KEY_STOP_AT, stopAt).apply();
        schedule(context, stopAt);
    }

    /** Called by BootReceiver after a restart. */
    public static void rearmAfterBoot(Context context) {
        long stopAt = prefs(context).getLong(KEY_STOP_AT, 0L);
        if (stopAt > System.currentTimeMillis()) {
            schedule(context, stopAt);
        } else {
            clear(context);
        }
    }

    public static void cancel(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) {
            alarms.cancel(pendingIntent(context));
        }
        clear(context);
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_STOP_AT).apply();
    }

    /** Minutes left, or 0 when no timer is running. */
    public static int minutesLeft(Context context) {
        long stopAt = prefs(context).getLong(KEY_STOP_AT, 0L);
        long remaining = stopAt - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (int) Math.ceil(remaining / 60000.0);
    }

    // ---- internals -------------------------------------------------------

    private static void schedule(Context context, long stopAt) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;

        PendingIntent pending = pendingIntent(context);

        // setExactAndAllowWhileIdle survives Doze mode. On Android 12+ the
        // app needs permission for exact alarms; if it is not granted we
        // quietly fall back to an inexact alarm, which is close enough for
        // a sleep timer.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.set(AlarmManager.RTC_WAKEUP, stopAt, pending);
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stopAt, pending);
        }
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, PlaybackActionReceiver.class)
                .setAction(MusicService.ACTION_STOP);
        return PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
