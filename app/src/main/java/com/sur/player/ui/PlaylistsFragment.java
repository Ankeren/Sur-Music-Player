package com.sur.player.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sur.player.PlaylistDetailActivity;
import com.sur.player.R;
import com.sur.player.SurApp;
import com.sur.player.databinding.DialogTextInputBinding;
import com.sur.player.databinding.FragmentPlaylistsBinding;
import com.sur.player.model.Playlist;
import com.sur.player.util.AppExecutors;

/**
 * Tab 2: the playlists, and the full set of CRUD operations on them.
 *
 *   CREATE  the + button
 *   READ    the list itself, and opening one
 *   UPDATE  rename, from the row menu
 *   DELETE  delete, from the row menu, with a confirmation
 */
public class PlaylistsFragment extends Fragment implements PlaylistAdapter.Callbacks {

    private FragmentPlaylistsBinding views;
    private PlaylistAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        views = FragmentPlaylistsBinding.inflate(inflater, container, false);
        return views.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new PlaylistAdapter(this);
        views.playlistList.setLayoutManager(new LinearLayoutManager(requireContext()));
        views.playlistList.setAdapter(adapter);

        views.addButton.setOnClickListener(v -> askForName(null));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload every time the tab comes back, so a song added inside
        // PlaylistDetailActivity shows up in the count straight away.
        reload();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        views = null;
    }

    private void reload() {
        AppExecutors.diskThenMain(
                () -> SurApp.repo().getPlaylists(),
                playlists -> {
                    if (views == null) return;
                    adapter.submit(playlists);
                    views.emptyText.setVisibility(playlists.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    // =====================================================================
    // CREATE and UPDATE share one dialog
    // =====================================================================

    /** @param existing null to create a new playlist, or the one to rename */
    private void askForName(@Nullable Playlist existing) {
        DialogTextInputBinding dialogViews =
                DialogTextInputBinding.inflate(getLayoutInflater());

        if (existing != null) {
            dialogViews.input.setText(existing.getName());
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? R.string.new_playlist : R.string.rename)
                .setView(dialogViews.getRoot())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = dialogViews.input.getText() == null
                            ? "" : dialogViews.input.getText().toString().trim();
                    if (name.isEmpty()) return;

                    AppExecutors.diskThenMain(
                            () -> {
                                if (existing == null) {
                                    SurApp.repo().createPlaylist(name);
                                } else {
                                    SurApp.repo().renamePlaylist(existing.getId(), name);
                                }
                                return SurApp.repo().getPlaylists();
                            },
                            playlists -> {
                                if (views == null) return;
                                adapter.submit(playlists);
                                views.emptyText.setVisibility(
                                        playlists.isEmpty() ? View.VISIBLE : View.GONE);
                            });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // =====================================================================
    // TAPS
    // =====================================================================

    @Override
    public void onPlaylistClick(Playlist playlist) {
        // EXPLICIT intent carrying the id of the row that was tapped.
        Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlist.getId());
        startActivity(intent);
    }

    @Override
    public void onPlaylistMenu(Playlist playlist, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.rename);
        menu.getMenu().add(0, 2, 1, R.string.delete);

        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                askForName(playlist);
                return true;
            }
            if (item.getItemId() == 2) {
                confirmDelete(playlist);
                return true;
            }
            return false;
        });
        menu.show();
    }

    /** Deleting is destructive, so it always asks first (week 9: UX). */
    private void confirmDelete(Playlist playlist) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(playlist.getName())
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        AppExecutors.diskThenMain(
                                () -> {
                                    SurApp.repo().deletePlaylist(playlist.getId());
                                    return SurApp.repo().getPlaylists();
                                },
                                playlists -> {
                                    if (views == null) return;
                                    adapter.submit(playlists);
                                    views.emptyText.setVisibility(
                                            playlists.isEmpty() ? View.VISIBLE : View.GONE);
                                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
