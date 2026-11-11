package com.sur.player;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.sur.player.data.Repository;

/**
 * The Application class. Android creates exactly one of these, before any
 * Activity, and keeps it alive for as long as the process lives.
 *
 * We use it for two things only:
 *   1. one shared Repository, so every screen talks to the same database
 *   2. creating the notification channel once (required since Android 8)
 *
 * Registered in AndroidManifest.xml with android:name=".SurApp".
 */
public class SurApp extends Application {

    public static final String CHANNEL_ID = "sur_playback";

    private static Repository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new Repository(this);
        createNotificationChannel();
    }

    /** Every screen calls this instead of building its own Repository. */
    public static Repository repo() {
        return repository;
    }

    private void createNotificationChannel() {
        // Channels only exist on Android 8 (API 26) and above. Without one,
        // no notification will ever appear on a modern phone.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW);   // LOW = no sound
            channel.setDescription(getString(R.string.channel_description));
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
