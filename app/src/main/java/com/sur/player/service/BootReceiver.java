package com.sur.player.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * WEEK 10 TOPIC: a BroadcastReceiver, declared in the manifest.
 *
 * When Android restarts, every alarm ever set by every app is thrown away.
 * If the user had set "stop playing in 40 minutes" and the phone rebooted
 * after 10, that timer would silently vanish.
 *
 * So the system sends BOOT_COMPLETED once, at startup, and we set the alarm
 * again from the time we saved in SharedPreferences.
 *
 * Why the manifest and not registerReceiver()? Because our app is not
 * running at that moment. A manifest-declared receiver lets Android start
 * the app process just to deliver this one broadcast. That is the practical
 * difference between the two ways of registering, and it is a very likely
 * viva question.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        // Keep this short: onReceive runs on the main thread and Android
        // kills a receiver that takes more than about 10 seconds.
        SleepTimer.rearmAfterBoot(context);
    }
}
