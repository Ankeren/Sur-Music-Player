package com.sur.player.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.sur.player.databinding.ItemQueueBinding;
import com.sur.player.model.Playable;

import java.util.List;

/**
 * WEEK 9 TOPIC: a second RecyclerView adapter.
 *
 * It shows the playback queue in the "Up next" sheet. It works on Playable,
 * the interface from week 1, so the same adapter can list songs, radio
 * stations and podcast episodes without asking what each one is.
 *
 * The row that is playing right now gets the accent color and a small music
 * icon. Tapping any row asks the service to jump to it.
 */
public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.QueueViewHolder> {

    public interface OnPick {
        void onPick(int position);
    }

    private final List<Playable> items;
    private final int currentIndex;
    private final OnPick onPick;

    public QueueAdapter(List<Playable> items, int currentIndex, OnPick onPick) {
        this.items = items;
        this.currentIndex = currentIndex;
        this.onPick = onPick;
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemQueueBinding binding = ItemQueueBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new QueueViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        holder.bind(items.get(position), position == currentIndex);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class QueueViewHolder extends RecyclerView.ViewHolder {

        private final ItemQueueBinding views;

        QueueViewHolder(ItemQueueBinding binding) {
            super(binding.getRoot());
            this.views = binding;

            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && onPick != null) {
                    onPick.onPick(position);
                }
            });
        }

        void bind(Playable item, boolean isCurrent) {
            views.title.setText(item.getTitle());
            views.subtitle.setText(item.getSubtitle());
            views.nowPlaying.setVisibility(isCurrent ? View.VISIBLE : View.INVISIBLE);

            int color = MaterialColors.getColor(views.title, isCurrent
                    ? com.google.android.material.R.attr.colorPrimary
                    : com.google.android.material.R.attr.colorOnSurface);
            views.title.setTextColor(color);
        }
    }
}
