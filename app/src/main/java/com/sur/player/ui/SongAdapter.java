package com.sur.player.ui;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sur.player.R;
import com.sur.player.databinding.ItemSongBinding;
import com.sur.player.model.Song;
import com.sur.player.util.LetterTile;
import com.sur.player.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * WEEK 9 TOPIC: RecyclerView, adapter, ViewHolder and a custom item layout.
 *
 * A phone can only show about eight rows at a time. RecyclerView creates
 * roughly that many row views and then RECYCLES them as you scroll: the row
 * that leaves the top is refilled with new data and reappears at the bottom.
 * That is why a list of 5,000 songs scrolls smoothly.
 *
 * The three methods Android calls:
 *   onCreateViewHolder - inflate item_song.xml (happens ~10 times, total)
 *   onBindViewHolder   - put one song's data into a recycled row
 *   getItemCount       - how many rows there are altogether
 */
public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    /** The fragment implements this to react to taps. */
    public interface Callbacks {
        void onSongClick(int position);

        void onSongMenu(Song song, View anchor);
    }

    private final List<Song> songs = new ArrayList<>();
    private final Callbacks callbacks;

    /** Id of the song playing right now, or NONE when nothing is playing. */
    private static final long NONE = -1L;
    private long playingId = NONE;

    public SongAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    /**
     * Marks one song as playing, so its row shows the bouncing bars.
     *
     * Only the two rows that actually change are refreshed, the one losing
     * the bars and the one gaining them. Calling notifyDataSetChanged here
     * would rebind every visible row for no reason.
     */
    public void setPlayingId(long songId) {
        if (playingId == songId) return;

        int previous = positionOf(playingId);
        playingId = songId;
        int next = positionOf(songId);

        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous);
        if (next != RecyclerView.NO_POSITION) notifyItemChanged(next);
    }

    private int positionOf(long songId) {
        if (songId == NONE) return RecyclerView.NO_POSITION;
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).getId() == songId) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    public void submit(List<Song> newSongs) {
        songs.clear();
        if (newSongs != null) songs.addAll(newSongs);
        notifyDataSetChanged();
    }

    public List<Song> getItems() {
        return songs;
    }

    // ---- editing the list in place (used by drag and swipe) -------------
    // These call notifyItemMoved/Removed/Inserted instead of
    // notifyDataSetChanged, so RecyclerView can animate the change.

    /** Moves one row to a new position while the user drags it. */
    public void moveItem(int from, int to) {
        Song moved = songs.remove(from);
        songs.add(to, moved);
        notifyItemMoved(from, to);
    }

    /** Takes a row out and returns it, so the caller can offer Undo. */
    public Song removeAt(int position) {
        Song removed = songs.remove(position);
        notifyItemRemoved(position);
        return removed;
    }

    /** Puts a row back, for example when the user taps Undo. */
    public void insertAt(int position, Song song) {
        songs.add(position, song);
        notifyItemInserted(position);
    }

    public Song getItem(int position) {
        return songs.get(position);
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSongBinding binding = ItemSongBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SongViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        holder.bind(songs.get(position));
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    /**
     * A ViewHolder keeps references to the views inside one row, so
     * onBindViewHolder never has to search the layout again.
     */
    class SongViewHolder extends RecyclerView.ViewHolder {

        private final ItemSongBinding views;

        SongViewHolder(ItemSongBinding binding) {
            super(binding.getRoot());
            this.views = binding;

            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && callbacks != null) {
                    callbacks.onSongClick(position);
                }
            });

            binding.overflow.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && callbacks != null) {
                    callbacks.onSongMenu(songs.get(position), v);
                }
            });
        }

        void bind(Song song) {
            views.title.setText(song.getTitle());
            views.artist.setText(song.getSubtitle());
            views.duration.setText(TimeUtil.format(song.getDurationMs()));
            views.favourite.setVisibility(song.isFavourite() ? View.VISIBLE : View.GONE);
            views.equalizer.setVisibility(
                    song.getId() == playingId ? View.VISIBLE : View.GONE);

            // A fresh tile per row, because its colour and letter come from
            // this song's title. It shows while the real cover loads, and
            // stays for the many songs that have no cover at all.
            Drawable tile = LetterTile.forTitle(views.art.getContext(), song.getTitle());

            Glide.with(views.art.getContext())
                    .load(song.getArtUri())
                    .placeholder(tile)
                    .error(tile)
                    .into(views.art);
        }
    }
}
