package com.sur.player;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.sur.player.databinding.ActivityMainBinding;
import com.sur.player.model.Playable;
import com.sur.player.model.Song;
import com.sur.player.service.MusicService;
import com.sur.player.ui.BoundActivity;
import com.sur.player.ui.LibraryFragment;
import com.sur.player.ui.ListenLiveFragment;
import com.sur.player.ui.PlaylistsFragment;
import com.sur.player.util.LetterTile;

import java.util.ArrayList;
import java.util.List;

/**
 * The one screen the user actually lives in.
 *
 * WEEK 8 TOPICS on this class:
 *   - view binding (no findViewById anywhere)
 *   - click listeners on the mini player
 *   - an EXPLICIT intent to PlayerActivity and SettingsActivity
 *   - runtime permissions
 *
 * WEEK 9: it hosts the three tab fragments and the reusable mini-player
 * include, and swaps fragments with the FragmentManager.
 */
public class MainActivity extends BoundActivity {

    private ActivityMainBinding views;

    /** Asks for one or more permissions and gives the answer back here. */
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        // Whatever the user chose, let the visible tab redraw.
                        Fragment current = getSupportFragmentManager()
                                .findFragmentById(R.id.fragmentContainer);
                        if (current instanceof LibraryFragment) {
                            ((LibraryFragment) current).reload();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        views = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(views.getRoot());

        views.toolbar.inflateMenu(R.menu.main_menu);
        views.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                // EXPLICIT intent: we name the exact class we want to open.
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return true;
            }
            return false;
        });

        // ---- tabs --------------------------------------------------------
        views.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_library) return show(new LibraryFragment());
            if (id == R.id.nav_playlists) return show(new PlaylistsFragment());
            if (id == R.id.nav_live) return show(new ListenLiveFragment());
            return false;
        });

        if (savedInstanceState == null) {
            views.bottomNav.setSelectedItemId(R.id.nav_library);
        }

        // ---- mini player -------------------------------------------------
        views.miniPlayer.miniPlayerRoot.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, PlayerActivity.class)));

        views.miniPlayer.miniPlayPause.setOnClickListener(v -> {
            MusicService service = getMusicService();
            if (service != null) service.togglePlayPause();
        });

        views.miniPlayer.miniNext.setOnClickListener(v -> {
            MusicService service = getMusicService();
            if (service != null) service.playNext(true);
        });

        askForPermissions();
    }

    /**
     * The id of the song playing now, or -1 when nothing is playing or when
     * the thing playing is a radio station rather than a song. Fragments ask
     * for this when they become visible.
     */
    public long currentSongId() {
        MusicService service = getMusicService();
        Playable current = service == null ? null : service.getCurrent();
        return current instanceof Song ? ((Song) current).getId() : -1L;
    }

    private boolean show(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        return true;
    }

    // =====================================================================
    // PERMISSIONS (week 10)
    // =====================================================================

    /**
     * Android 13 replaced READ_EXTERNAL_STORAGE with per-type permissions,
     * and made notifications something you have to ask for. So the list of
     * permissions depends on the version the phone is running.
     */
    public void askForPermissions() {
        List<String> wanted = new ArrayList<>();

        if (!hasAudioPermission()) {
            wanted.add(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? Manifest.permission.READ_MEDIA_AUDIO
                    : Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!wanted.isEmpty()) {
            permissionLauncher.launch(wanted.toArray(new String[0]));
        }
    }

    public boolean hasAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    // =====================================================================
    // PLAYBACK
    // =====================================================================

    /** Called by the fragments when the user taps something playable. */
    public void play(List<Playable> queue, int index) {
        MusicService service = getMusicService();
        if (service == null) return;

        // Promote the service to the foreground first - that is what lets
        // playback survive the app being closed - then hand it the queue.
        com.sur.player.service.ServiceStarter.wake(this);
        service.playQueue(queue, index);
    }

    /** Keeps the mini player in step with the service. */
    @Override
    public void onPlaybackChanged() {
        MusicService service = getMusicService();
        if (service == null) return;

        showPlaybackErrorIfAny();

        // Tell the Library tab which row should show the bouncing bars.
        Fragment shown = getSupportFragmentManager()
                .findFragmentById(R.id.fragmentContainer);
        if (shown instanceof LibraryFragment) {
            ((LibraryFragment) shown).setPlayingId(currentSongId());
        }

        Playable current = service.getCurrent();
        if (current == null) {
            views.miniPlayer.miniPlayerRoot.setVisibility(View.GONE);
            return;
        }

        views.miniPlayer.miniPlayerRoot.setVisibility(View.VISIBLE);
        views.miniPlayer.miniTitle.setText(current.getTitle());
        views.miniPlayer.miniArtist.setText(current.getSubtitle());
        views.miniPlayer.miniPlayPause.setImageResource(
                service.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);

        // Artwork, falling back to a coloured letter tile. Radio stations
        // and podcast episodes have no cover, so they always get the tile.
        android.graphics.drawable.Drawable tile =
                LetterTile.forTitle(this, current.getTitle());
        Glide.with(this)
                .load(current instanceof Song ? ((Song) current).getArtUri() : null)
                .placeholder(tile)
                .error(tile)
                .into(views.miniPlayer.miniArt);

        updateMiniProgress();
    }

    // =====================================================================
    // THE PROGRESS LINE ON THE MINI PLAYER
    // =====================================================================

    /**
     * The same pattern PlayerActivity uses for its seek ring: a repeating
     * message on the main thread while the screen is in front of the user,
     * stopped the moment it is not. A hidden screen costs nothing.
     */
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private boolean ticking = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateMiniProgress();
            if (ticking) ticker.postDelayed(this, 500);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        ticking = true;
        ticker.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ticking = false;
        ticker.removeCallbacks(tick);
    }

    private void updateMiniProgress() {
        MusicService service = getMusicService();
        if (service == null || isDestroyed()) return;

        long duration = service.getDuration();
        long position = service.getPosition();

        // A live radio stream has no duration, so there is no progress to
        // show and the line stays empty rather than jumping around.
        int percent = duration > 0 ? (int) (position * 100 / duration) : 0;
        views.miniPlayer.miniProgress.setProgress(percent);
    }
}
