package com.sur.player.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sur.player.databinding.ItemEpisodeBinding;
import com.sur.player.model.Episode;

import java.util.ArrayList;
import java.util.List;

/** Episodes parsed out of an RSS feed. */
public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder> {

    public interface Callbacks {
        void onEpisodeClick(int position);
    }

    private final List<Episode> episodes = new ArrayList<>();
    private final Callbacks callbacks;

    public EpisodeAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void submit(List<Episode> newEpisodes) {
        episodes.clear();
        if (newEpisodes != null) episodes.addAll(newEpisodes);
        notifyDataSetChanged();
    }

    public List<Episode> getItems() {
        return episodes;
    }

    @NonNull
    @Override
    public EpisodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEpisodeBinding binding = ItemEpisodeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new EpisodeViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        holder.bind(episodes.get(position));
    }

    @Override
    public int getItemCount() {
        return episodes.size();
    }

    class EpisodeViewHolder extends RecyclerView.ViewHolder {

        private final ItemEpisodeBinding views;

        EpisodeViewHolder(ItemEpisodeBinding binding) {
            super(binding.getRoot());
            this.views = binding;

            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && callbacks != null) {
                    callbacks.onEpisodeClick(position);
                }
            });
        }

        void bind(Episode episode) {
            views.title.setText(episode.getTitle());
            views.published.setText(episode.getSubtitle());
        }
    }
}
