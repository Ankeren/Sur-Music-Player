package com.sur.player.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * WEEK 10 TOPIC: a BroadcastReceiver, and communication between components.
 *
 * Two different things send broadcasts here:
 *
 *   1. the three buttons on the playback notification, and
 *   2. the sleep-timer alarm from AlarmManager.
 *
 * A notification button cannot call a method on the service directly - the
 * notification lives in the system UI, in another process. So the button
 * fires a broadcast, this receiver catches it, and forwards the action to
 * MusicService. Receiver in, service out.
 *
 * Declared in AndroidManifest.xml (not exported: only our own app and the
 * system alarm may reach it).
 */
public class PlaybackActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();

        // The sleep timer fired: clear the saved time so Settings stops
        // showing a countdown, then stop playback.
        if (MusicService.ACTION_STOP.equals(action)) {
            SleepTimer.clear(context);
        }

        ServiceStarter.send(context, action);
    }
}
