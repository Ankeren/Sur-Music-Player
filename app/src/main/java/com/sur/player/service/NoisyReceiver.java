package com.sur.player.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

/**
 * WEEK 10 TOPIC: a BroadcastReceiver, registered in code.
 *
 * Android sends ACTION_AUDIO_BECOMING_NOISY just before sound is about to
 * come out of the phone's own speaker instead of the headphones - in other
 * words, the moment the user pulls their earphones out.
 *
 * Every decent music app pauses at that point. Without this, your song
 * suddenly blares out of the speaker on the bus.
 *
 * This receiver is registered inside MusicService.onCreate() and
 * unregistered in onDestroy(), because it only matters while the service is
 * alive. Compare it with BootReceiver, which is declared in the manifest
 * because it has to work when the app is not running at all.
 */
public class NoisyReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) return;

        // Ask the service to pause. We send an Intent rather than holding a
        // reference to the service, so this works however it was registered.
        // Note it is ACTION_PAUSE, not PLAY_PAUSE: unplugging headphones
        // should never START music that was already paused.
        ServiceStarter.send(context, MusicService.ACTION_PAUSE);
    }
}
