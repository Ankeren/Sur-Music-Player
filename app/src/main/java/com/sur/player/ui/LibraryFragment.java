package com.sur.player.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sur.player.MainActivity;
import com.sur.player.R;
import com.sur.player.SurApp;
import com.sur.player.databinding.FragmentLibraryBinding;
import com.sur.player.model.Playable;
import com.sur.player.model.Playlist;
import com.sur.player.model.Song;
import com.sur.player.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab 1: every song on the phone.
 *
 * Watch the shape of every data call in this file: the slow part runs on a
 * background thread, and only the last step - touching a View - happens on
 * the main thread. That is AppExecutors.diskThenMain(...) and it is the
 * pattern used everywhere in the app (week 4 and week 11).
 */
public class LibraryFragment extends Fragment implements SongAdapter.Callbacks {

    private FragmentLibraryBinding views;
    private SongAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        views = FragmentLibraryBinding.inflate(inflater, container, false);
        return views.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back to this tab, ask who is playing so the bouncing bars
        // appear on the right row straight away.
        if (adapter != null && getActivity() instanceof MainActivity) {
            adapter.setPlayingId(((MainActivity) getActivity()).currentSongId());
        }
    }

    /** Called by MainActivity whenever the playing track changes. */
    public void setPlayingId(long songId) {
        if (adapter != null) adapter.setPlayingId(songId);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new SongAdapter(this);
        views.songList.setLayoutManager(new LinearLayoutManager(requireContext()));
        views.songList.setAdapter(adapter);

        views.grantButton.setOnClickListener(v ->
                ((MainActivity) requireActivity()).askForPermissions());

        // Search as the user types.
        views.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                search(s.toString());
            }
        });

        reload();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Release the binding so the fragment does not hold on to dead views.
        views = null;
    }

    // =====================================================================
    // LOADING
    // =====================================================================

    /** Called on first show, and again after the user grants permission. */
    public void reload() {
        if (views == null) return;

        if (!((MainActivity) requireActivity()).hasAudioPermission()) {
            showMessage(getString(R.string.permission_needed), true);
            return;
        }

        views.progress.setVisibility(View.VISIBLE);
        views.emptyText.setVisibility(View.GONE);
        views.grantButton.setVisibility(View.GONE);

        AppExecutors.diskThenMain(
                () -> {
                    // First run: our table is empty, so scan MediaStore.
                    List<Song> songs = SurApp.repo().getAllSongs();
                    if (songs.isEmpty()) {
                        SurApp.repo().rescanLibrary();
                        songs = SurApp.repo().getAllSongs();
                    }
                    return songs;
                },
                songs -> {
                    if (views == null) return;
                    views.progress.setVisibility(View.GONE);
                    adapter.submit(songs);
                    if (songs.isEmpty()) {
                        showMessage(getString(R.string.no_songs), false);
                    } else {
                        views.emptyText.setVisibility(View.GONE);
                    }
                });
    }

    private void search(String keyword) {
        AppExecutors.diskThenMain(
                () -> SurApp.repo().searchSongs(keyword),
                songs -> {
                    if (views == null) return;
                    adapter.submit(songs);
                });
    }

    private void showMessage(String message, boolean showGrant) {
        views.progress.setVisibility(View.GONE);
        views.emptyText.setText(message);
        views.emptyText.setVisibility(View.VISIBLE);
        views.grantButton.setVisibility(showGrant ? View.VISIBLE : View.GONE);
    }

    // =====================================================================
    // TAPS
    // =====================================================================

    @Override
    public void onSongClick(int position) {
        // The whole visible list becomes the queue, starting at this song.
        List<Playable> queue = new ArrayList<>(adapter.getItems());
        ((MainActivity) requireActivity()).play(queue, position);
    }

    @Override
    public void onSongMenu(Song song, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, song.isFavourite()
                ? "Remove from favourites" : "Add to favourites");
        menu.getMenu().add(0, 2, 1, getString(R.string.add_to_playlist));

        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                toggleFavourite(song);
                return true;
            }
            if (item.getItemId() == 2) {
                choosePlaylistFor(song);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void toggleFavourite(Song song) {
        final boolean newValue = !song.isFavourite();

        // Read the search box HERE, on the main thread. Touching a View from
        // a background thread is exactly the mistake this whole pattern
        // exists to avoid.
        final String keyword = views.searchInput.getText() == null
                ? "" : views.searchInput.getText().toString();

        AppExecutors.diskThenMain(
                () -> {
                    SurApp.repo().setFavourite(song.getId(), newValue);
                    return SurApp.repo().searchSongs(keyword);
                },
                songs -> {
                    if (views == null) return;
                    adapter.submit(songs);
                });
    }

    /** Small dialog listing the playlists, so the song can be added to one. */
    private void choosePlaylistFor(Song song) {
        AppExecutors.diskThenMain(
                () -> SurApp.repo().getPlaylists(),
                playlists -> {
                    if (views == null) return;
                    if (playlists.isEmpty()) {
                        Toast.makeText(requireContext(),
                                R.string.no_playlists, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] names = new String[playlists.size()];
                    for (int i = 0; i < playlists.size(); i++) {
                        names[i] = playlists.get(i).getName();
                    }
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.add_to_playlist)
                            .setItems(names, (dialog, which) -> {
                                Playlist chosen = playlists.get(which);
                                AppExecutors.disk(() ->
                                        SurApp.repo().addSongToPlaylist(chosen.getId(), song.getId()));
                                Toast.makeText(requireContext(),
                                        chosen.getName(), Toast.LENGTH_SHORT).show();
                            })
                            .show();
                });
    }
}
