package com.sur.player;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sur.player.databinding.ActivityMostPlayedBinding;
import com.sur.player.databinding.ItemQueueBinding;
import com.sur.player.firebase.Accounts;
import com.sur.player.firebase.PlayCounts;
import com.sur.player.util.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * WEEK 11 TOPIC: reading from the Realtime Database.
 *
 * Shows the user's most played songs, counted in the cloud rather than on
 * this phone, so the list survives a reinstall and would follow the user to
 * a second device.
 *
 * The rows reuse item_queue.xml, the same layout the "Up next" sheet uses.
 * Reusing a layout rather than writing a near identical one is the reusable
 * component idea from week 9.
 */
public class MostPlayedActivity extends AppCompatActivity {

    private static final int HOW_MANY = 20;

    private ActivityMostPlayedBinding views;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);

        views = ActivityMostPlayedBinding.inflate(getLayoutInflater());
        setContentView(views.getRoot());

        views.toolbar.setNavigationOnClickListener(v -> finish());
        views.songList.setLayoutManager(new LinearLayoutManager(this));

        load();
    }

    private void load() {
        String userId = Accounts.userId();
        if (userId == null) {
            showMessage(getString(R.string.sign_in_required));
            return;
        }

        views.progress.setVisibility(View.VISIBLE);

        PlayCounts.top(userId, HOW_MANY, (success, entries, errorMessage) -> {
            if (isFinishing() || isDestroyed()) return;
            views.progress.setVisibility(View.GONE);

            if (!success) {
                showMessage(errorMessage == null
                        ? getString(R.string.error_generic) : errorMessage);
                return;
            }
            if (entries == null || entries.isEmpty()) {
                showMessage(getString(R.string.most_played_empty));
                return;
            }

            views.emptyText.setVisibility(View.GONE);
            views.songList.setAdapter(new TopAdapter(entries));
        });
    }

    private void showMessage(String message) {
        views.progress.setVisibility(View.GONE);
        views.emptyText.setVisibility(View.VISIBLE);
        views.emptyText.setText(message);
    }

    // =====================================================================

    private class TopAdapter extends RecyclerView.Adapter<TopAdapter.Holder> {

        private final List<PlayCounts.Entry> entries;

        TopAdapter(List<PlayCounts.Entry> entries) {
            this.entries = new ArrayList<>(entries);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemQueueBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(position + 1, entries.get(position));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        class Holder extends RecyclerView.ViewHolder {

            private final ItemQueueBinding rowViews;

            Holder(ItemQueueBinding binding) {
                super(binding.getRoot());
                rowViews = binding;
                // That layout hides its icon by default, for the Up next list.
                rowViews.nowPlaying.setVisibility(View.VISIBLE);
            }

            void bind(int rank, PlayCounts.Entry entry) {
                rowViews.title.setText(getString(R.string.ranked_title, rank, entry.title));
                rowViews.subtitle.setText(getResources().getQuantityString(
                        R.plurals.play_count, (int) entry.count, entry.artist, entry.count));
            }
        }
    }
}
