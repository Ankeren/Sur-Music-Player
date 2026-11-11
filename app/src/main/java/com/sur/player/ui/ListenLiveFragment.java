package com.sur.player.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.sur.player.MainActivity;
import com.sur.player.R;
import com.sur.player.SurApp;
import com.sur.player.databinding.FragmentListenLiveBinding;
import com.sur.player.model.Episode;
import com.sur.player.model.Playable;
import com.sur.player.model.RadioStation;
import com.sur.player.net.ApiClient;
import com.sur.player.net.RssParser;
import com.sur.player.net.StationDto;
import com.sur.player.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

/**
 * Tab 3: everything that comes off the internet.
 *
 * Two segments, deliberately, because they show the two parsing techniques
 * week 11 asks for:
 *
 *   Stations -> Retrofit + Gson    -> JSON
 *   Shows    -> HttpURLConnection  -> XML (RSS), parsed by XmlPullParser
 *
 * Both run on the network thread pool and post their result back to the UI
 * thread, so the screen never freezes while waiting.
 */
public class ListenLiveFragment extends Fragment
        implements StationAdapter.Callbacks, EpisodeAdapter.Callbacks {

    private static final int SEGMENT_STATIONS = 0;
    private static final int SEGMENT_SHOWS = 1;

    private static final String PREFS = "sur_prefs";
    private static final String KEY_LAST_FEED = "last_feed_url";

    private FragmentListenLiveBinding views;
    private StationAdapter stationAdapter;
    private EpisodeAdapter episodeAdapter;

    private int segment = SEGMENT_STATIONS;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        views = FragmentListenLiveBinding.inflate(inflater, container, false);
        return views.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        stationAdapter = new StationAdapter(this);
        episodeAdapter = new EpisodeAdapter(this);

        views.liveList.setLayoutManager(new LinearLayoutManager(requireContext()));
        views.liveList.setAdapter(stationAdapter);

        views.segments.addTab(views.segments.newTab().setText(R.string.segment_stations));
        views.segments.addTab(views.segments.newTab().setText(R.string.segment_shows));
        views.segments.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                segment = tab.getPosition();
                switchSegment();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // Enter on the keyboard runs the search.
        views.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });

        switchSegment();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        views = null;
    }

    private void switchSegment() {
        if (views == null) return;

        if (segment == SEGMENT_STATIONS) {
            views.liveList.setAdapter(stationAdapter);
            views.searchBox.setHint(getString(R.string.search_stations));
            views.searchInput.setText("");
            loadStations("");
        } else {
            views.liveList.setAdapter(episodeAdapter);
            views.searchBox.setHint("Paste a podcast RSS feed URL");
            views.searchInput.setText(lastFeedUrl());
            String saved = lastFeedUrl();
            if (!saved.isEmpty()) {
                loadShow(saved);
            } else {
                showStatus("Paste any podcast RSS feed URL above and press search.");
            }
        }
    }

    private void runSearch() {
        String text = views.searchInput.getText() == null
                ? "" : views.searchInput.getText().toString().trim();
        if (segment == SEGMENT_STATIONS) {
            loadStations(text);
        } else if (!text.isEmpty()) {
            saveFeedUrl(text);
            loadShow(text);
        }
    }

    // =====================================================================
    // STATIONS - JSON over HTTP with Retrofit
    // =====================================================================

    private void loadStations(final String keyword) {
        busy(true);

        AppExecutors.networkThenMain(
                () -> {
                    try {
                        // execute() is the blocking form of a Retrofit call.
                        // That is fine here because we are already on the
                        // network pool, and it keeps the threading visible
                        // instead of hidden inside enqueue().
                        Response<List<StationDto>> response = keyword.isEmpty()
                                ? ApiClient.radio().stationsByCountry("Bangladesh", 40).execute()
                                : ApiClient.radio().searchStations(keyword, 40).execute();

                        if (!response.isSuccessful() || response.body() == null) {
                            return SurApp.repo().getCachedStations();   // fall back
                        }

                        List<RadioStation> stations = StationDto.toEntities(response.body());
                        if (!stations.isEmpty()) {
                            SurApp.repo().cacheStations(stations);
                        }
                        return stations;

                    } catch (Exception e) {
                        // No internet: show whatever we cached last time.
                        return SurApp.repo().getCachedStations();
                    }
                },
                stations -> {
                    if (views == null) return;
                    busy(false);
                    stationAdapter.submit(stations);
                    if (stations.isEmpty()) {
                        showStatus(getString(R.string.no_stations));
                    } else {
                        views.statusText.setVisibility(View.GONE);
                    }
                });
    }

    @Override
    public void onStationClick(int position) {
        List<Playable> queue = new ArrayList<>(stationAdapter.getItems());
        ((MainActivity) requireActivity()).play(queue, position);
    }

    @Override
    public void onStationMenu(RadioStation station, View anchor) {
        // WEEK 8 TOPIC: an IMPLICIT intent. We do not name a class; we
        // describe what we want done and let Android offer the apps that
        // can do it (WhatsApp, Messenger, email, and so on).
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, station.getTitle());
        share.putExtra(Intent.EXTRA_TEXT,
                "Listening to " + station.getTitle() + " on Sur\n" + station.getStreamUrl());
        startActivity(Intent.createChooser(share, station.getTitle()));
    }

    // =====================================================================
    // SHOWS - XML over HTTP, parsed by hand
    // =====================================================================

    private void loadShow(final String feedUrl) {
        busy(true);

        AppExecutors.networkThenMain(
                () -> {
                    List<Episode> episodes = RssParser.fetch(feedUrl);
                    if (!episodes.isEmpty()) {
                        SurApp.repo().cacheEpisodes(feedUrl, episodes);
                        return SurApp.repo().getCachedEpisodes(feedUrl);
                    }
                    return SurApp.repo().getCachedEpisodes(feedUrl);
                },
                episodes -> {
                    if (views == null) return;
                    busy(false);
                    episodeAdapter.submit(episodes);
                    if (episodes.isEmpty()) {
                        showStatus(getString(R.string.network_error));
                    } else {
                        views.statusText.setVisibility(View.GONE);
                    }
                });
    }

    @Override
    public void onEpisodeClick(int position) {
        List<Playable> queue = new ArrayList<>(episodeAdapter.getItems());
        ((MainActivity) requireActivity()).play(queue, position);
    }

    // =====================================================================
    // small helpers
    // =====================================================================

    private void busy(boolean loading) {
        if (views == null) return;
        views.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) views.statusText.setVisibility(View.GONE);
    }

    private void showStatus(String message) {
        if (views == null) return;
        views.statusText.setText(message);
        views.statusText.setVisibility(View.VISIBLE);
    }

    private String lastFeedUrl() {
        return prefs().getString(KEY_LAST_FEED, "");
    }

    private void saveFeedUrl(String url) {
        prefs().edit().putString(KEY_LAST_FEED, url).apply();
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
