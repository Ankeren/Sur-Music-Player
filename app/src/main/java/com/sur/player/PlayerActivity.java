package com.sur.player;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.sur.player.databinding.ActivityPlayerBinding;
import com.sur.player.databinding.DialogQueueBinding;
import com.sur.player.ui.QueueAdapter;
import com.sur.player.model.Playable;
import com.sur.player.model.Song;
import com.sur.player.service.MusicService;
import com.sur.player.service.PlaybackQueue;
import com.sur.player.service.SleepTimer;
import com.sur.player.ui.BoundActivity;
import com.sur.player.util.AppExecutors;
import com.sur.player.util.ArtworkTheme;
import com.sur.player.util.LetterTile;
import com.sur.player.util.TimeUtil;

import java.util.List;

/**
 * The full-screen player.
 *
 * WEEK 8 TOPIC: the Activity lifecycle, used for something real.
 *   onStart  -> BoundActivity binds to the service (inherited)
 *   onResume -> start the half-second ticker that moves the seek ring
 *   onPause  -> stop the ticker, so a hidden screen costs nothing
 *   onStop   -> BoundActivity unbinds (inherited)
 *
 * Note what this class does NOT do: it never touches ExoPlayer and never
 * holds the song itself. It asks the service, and the service answers.
 * Rotating the phone throws this Activity away and builds the landscape
 * one, and the music does not even stutter, because the audio lives in the
 * service rather than in the screen.
 */
public class PlayerActivity extends BoundActivity {

    private ActivityPlayerBinding views;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private boolean ticking = false;

    /** Runs itself again every 500 ms while the screen is in front. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (ticking) ticker.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        views = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(views.getRoot());

        views.backButton.setOnClickListener(v -> finish());

        // The custom seek ring draws with plain Paint objects, which do not
        // know about themes, so we hand it the current accent color.
        views.seekBar.setAccentColor(MaterialColors.getColor(
                views.seekBar, com.google.android.material.R.attr.colorPrimary));

        views.playPauseButton.setOnClickListener(v -> {
            MusicService service = getMusicService();
            if (service != null) service.togglePlayPause();
        });

        views.nextButton.setOnClickListener(v -> {
            MusicService service = getMusicService();
            if (service != null) service.playNext(true);
        });

        views.prevButton.setOnClickListener(v -> {
            MusicService service = getMusicService();
            if (service != null) service.playPrevious();
        });

        views.shuffleButton.setOnClickListener(v -> {
            PlaybackQueue queue = PlaybackQueue.get();
            queue.setShuffle(!queue.isShuffle());
            updateModeIcons();
        });

        views.repeatButton.setOnClickListener(v -> {
            PlaybackQueue.get().cycleRepeatMode();
            updateModeIcons();
            if (PlaybackQueue.get().getRepeatMode() == PlaybackQueue.REPEAT_ONE) {
                Toast.makeText(this, "Repeat one", Toast.LENGTH_SHORT).show();
            }
        });

        views.favouriteButton.setOnClickListener(v -> toggleFavourite());

        views.timerButton.setOnClickListener(v -> showSleepTimerDialog());
        views.queueButton.setOnClickListener(v -> showQueue());

        // The custom view reports a fraction; we turn it into milliseconds.
        views.seekBar.setListener(fraction -> {
            MusicService service = getMusicService();
            if (service == null) return;
            long duration = service.getDuration();
            if (duration > 0) {
                service.seekTo((long) (fraction * duration));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ticking = true;
        ticker.post(tick);
    }

    @Override
    protected void onPause() {
        ticking = false;
        ticker.removeCallbacks(tick);
        super.onPause();
    }

    // =====================================================================
    // DRAWING THE CURRENT STATE
    // =====================================================================

    @Override
    protected void onServiceReady(MusicService service) {
        updateModeIcons();
    }

    @Override
    public void onPlaybackChanged() {
        showPlaybackErrorIfAny();
        MusicService service = getMusicService();
        if (service == null || isDestroyed()) return;

        Playable current = service.getCurrent();
        if (current == null) {
            views.title.setText(R.string.nothing_playing);
            views.artist.setText("");
            return;
        }

        views.title.setText(current.getTitle());
        views.artist.setText(current.getSubtitle());
        views.playPauseButton.setImageResource(
                service.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);

        if (current instanceof Song) {
            Song song = (Song) current;
            // asBitmap, because we need the pixels themselves to work out the
            // colours, not just something to show in the ImageView.
            Glide.with(this)
                    .asBitmap()
                    .load(song.getArtUri())
                    .into(artTarget);
            views.favouriteButton.setImageResource(song.isFavourite()
                    ? R.drawable.ic_favourite : R.drawable.ic_favourite_border);
            views.favouriteButton.setVisibility(View.VISIBLE);
        } else {
            // Radio and shows have no artwork and cannot be favourited here.
            views.art.setImageDrawable(currentLetterTile());
            views.favouriteButton.setVisibility(View.INVISIBLE);
            clearArtworkTheme();
        }

        updateProgress();
    }

    private void updateProgress() {
        MusicService service = getMusicService();
        if (service == null || isDestroyed()) return;

        long position = service.getPosition();
        long duration = service.getDuration();

        views.elapsed.setText(TimeUtil.format(position));
        // Live radio reports no duration, so show a dash instead of 0:00.
        views.total.setText(duration > 0 ? TimeUtil.format(duration) : "--:--");
        views.seekBar.setProgress(position, duration);
    }

    private void updateModeIcons() {
        if (isDestroyed()) return;
        PlaybackQueue queue = PlaybackQueue.get();

        views.shuffleButton.setAlpha(queue.isShuffle() ? 1f : 0.4f);

        switch (queue.getRepeatMode()) {
            case PlaybackQueue.REPEAT_OFF:
                views.repeatButton.setAlpha(0.4f);
                break;
            case PlaybackQueue.REPEAT_ALL:
                views.repeatButton.setAlpha(1f);
                break;
            case PlaybackQueue.REPEAT_ONE:
                views.repeatButton.setAlpha(1f);
                break;
            default:
                break;
        }
    }

    // =====================================================================
    // ACTIONS
    // =====================================================================

    private void toggleFavourite() {
        MusicService service = getMusicService();
        if (service == null) return;

        Playable current = service.getCurrent();
        if (!(current instanceof Song)) return;

        final Song song = (Song) current;
        final boolean newValue = !song.isFavourite();
        song.setFavourite(newValue);

        views.favouriteButton.setImageResource(
                newValue ? R.drawable.ic_favourite : R.drawable.ic_favourite_border);

        AppExecutors.disk(() -> SurApp.repo().setFavourite(song.getId(), newValue));
    }

    // =====================================================================
    // COLOURS FROM THE ALBUM ART
    // =====================================================================

    /**
     * One target object, reused for every song, rather than a new one each
     * time. Glide cancels the previous request when the same target is used
     * again, so a slow load for an old song can never arrive late and put
     * the wrong artwork on screen.
     */
    private final CustomTarget<Bitmap> artTarget = new CustomTarget<Bitmap>() {

        @Override
        public void onResourceReady(@NonNull Bitmap art,
                                    @Nullable Transition<? super Bitmap> transition) {
            if (PlayerActivity.this.isDestroyed()) return;
            views.art.setImageBitmap(art);
            applyArtworkTheme(art);
        }

        @Override
        public void onLoadFailed(@Nullable Drawable errorDrawable) {
            if (PlayerActivity.this.isDestroyed()) return;
            views.art.setImageDrawable(currentLetterTile());
            clearArtworkTheme();
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholder) {
            if (PlayerActivity.this.isDestroyed()) return;
            views.art.setImageDrawable(currentLetterTile());
        }
    };

    /**
     * Repaints the screen to match the cover that just loaded: the seek ring
     * and the title take its strongest colour, and the background becomes
     * the same image blurred.
     *
     * Palette reads the image on a background thread and calls back on the
     * main thread, so a large cover never freezes the screen.
     */
    private void applyArtworkTheme(Bitmap art) {
        final int fallbackAccent = themeColour(com.google.android.material.R.attr.colorPrimary);
        final int base = themeColour(com.google.android.material.R.attr.colorSurface);

        Palette.from(art).maximumColorCount(16).generate(palette -> {
            // The user may have left the screen while Palette was working.
            if (isDestroyed()) return;

            int accent = ArtworkTheme.accentFrom(palette, fallbackAccent);
            applyAccent(accent);
            views.getRoot().setBackground(
                    ArtworkTheme.backgroundFrom(getResources(), art, accent, base));
        });
    }

    /** Puts the plain theme colours back, for radio or a missing cover. */
    private void clearArtworkTheme() {
        if (isDestroyed()) return;
        int accent = themeColour(com.google.android.material.R.attr.colorPrimary);
        applyAccent(accent);
        views.getRoot().setBackground(ArtworkTheme.plainBackground(
                themeColour(com.google.android.material.R.attr.colorSurface)));
    }

    /**
     * Paints the two things that carry the colour: the progress line and
     * the filled play button. The title stays plain white, because white on
     * a dark cover reads better than a tinted title, and the colour has
     * somewhere better to go.
     */
    private void applyAccent(int accent) {
        views.seekBar.setAccentColor(accent);
        views.playPauseButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(accent));
    }

    private int themeColour(int attribute) {
        return MaterialColors.getColor(views.getRoot(), attribute);
    }

    /** The coloured letter shown when a track has no cover of its own. */
    private Drawable currentLetterTile() {
        MusicService service = getMusicService();
        Playable current = service == null ? null : service.getCurrent();
        return LetterTile.forTitle(this, current == null ? null : current.getTitle());
    }

    /**
     * The "Up next" sheet: the playback queue as a list, with the playing
     * track highlighted. Tapping a row jumps to it.
     *
     * PlaybackQueue.snapshot() gives us a copy of the queue. The queue is
     * shared with the service thread, so we never hold its real list.
     */
    private void showQueue() {
        final MusicService service = getMusicService();
        if (service == null) return;

        List<Playable> items = PlaybackQueue.get().snapshot();
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.queue_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        int current = PlaybackQueue.get().getIndex();

        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        DialogQueueBinding sheetViews = DialogQueueBinding.inflate(getLayoutInflater());
        sheet.setContentView(sheetViews.getRoot());

        sheetViews.queueList.setLayoutManager(new LinearLayoutManager(this));
        sheetViews.queueList.setAdapter(new QueueAdapter(items, current, position -> {
            service.playAt(position);
            sheet.dismiss();
        }));
        // Start the list just above the playing track, so it is in view.
        sheetViews.queueList.scrollToPosition(Math.max(0, current - 1));

        sheet.show();
    }

    private void showSleepTimerDialog() {
        final int[] minutes = {15, 30, 45, 60};
        String[] labels = {"15 minutes", "30 minutes", "45 minutes", "1 hour",
                getString(R.string.sleep_timer_off)};

        new AlertDialog.Builder(this)
                .setTitle(R.string.sleep_timer)
                .setItems(labels, (dialog, which) -> {
                    if (which == labels.length - 1) {
                        SleepTimer.cancel(this);
                        Toast.makeText(this, R.string.sleep_timer_off, Toast.LENGTH_SHORT).show();
                    } else {
                        SleepTimer.start(this, minutes[which]);
                        Toast.makeText(this,
                                getString(R.string.sleep_timer_set, minutes[which]),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
}
