package com.sur.player.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sur.player.R;
import com.sur.player.databinding.ItemPlaylistBinding;
import com.sur.player.model.Playlist;

import java.util.ArrayList;
import java.util.List;

/** The list on the Playlists tab. Same pattern as SongAdapter. */
public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {

    public interface Callbacks {
        void onPlaylistClick(Playlist playlist);

        void onPlaylistMenu(Playlist playlist, View anchor);
    }

    private final List<Playlist> playlists = new ArrayList<>();
    private final Callbacks callbacks;

    public PlaylistAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void submit(List<Playlist> newPlaylists) {
        playlists.clear();
        if (newPlaylists != null) playlists.addAll(newPlaylists);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlaylistBinding binding = ItemPlaylistBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PlaylistViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        holder.bind(playlists.get(position));
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    class PlaylistViewHolder extends RecyclerView.ViewHolder {

        private final ItemPlaylistBinding views;

        PlaylistViewHolder(ItemPlaylistBinding binding) {
            super(binding.getRoot());
            this.views = binding;

            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && callbacks != null) {
                    callbacks.onPlaylistClick(playlists.get(position));
                }
            });

            binding.overflow.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && callbacks != null) {
                    callbacks.onPlaylistMenu(playlists.get(position), v);
                }
            });
        }

        void bind(Playlist playlist) {
            views.name.setText(playlist.getName());
            views.count.setText(views.getRoot().getContext()
                    .getString(R.string.songs_count, playlist.getSongCount()));
        }
    }
}
