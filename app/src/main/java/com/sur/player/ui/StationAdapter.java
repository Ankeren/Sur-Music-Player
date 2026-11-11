package com.sur.player.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sur.player.R;
import com.sur.player.databinding.ItemStationBinding;
import com.sur.player.model.RadioStation;

import java.util.ArrayList;
import java.util.List;

/** Radio stations on the Listen live tab. */
public class StationAdapter extends RecyclerView.Adapter<StationAdapter.StationViewHolder> {

    public interface Callbacks {
        void onStationClick(int position);

        void onStationMenu(RadioStation station, View anchor);
    }

    private final List<RadioStation> stations = new ArrayList<>();
    private final Callbacks callbacks;

    public StationAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void submit(List<RadioStation> newStations) {
        stations.clear();
        if (newStations != null) stations.addAll(newStations);
        notifyDataSetChanged();
    }

    public List<RadioStation> getItems() {
        return stations;
    }

    @NonNull
    @Override
    public StationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStationBinding binding = ItemStationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new StationViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull StationViewHolder holder, int position) {
        holder.bind(stations.get(position));
    }

    @Override
    public int getItemCount() {
        return stations.size();
    }

    class StationViewHolder extends RecyclerView.ViewHolder {

        private final ItemStationBinding views;

        StationViewHolder(ItemStationBinding binding) {
            super(binding.getRoot());
            this.views = binding;

            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && callbacks != null) {
                    callbacks.onStationClick(position);
                }
            });

            binding.share.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && callbacks != null) {
                    callbacks.onStationMenu(stations.get(position), v);
                }
            });
        }

        void bind(RadioStation station) {
            views.name.setText(station.getTitle());
            views.subtitle.setText(station.getSubtitle());

            Glide.with(views.logo.getContext())
                    .load(station.getFaviconUrl())
                    .placeholder(R.drawable.ic_radio)
                    .error(R.drawable.ic_radio)
                    .into(views.logo);
        }
    }
}
