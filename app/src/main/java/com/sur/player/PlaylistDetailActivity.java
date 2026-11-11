package com.sur.player;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import com.sur.player.databinding.ActivityPlaylistDetailBinding;
import com.sur.player.model.Playable;
import com.sur.player.model.Song;
import com.sur.player.service.MusicService;
import com.sur.player.service.ServiceStarter;
import com.sur.player.ui.BoundActivity;
import com.sur.player.ui.SongAdapter;
import com.sur.player.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

/**
 * The songs inside one playlist.
 *
 * WEEK 8 TOPIC: receiving data through an explicit Intent. PlaylistsFragment
 * put the playlist id in an extra; we read it back in onCreate.
 *
 * WEEK 6 TOPIC: this whole screen is the JOIN across playlist_songs doing
 * its job - getSongsIn(id) walks the junction table to the songs table.
 */
public class PlaylistDetailActivity extends BoundActivity implements SongAdapter.Callbacks {

    public static final String EXTRA_PLAYLIST_ID = "playlist_id";

    private ActivityPlaylistDetailBinding views;
    private SongAdapter adapter;
    private long playlistId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        views = ActivityPlaylistDetailBinding.inflate(getLayoutInflater());
        setContentView(views.getRoot());

        playlistId = getIntent().getLongExtra(EXTRA_PLAYLIST_ID, -1L);
        if (playlistId == -1L) {
            finish();   // opened without an id: nothing to show
            return;
        }

        views.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new SongAdapter(this);
        views.songList.setLayoutManager(new LinearLayoutManager(this));
        views.songList.setAdapter(adapter);
        new ItemTouchHelper(touchCallback).attachToRecyclerView(views.songList);
        views.toolbar.setSubtitle(R.string.playlist_hint);

        views.playAllButton.setOnClickListener(v -> {
            if (adapter.getItemCount() > 0) playFrom(0);
        });

        load();
    }

    // =====================================================================
    // DRAG AND SWIPE
    // =====================================================================

    /**
     * WEEK 9 TOPIC: gestures on a RecyclerView, using ItemTouchHelper.
     *
     * Hold a row and drag it up or down to reorder. Swipe it left or right
     * to remove it from the playlist. The helper handles the touch tracking
     * and the animation; we only answer three questions:
     *   onMove   - a row was dragged over another row, so swap them
     *   onSwiped - a row was swiped away, so delete it
     *   clearView- the finger lifted, so save the new order
     */
    private boolean orderChanged = false;

    private final ItemTouchHelper.SimpleCallback touchCallback =
            new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                    ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView,
                                      @NonNull RecyclerView.ViewHolder dragged,
                                      @NonNull RecyclerView.ViewHolder target) {
                    int from = dragged.getBindingAdapterPosition();
                    int to = target.getBindingAdapterPosition();
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                        return false;
                    }
                    adapter.moveItem(from, to);
                    orderChanged = true;
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                    int position = holder.getBindingAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) return;
                    removeWithUndo(position);
                }

                @Override
                public void clearView(@NonNull RecyclerView recyclerView,
                                      @NonNull RecyclerView.ViewHolder holder) {
                    super.clearView(recyclerView, holder);
                    // The finger lifted. Write the order once, not on every swap.
                    if (orderChanged) {
                        orderChanged = false;
                        saveOrder();
                    }
                }
            };

    /** Writes the current on-screen order into the playlist_songs table. */
    private void saveOrder() {
        final List<Song> order = new ArrayList<>(adapter.getItems());
        AppExecutors.disk(() -> SurApp.repo().savePlaylistOrder(playlistId, order));
    }

    private void removeWithUndo(final int position) {
        final Song removed = adapter.removeAt(position);
        final long songId = removed.getId();
        views.emptyText.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);

        AppExecutors.disk(() -> SurApp.repo().removeSongFromPlaylist(playlistId, songId));

        Snackbar.make(views.getRoot(), R.string.song_removed, Snackbar.LENGTH_LONG)
                .setAnchorView(views.playAllButton)
                .setAction(R.string.undo, v -> {
                    adapter.insertAt(Math.min(position, adapter.getItemCount()), removed);
                    views.emptyText.setVisibility(View.GONE);
                    final List<Song> order = new ArrayList<>(adapter.getItems());
                    // One task does both steps, so they cannot run out of order:
                    // add the row back (it lands at the end), then fix its place.
                    AppExecutors.disk(() -> {
                        SurApp.repo().addSongToPlaylist(playlistId, songId);
                        SurApp.repo().savePlaylistOrder(playlistId, order);
                    });
                })
                .show();
    }

    private void load() {
        AppExecutors.diskThenMain(
                () -> {
                    // Two reads: the playlist row for its name, and the JOIN
                    // for its songs.
                    Object[] pair = new Object[2];
                    pair[0] = SurApp.repo().getPlaylist(playlistId);
                    pair[1] = SurApp.repo().getSongsInPlaylist(playlistId);
                    return pair;
                },
                pair -> {
                    if (isDestroyed()) return;

                    com.sur.player.model.Playlist playlist =
                            (com.sur.player.model.Playlist) pair[0];
                    @SuppressWarnings("unchecked")
                    List<Song> songs = (List<Song>) pair[1];

                    views.toolbar.setTitle(playlist == null ? "" : playlist.getName());
                    adapter.submit(songs);
                    views.emptyText.setVisibility(songs.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    // =====================================================================
    // TAPS
    // =====================================================================

    @Override
    public void onSongClick(int position) {
        playFrom(position);
    }

    private void playFrom(int position) {
        MusicService service = getMusicService();
        if (service == null) return;

        List<Playable> queue = new ArrayList<>(adapter.getItems());
        ServiceStarter.wake(this);
        service.playQueue(queue, position);
    }

    @Override
    public void onSongMenu(Song song, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.remove_from_playlist);

        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                AppExecutors.diskThenMain(
                        () -> {
                            SurApp.repo().removeSongFromPlaylist(playlistId, song.getId());
                            return SurApp.repo().getSongsInPlaylist(playlistId);
                        },
                        songs -> {
                            if (isDestroyed()) return;
                            adapter.submit(songs);
                            views.emptyText.setVisibility(
                                    songs.isEmpty() ? View.VISIBLE : View.GONE);
                        });
                return true;
            }
            return false;
        });
        menu.show();
    }
}
