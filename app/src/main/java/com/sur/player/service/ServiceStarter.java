package com.sur.player.service;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

/**
 * Two small helpers so nobody has to remember the Android 8 rule that a
 * background component must use startForegroundService() instead of
 * startService().
 */
public final class ServiceStarter {

    private ServiceStarter() {
    }

    /** Start (or wake) the music service with an action. */
    public static void send(Context context, String action) {
        Intent intent = new Intent(context, MusicService.class).setAction(action);
        ContextCompat.startForegroundService(context, intent);
    }

    /**
     * Starts the service with no action at all. The UI calls this just
     * before playQueue(), so the service is promoted to the foreground and
     * keeps running once the user leaves the app.
     */
    public static void wake(Context context) {
        Intent intent = new Intent(context, MusicService.class);
        ContextCompat.startForegroundService(context, intent);
    }

}
