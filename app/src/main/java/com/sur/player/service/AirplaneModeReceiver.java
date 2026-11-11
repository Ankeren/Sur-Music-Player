package com.sur.player.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * WEEK 10 TOPIC: a second BroadcastReceiver, reacting to a system setting
 * rather than to hardware.
 *
 * Android sends ACTION_AIRPLANE_MODE_CHANGED whenever the user turns
 * airplane mode on or off. For this app that matters, because the two
 * halves of Sur behave very differently without a network:
 *
 *   songs on the phone  - keep playing, nothing changes
 *   radio and podcasts  - cannot continue, the stream is gone
 *
 * So when airplane mode comes on, the service stops only if the thing
 * playing came from the internet, and says why. A local song is left alone,
 * which is the whole point of an offline first player.
 *
 * WHY THIS IS REGISTERED IN CODE AND NOT IN THE MANIFEST:
 * since Android 8, most implicit broadcasts no longer reach receivers that
 * are only declared in AndroidManifest.xml. That change stopped dozens of
 * sleeping apps waking up every time a system setting moved.
 * ACTION_AIRPLANE_MODE_CHANGED is one of the affected broadcasts, so a
 * manifest entry would simply never fire. Registering it from
 * MusicService.onCreate() ties it to something that is genuinely running.
 * BootReceiver is the opposite case: it has to work when the app is not
 * running at all, so it must be in the manifest.
 */
public class AirplaneModeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || context == null) return;
        if (!Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) return;

        if (!isAirplaneModeOn(context)) return;   // turned off, nothing to do

        ServiceStarter.send(context, MusicService.ACTION_NETWORK_LOST);
    }

    /**
     * The broadcast carries a "state" extra, but reading the setting itself
     * is more reliable, because some manufacturers send the broadcast
     * without that extra.
     */
    private boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }
}
