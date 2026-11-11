package com.sur.player.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.sur.player.PlayerActivity;
import com.sur.player.R;
import com.sur.player.SurApp;
import com.sur.player.firebase.Accounts;
import com.sur.player.firebase.PlayCounts;
import com.sur.player.model.Playable;
import com.sur.player.model.Song;
import com.sur.player.util.AppExecutors;

import java.util.List;

/**
 * WEEK 10 TOPIC: a Service, and its lifecycle.
 *
 * This is the heart of the app. It owns the audio player and it is a
 * FOREGROUND service, which means:
 *
 *   - it keeps running after the user leaves the app or swipes it away,
 *   - Android requires it to show a notification the whole time, so the
 *     user always knows something is playing.
 *
 * Lifecycle in this app:
 *   onCreate()        -> build the player, register the noisy receiver
 *   onStartCommand()  -> a button was pressed somewhere; go foreground
 *   onBind()          -> an Activity wants to talk to us directly
 *   onDestroy()       -> release the player, unregister the receiver
 *
 * WEEK 10 TOPIC: communication between components. Activities bind to this
 * service and get the object itself back (see LocalBinder), so PlayerActivity
 * can call next() directly instead of firing intents for everything.
 *
 * @OptIn is needed because media3's ExoPlayer classes are still marked
 * @UnstableApi. It only silences the warning; nothing unusual is happening.
 */
@OptIn(markerClass = UnstableApi.class)
public class MusicService extends Service {

    // Actions the notification buttons and the sleep timer send us.
    public static final String ACTION_PLAY_PAUSE = "com.sur.player.action.PLAY_PAUSE";
    public static final String ACTION_PAUSE = "com.sur.player.action.PAUSE";
    public static final String ACTION_NEXT = "com.sur.player.action.NEXT";
    public static final String ACTION_PREV = "com.sur.player.action.PREV";
    public static final String ACTION_STOP = "com.sur.player.action.STOP";

    /** Sent by AirplaneModeReceiver when the network has just gone away. */
    public static final String ACTION_NETWORK_LOST = "com.sur.player.action.NETWORK_LOST";

    private static final int NOTIFICATION_ID = 42;

    private ExoPlayer player;

    /**
     * How many things failed to play since the last thing that worked, and
     * the limit before the service gives up instead of running down the
     * whole queue. See onPlayerError below.
     */
    private static final int MAX_FAILURES_IN_A_ROW = 3;

    /** Wait between skips, so a run of dead streams cannot spin at full speed. */
    private static final long SKIP_DELAY_MS = 600;

    /**
     * How long something must keep playing before we treat it as working.
     * A live stream often reports itself ready and then dies a moment later,
     * so "ready" on its own is not proof that anything worked.
     */
    private static final long SUCCESS_AFTER_MS = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private int failuresInARow = 0;

    /** Runs five seconds into playback that is still going. */
    private final Runnable markWorking = () -> failuresInARow = 0;

    /** Set when playback gives up, so the screen can say why. Read once. */
    private String lastError;
    private MediaSessionCompat mediaSession;
    private NoisyReceiver noisyReceiver;
    private AirplaneModeReceiver airplaneModeReceiver;

    private final IBinder binder = new LocalBinder();
    private Listener listener;

    /** How an Activity gets hold of this exact service object. */
    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    /** The UI implements this so it can redraw when playback changes. */
    public interface Listener {
        void onPlaybackChanged();
    }

    // =====================================================================
    // LIFECYCLE
    // =====================================================================

    @Override
    public void onCreate() {
        super.onCreate();

        // Tell Android this is music, and let ExoPlayer handle audio focus:
        // playback ducks for a notification and pauses for a phone call.
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(attributes, /* handleAudioFocus = */ true)
                // ExoPlayer could handle the headphone-unplug case for us with
                // .setHandleAudioBecomingNoisy(true). We deliberately leave it
                // off and write our own BroadcastReceiver instead, because
                // that is the week 10 topic being demonstrated.
                .build();

        player.addListener(new PlayerEvents());

        // MediaSession is what puts the controls on the lock screen and lets
        // Bluetooth headset buttons reach us.
        mediaSession = new MediaSessionCompat(this, "SurSession");
        // Tapping the lock screen card opens the player.
        mediaSession.setSessionActivity(playerIntent());
        // Without a callback the lock screen buttons and the button on a
        // Bluetooth headset have nowhere to go, so they do nothing.
        mediaSession.setCallback(new SessionButtons());
        mediaSession.setActive(true);

        // WEEK 10 TOPIC: a BroadcastReceiver registered IN CODE.
        // This one only matters while the service is alive, so it is created
        // here rather than declared in the manifest.
        noisyReceiver = new NoisyReceiver();
        IntentFilter filter = new IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        // ContextCompat picks the right registerReceiver() for the Android
        // version. NOT_EXPORTED means only the system can send us this.
        ContextCompat.registerReceiver(this, noisyReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);

        // The same pattern again, for airplane mode. See the comment at the
        // top of AirplaneModeReceiver for why this one cannot live in the
        // manifest on Android 8 and later.
        airplaneModeReceiver = new AirplaneModeReceiver();
        ContextCompat.registerReceiver(this, airplaneModeReceiver,
                new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Android gives a freshly started foreground service about five
        // seconds to show its notification, so this happens first.
        startForeground(NOTIFICATION_ID, buildNotification());

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY_PAUSE:
                    togglePlayPause();
                    break;
                case ACTION_PAUSE:
                    pause();
                    break;
                case ACTION_NEXT:
                    playNext(true);
                    break;
                case ACTION_PREV:
                    playPrevious();
                    break;
                case ACTION_NETWORK_LOST:
                    onNetworkLost();
                    break;
                case ACTION_STOP:
                    stopEverything();
                    return START_NOT_STICKY;
            }
        }

        // If Android kills us for memory, do not recreate the service with a
        // null intent - the user can start playback again themselves.
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        // Every registered receiver must be unregistered, or Android leaks it
        // and logs a warning.
        if (noisyReceiver != null) {
            unregisterReceiver(noisyReceiver);
            noisyReceiver = null;
        }
        if (airplaneModeReceiver != null) {
            unregisterReceiver(airplaneModeReceiver);
            airplaneModeReceiver = null;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        // Drop any skip or success check still waiting, so nothing runs
        // against a player that no longer exists.
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // =====================================================================
    // WHAT THE UI CALLS
    // =====================================================================

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Only clears the listener if it is still the one that asked.
     *
     * Without this check there is a real bug: opening PlayerActivity stops
     * MainActivity a moment LATER, and MainActivity's onStop would wipe the
     * listener PlayerActivity had just set, leaving the player screen frozen.
     */
    public void clearListener(Listener candidate) {
        if (this.listener == candidate) {
            this.listener = null;
        }
    }

    /** Replaces the queue and starts playing at the given position. */
    public void playQueue(List<Playable> items, int startIndex) {
        failuresInARow = 0;   // a fresh choice by the user
        PlaybackQueue.get().setQueue(items, startIndex);
        startCurrent();
    }

    /** Jumps to one position in the current queue. Used by the "Up next" list. */
    public void playAt(int index) {
        failuresInARow = 0;   // a fresh choice by the user
        Playable item = PlaybackQueue.get().jumpTo(index);
        if (item != null) startCurrent();
    }

    public void playSingle(Playable item) {
        failuresInARow = 0;   // a fresh choice by the user
        PlaybackQueue.get().setSingle(item);
        startCurrent();
    }

    /**
     * Airplane mode just came on.
     *
     * A song stored on the phone is unaffected, so it keeps playing and the
     * user sees nothing. Radio and podcasts come over the network, so there
     * is nothing left to play and the service stops and says why.
     */
    private void onNetworkLost() {
        Playable current = PlaybackQueue.get().current();

        // A Song is a local file. Anything else came from the internet.
        if (current == null || current instanceof Song) return;

        if (player != null) player.stop();
        lastError = getString(R.string.airplane_mode_stopped);
        refresh();
    }

    /**
     * The message from the last playback failure, or null if there was none.
     * Reading it clears it, so the warning is shown once and not every time
     * the screen refreshes.
     */
    @Nullable
    public String takeLastError() {
        String message = lastError;
        lastError = null;
        return message;
    }

    public void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
        } else {
            // Nothing loaded yet (for example after a restart): load the
            // current queue item first.
            if (player.getMediaItemCount() == 0) {
                startCurrent();
                return;
            }
            player.play();
        }
        refresh();
    }

    public void pause() {
        if (player != null && player.isPlaying()) {
            player.pause();
            refresh();
        }
    }

    public void playNext(boolean userPressed) {
        Playable next = PlaybackQueue.get().next(userPressed);
        if (next == null) {
            // Queue finished.
            if (player != null) player.pause();
            refresh();
            return;
        }
        startCurrent();
    }

    public void playPrevious() {
        // Standard behaviour: if more than three seconds in, restart the
        // track instead of skipping back.
        if (player != null && player.getCurrentPosition() > 3000) {
            player.seekTo(0);
            refresh();
            return;
        }
        PlaybackQueue.get().previous();
        startCurrent();
    }

    public void seekTo(long positionMs) {
        if (player != null) {
            player.seekTo(positionMs);
            refresh();
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public long getPosition() {
        return player == null ? 0 : Math.max(0, player.getCurrentPosition());
    }

    public long getDuration() {
        if (player == null) return 0;
        long duration = player.getDuration();
        // Live radio has no duration; ExoPlayer reports TIME_UNSET.
        return duration == C.TIME_UNSET ? 0 : duration;
    }

    public Playable getCurrent() {
        return PlaybackQueue.get().current();
    }

    public void stopEverything() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        stopForeground(true);
        stopSelf();
    }

    // =====================================================================
    // INTERNALS
    // =====================================================================

    /** Loads whatever the queue is pointing at and plays it. */
    private void startCurrent() {
        // A pending "this one works" from the previous item must not count
        // towards the new one.
        handler.removeCallbacks(markWorking);

        Playable item = PlaybackQueue.get().current();
        if (item == null || player == null) return;

        String uri = item.getPlayUri();
        if (uri == null || uri.isEmpty()) return;

        // ExoPlayer decides here, synchronously, how to read this address.
        // If it meets a streaming format no module on the classpath can
        // handle, it throws rather than reporting it through onPlayerError,
        // so a try/catch is the only thing that can stop it. The HLS and
        // DASH modules in build.gradle cover the formats radio actually
        // uses; this catch is the net under that, so an odd station can
        // never take the whole app down while the app is being shown.
        try {
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri));
            player.prepare();
            player.play();
        } catch (Exception unsupported) {
            lastError = getString(R.string.playback_failed);
            refresh();
            return;
        }

        // Write the listening history on a background thread - never touch
        // the database from a playback callback.
        if (item instanceof Song) {
            final Song song = (Song) item;
            final long songId = song.getId();
            AppExecutors.disk(new Runnable() {
                @Override
                public void run() {
                    SurApp.repo().notePlayed(songId);
                }
            });

            // WEEK 11: the same play, counted in the cloud as well, so the
            // Most played list survives a reinstall. Firebase does its own
            // threading, so this call does not need AppExecutors. It does
            // nothing at all when nobody is signed in.
            String userId = Accounts.userId();
            if (userId != null) PlayCounts.addPlay(userId, song);
        }

        // New track, so the lock screen needs new words and a new cover.
        updateSessionMetadata();
        refresh();
    }

    /** Redraw the notification and tell the Activity, if one is bound. */
    private void refresh() {
        updateSessionState();
        postNotification();
        if (listener != null) {
            listener.onPlaybackChanged();
        }
    }

    /**
     * The service may already be in the foreground; notify() just updates
     * the same notification in place.
     */
    private void postNotification() {
        androidx.core.app.NotificationManagerCompat manager =
                androidx.core.app.NotificationManagerCompat.from(this);
        try {
            manager.notify(NOTIFICATION_ID, buildNotification());
        } catch (SecurityException ignored) {
            // The user declined the POST_NOTIFICATIONS permission on
            // Android 13+. Playback still works; there is just no notification.
        }
    }

    // =====================================================================
    // MEDIA SESSION
    //
    // Marking the session active is only half the job. Android reads two
    // more things from it: a playback state, which says playing or paused
    // and where we are in the track, and metadata, which carries the title,
    // the artist and the cover. Without those the phone has an active
    // session that reports nothing, so the lock screen stays empty and the
    // media card never appears in the pull down shade.
    // =====================================================================

    /** Says whether we are playing, where we are, and which buttons to draw. */
    private void updateSessionState() {
        if (mediaSession == null) return;

        boolean playing = isPlaying();
        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_SEEK_TO;

        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(actions)
                // The speed lets the lock screen move the progress bar on
                // its own between updates, instead of only on each refresh.
                .setState(playing ? PlaybackStateCompat.STATE_PLAYING
                                  : PlaybackStateCompat.STATE_PAUSED,
                        getPosition(), playing ? 1f : 0f)
                .build());
    }

    /**
     * The title, artist, length and cover. Called when the track changes,
     * and again once the length is known, because a file reports its length
     * only after it is ready.
     */
    private void updateSessionMetadata() {
        if (mediaSession == null) return;

        Playable current = PlaybackQueue.get().current();
        if (current == null) {
            mediaSession.setMetadata(null);
            return;
        }

        final String title = current.getTitle();
        final String subtitle = current.getSubtitle();
        final String artUri = current.getArtUri();
        long duration = getDuration();

        final MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
                // Live radio has no length. -1 tells the lock screen to show
                // a plain bar rather than a wrong one.
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                        duration > 0 ? duration : -1);

        // Show the words now. The cover takes a disk read, so it follows.
        mediaSession.setMetadata(meta.build());

        if (artUri == null) return;
        AppExecutors.diskThenMain(() -> readArt(artUri), art -> {
            if (art == null || mediaSession == null) return;

            // The user may have skipped while the cover was being read.
            Playable nowPlaying = PlaybackQueue.get().current();
            if (nowPlaying == null || !title.equals(nowPlaying.getTitle())) return;

            mediaSession.setMetadata(
                    meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art).build());
            // Redraw so the shade picks up the cover too. Not refresh(),
            // which would come back here and loop.
            postNotification();
        });
    }

    /**
     * Reads the cover from MediaStore, shrunk on the way in. The picture
     * crosses a process boundary to the system UI, which has a size limit,
     * and a full size cover can be large enough to be dropped.
     */
    @Nullable
    private Bitmap readArt(String artUri) {
        try (java.io.InputStream in = getContentResolver().openInputStream(Uri.parse(artUri))) {
            if (in == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            return BitmapFactory.decodeStream(in, null, options);
        } catch (Exception noCover) {
            // A song with no cover, or one whose cover has gone. Not a problem.
            return null;
        }
    }

    /**
     * WEEK 10 TOPIC again, from the other side: these arrive from outside
     * the app. The lock screen buttons, the button on a Bluetooth headset
     * and a car stereo all land here.
     */
    private class SessionButtons extends MediaSessionCompat.Callback {
        // A car stereo can send Play while we are already playing. Toggling
        // there would pause it, which is the opposite of what was asked.
        @Override public void onPlay() { if (!isPlaying()) togglePlayPause(); }
        @Override public void onPause() { pause(); }
        @Override public void onSkipToNext() { playNext(true); }
        @Override public void onSkipToPrevious() { playPrevious(); }
        @Override public void onSeekTo(long position) { seekTo(position); }
        @Override public void onStop() { stopEverything(); }
    }

    /**
     * Builds the playback notification, including the three buttons.
     *
     * Each button is a PendingIntent aimed at PlaybackActionReceiver - our
     * own BroadcastReceiver - which forwards the action back to this service.
     */
    private android.app.Notification buildNotification() {
        Playable current = PlaybackQueue.get().current();
        String title = current == null ? getString(R.string.nothing_playing) : current.getTitle();
        String subtitle = current == null ? "" : current.getSubtitle();

        // Tapping the notification body opens the full player.
        PendingIntent contentIntent = playerIntent();

        boolean playing = isPlaying();

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, SurApp.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(subtitle)
                        .setContentIntent(contentIntent)
                        .setOnlyAlertOnce(true)
                        .setShowWhen(false)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .addAction(R.drawable.ic_prev, getString(R.string.previous),
                                actionIntent(ACTION_PREV))
                        .addAction(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                                getString(playing ? R.string.pause : R.string.play),
                                actionIntent(ACTION_PLAY_PAUSE))
                        .addAction(R.drawable.ic_next, getString(R.string.next),
                                actionIntent(ACTION_NEXT));

        // MediaStyle draws it like a media player and, with the session
        // token, puts the same controls on the lock screen.
        androidx.media.app.NotificationCompat.MediaStyle style =
                new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2);
        if (mediaSession != null) {
            style.setMediaSession(mediaSession.getSessionToken());
        }
        builder.setStyle(style);

        return builder.build();
    }

    /** Opens PlayerActivity. Used by the notification and the lock screen. */
    private PendingIntent playerIntent() {
        return PendingIntent.getActivity(
                this, 0,
                new Intent(this, PlayerActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent actionIntent(String action) {
        Intent intent = new Intent(this, PlaybackActionReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(
                this, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** ExoPlayer tells us when playback actually starts, stops or finishes. */
    private class PlayerEvents implements Player.Listener {

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) {
                // Start the clock. If it is still playing in five seconds,
                // markWorking clears the failure count.
                handler.removeCallbacks(markWorking);
                handler.postDelayed(markWorking, SUCCESS_AFTER_MS);
            } else {
                handler.removeCallbacks(markWorking);
            }
            refresh();
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
                // The track finished on its own, so repeat-one applies.
                playNext(false);
            } else {
                if (playbackState == Player.STATE_READY) {
                    // The length is only known once the file is ready, and
                    // the lock screen progress bar needs it.
                    updateSessionMetadata();
                }
                refresh();
            }
        }

        /**
         * Something would not play. For a song that usually means a missing
         * file. For radio it means a dead stream, which is common, because
         * the station list comes from a public directory that nobody prunes.
         *
         * Skipping to the next item is right, but only a few times. Without
         * the counter, one dead stream skips to the next, which is also
         * dead, and the app walks the whole list on its own.
         */
        @Override
        public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
            handler.removeCallbacks(markWorking);
            failuresInARow++;

            if (failuresInARow >= MAX_FAILURES_IN_A_ROW) {
                failuresInARow = 0;
                if (player != null) player.stop();
                lastError = getString(R.string.playback_failed);
                refresh();
                return;
            }

            // Posted rather than called here and now. Preparing the next item
            // inside the player's own error callback can fail again straight
            // away, stacking one error on top of another until the app dies.
            // The delay also keeps the skipping visible instead of a blur.
            handler.postDelayed(() -> playNext(true), SKIP_DELAY_MS);
        }
    }
}
