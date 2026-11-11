package com.sur.player;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.sur.player.databinding.ActivitySettingsBinding;
import com.sur.player.firebase.Accounts;
import com.sur.player.firebase.CloudBackup;
import com.sur.player.model.Playlist;
import com.sur.player.model.Song;
import com.sur.player.service.SleepTimer;
import com.sur.player.util.AppExecutors;
import com.sur.player.util.ThemeHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A short settings screen: the sleep timer, a library rescan, and the two
 * Firebase buttons.
 *
 * This Activity does NOT extend BoundActivity, because nothing here needs
 * to talk to the player directly - the sleep timer goes through
 * AlarmManager and a BroadcastReceiver instead.
 */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding views;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);   // must come before super.onCreate()
        super.onCreate(savedInstanceState);
        views = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(views.getRoot());

        views.toolbar.setNavigationOnClickListener(v -> finish());

        views.sleepTimerButton.setOnClickListener(v -> showSleepTimerDialog());

        views.accentStatus.setText(getString(R.string.accent_current,
                ThemeHelper.NAMES[ThemeHelper.getAccent(this)]));
        views.accentButton.setOnClickListener(v -> showAccentDialog());

        views.rescanButton.setOnClickListener(v -> {
            Toast.makeText(this, R.string.scanning, Toast.LENGTH_SHORT).show();
            AppExecutors.diskThenMain(
                    () -> SurApp.repo().rescanLibrary(),
                    count -> Toast.makeText(SettingsActivity.this,
                            getString(R.string.rescan_done, count),
                            Toast.LENGTH_SHORT).show());
        });

        // --- FIREBASE (week 11) --------------------------------------------
        views.accountButton.setOnClickListener(v -> {
            if (Accounts.isSignedIn()) {
                Accounts.signOut();
                LoginActivity.clearSkip(this);   // so the sign in screen returns
                showAccountStatus();
                Toast.makeText(this, R.string.signed_out, Toast.LENGTH_SHORT).show();
            } else {
                // EXTRA_FORCE tells LoginActivity this is a deliberate visit,
                // so it shows the form even if the user skipped it before.
                Intent intent = new Intent(this, LoginActivity.class);
                intent.putExtra(LoginActivity.EXTRA_FORCE, true);
                startActivity(intent);
            }
        });

        views.backupButton.setOnClickListener(v -> backupPlaylists());
        views.restoreButton.setOnClickListener(v -> restorePlaylists());
        views.mostPlayedButton.setOnClickListener(v ->
                startActivity(new Intent(this, MostPlayedActivity.class)));
        // -------------------------------------------------------------------
    }

    @Override
    protected void onResume() {
        super.onResume();
        showTimerStatus();
        showAccountStatus();
    }

    // =====================================================================
    // WEEK 11: FIREBASE
    // =====================================================================

    private void showAccountStatus() {
        boolean signedIn = Accounts.isSignedIn();

        if (!Accounts.isAvailable()) {
            views.accountStatus.setText(R.string.backup_not_configured);
        } else if (signedIn) {
            views.accountStatus.setText(getString(R.string.signed_in_as, Accounts.email()));
        } else {
            views.accountStatus.setText(R.string.not_signed_in);
        }

        views.accountButton.setText(signedIn ? R.string.sign_out : R.string.sign_in);

        // The cloud buttons do nothing useful without an account, so they are
        // greyed out rather than letting the user press them and get an error.
        views.backupButton.setEnabled(signedIn);
        views.restoreButton.setEnabled(signedIn);
        views.mostPlayedButton.setEnabled(signedIn);
    }

    /**
     * Reads every playlist and its songs off the Room database on a
     * background thread, then hands the whole lot to Firestore in one batch.
     * The database work must not happen on the main thread, which is why it
     * goes through AppExecutors first.
     */
    private void backupPlaylists() {
        final String userId = Accounts.userId();
        if (userId == null) return;

        Toast.makeText(this, R.string.backing_up, Toast.LENGTH_SHORT).show();

        AppExecutors.disk(() -> {
            List<Playlist> playlists = SurApp.repo().getPlaylists();
            Map<Long, List<Song>> songs = new HashMap<>();
            for (Playlist playlist : playlists) {
                songs.put(playlist.getId(), SurApp.repo().getSongsInPlaylist(playlist.getId()));
            }

            CloudBackup.backup(userId, playlists, songs, (success, count, errorMessage) ->
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        Toast.makeText(this, success
                                        ? getString(R.string.backup_done, count)
                                        : getString(R.string.backup_failed, errorMessage),
                                Toast.LENGTH_LONG).show();
                    }));
        });
    }

    /**
     * Pulls the playlists back out of Firestore and writes them into Room.
     *
     * Songs are matched by their file path, so a song that is no longer on
     * the phone is skipped rather than creating a broken entry. The count of
     * what was actually restored is reported, so nothing fails silently.
     */
    /**
     * Says what restoring actually did. Counting playlists alone was
     * misleading, because one could come back with none of its songs if the
     * files are no longer on this phone, and still be reported as restored.
     */
    private String describeRestore(int restored, int songsAdded, int skipped) {
        if (restored == 0 && skipped > 0) {
            return getString(R.string.restore_nothing_new);
        }
        if (restored == 0) {
            return getString(R.string.restore_empty);
        }

        String message = getString(R.string.restore_done, restored, songsAdded);
        if (skipped > 0) {
            message += " " + getString(R.string.restore_skipped, skipped);
        }
        return message;
    }

    private void restorePlaylists() {
        final String userId = Accounts.userId();
        if (userId == null) return;

        Toast.makeText(this, R.string.restoring, Toast.LENGTH_SHORT).show();

        CloudBackup.restore(userId, (success, playlists, errorMessage) -> {
            if (!success || playlists == null) {
                Toast.makeText(this, getString(R.string.restore_failed, errorMessage),
                        Toast.LENGTH_LONG).show();
                return;
            }

            final List<CloudBackup.RestoredPlaylist> fromCloud = playlists;

            AppExecutors.diskThenMain(() -> {
                // Build a lookup of what is actually on this phone right now.
                Map<String, Long> idByUri = new HashMap<>();
                for (Song song : SurApp.repo().getAllSongs()) {
                    idByUri.put(song.getUri(), song.getId());
                }

                // Names already on this phone. Restoring is meant to bring
                // back what is missing, not to make a second copy of what is
                // already here, so anything matching by name is left alone.
                // Without this, tapping Restore twice gives three copies.
                Set<String> alreadyHere = new HashSet<>();
                for (Playlist playlist : SurApp.repo().getPlaylists()) {
                    alreadyHere.add(playlist.getName().toLowerCase(Locale.ROOT));
                }

                int restored = 0;
                int skipped = 0;
                int songsAdded = 0;

                for (CloudBackup.RestoredPlaylist playlist : fromCloud) {
                    if (alreadyHere.contains(playlist.name.toLowerCase(Locale.ROOT))) {
                        skipped++;
                        continue;
                    }

                    long newId = SurApp.repo().createPlaylist(playlist.name);
                    for (String uri : playlist.songUris) {
                        // A song backed up from another phone, or since
                        // deleted, has no match here and is passed over.
                        Long songId = idByUri.get(uri);
                        if (songId != null) {
                            SurApp.repo().addSongToPlaylist(newId, songId);
                            songsAdded++;
                        }
                    }
                    restored++;
                }
                return describeRestore(restored, songsAdded, skipped);
            }, message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
        });
    }

    private void showTimerStatus() {
        int left = SleepTimer.minutesLeft(this);
        views.sleepTimerStatus.setText(left == 0
                ? getString(R.string.sleep_timer_off)
                : getString(R.string.sleep_timer_set, left));
    }

    /** A single choice list. Picking a color saves it and redraws this screen. */
    private void showAccentDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.accent_color)
                .setSingleChoiceItems(ThemeHelper.NAMES, ThemeHelper.getAccent(this),
                        (dialog, which) -> {
                            ThemeHelper.setAccent(this, which);
                            dialog.dismiss();
                            // The theme is read in onCreate, so rebuild the screen.
                            recreate();
                        })
                .show();
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
                    } else {
                        SleepTimer.start(this, minutes[which]);
                    }
                    showTimerStatus();
                })
                .show();
    }
}
