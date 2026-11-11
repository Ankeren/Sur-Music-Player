package com.sur.player.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.sur.player.service.MusicService;
import com.sur.player.util.ThemeHelper;

/**
 * WEEK 10 TOPIC: communication between components (Activity <-> Service).
 *
 * Any screen that needs to talk to the player extends this class instead of
 * AppCompatActivity, and the binding is handled once, here.
 *
 * How binding works:
 *   onStart()  -> bindService(...)  Android creates the connection
 *   onServiceConnected(...)         we get the LocalBinder, and through it
 *                                   the real MusicService object
 *   onStop()   -> unbindService(...) or the connection leaks
 *
 * Binding in onStart and unbinding in onStop (rather than onCreate/onDestroy)
 * means we only hold the connection while the screen is actually visible.
 * That is also a clean example of the Activity lifecycle - week 8.
 */
public abstract class BoundActivity extends AppCompatActivity
        implements MusicService.Listener {

    private MusicService musicService;
    private boolean bound = false;

    /** The accent this screen was built with, so we notice when it changes. */
    private int appliedAccent;

    private final ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            // The binder is the object MusicService.onBind() returned.
            musicService = ((MusicService.LocalBinder) binder).getService();
            musicService.setListener(BoundActivity.this);
            bound = true;
            onServiceReady(musicService);
            onPlaybackChanged();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Only called if the service crashes, not on a normal unbind.
            musicService = null;
            bound = false;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // The theme must be chosen before the layout is inflated, so this
        // runs first. Every screen that extends BoundActivity gets it free.
        ThemeHelper.apply(this);
        appliedAccent = ThemeHelper.getAccent(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have picked a new color in Settings and come back.
        if (ThemeHelper.getAccent(this) != appliedAccent) {
            recreate();
        }
    }

    /**
     * Shows a message if playback gave up, for example when a radio stream
     * is dead. The service clears the message once it has been read, so it
     * is shown once rather than on every refresh.
     */
    protected void showPlaybackErrorIfAny() {
        if (musicService == null) return;
        String message = musicService.takeLastError();
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, MusicService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        if (bound) {
            // clearListener, not setListener(null): see the comment in
            // MusicService. Another screen may already have taken over.
            if (musicService != null) musicService.clearListener(this);
            unbindService(connection);
            bound = false;
        }
        super.onStop();
    }

    /** Null until the connection is made, so always check before using it. */
    @Nullable
    public MusicService getMusicService() {
        return musicService;
    }

    public boolean isServiceBound() {
        return bound;
    }

    /** Subclasses override this if they need to do something once connected. */
    protected void onServiceReady(MusicService service) {
    }

    /** Called on the main thread whenever playback changes. */
    @Override
    public void onPlaybackChanged() {
    }
}
