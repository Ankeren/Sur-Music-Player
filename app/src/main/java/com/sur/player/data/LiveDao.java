package com.sur.player.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.sur.player.model.Episode;
import com.sur.player.model.RadioStation;

import java.util.List;

/**
 * Cache for the two things that come off the internet: radio stations
 * (from JSON) and show episodes (from XML).
 *
 * Caching them means the Listen live tab still shows the last results when
 * the phone is offline, instead of an empty screen.
 */
@Dao
public interface LiveDao {

    // ---------- radio stations -------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertStations(List<RadioStation> stations);

    @Query("SELECT * FROM radio_stations ORDER BY title COLLATE NOCASE")
    List<RadioStation> getStations();

    @Query("SELECT * FROM radio_stations WHERE is_favourite = 1")
    List<RadioStation> getFavouriteStations();

    @Query("UPDATE radio_stations SET is_favourite = :favourite WHERE id = :id")
    void setStationFavourite(String id, boolean favourite);

    /** Called before caching a fresh search, so old results do not pile up. */
    @Query("DELETE FROM radio_stations WHERE is_favourite = 0")
    void clearUnfavouritedStations();

    // ---------- show episodes --------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertEpisodes(List<Episode> episodes);

    @Query("SELECT * FROM episodes WHERE feed_url = :feedUrl ORDER BY id")
    List<Episode> getEpisodes(String feedUrl);

    @Query("DELETE FROM episodes WHERE feed_url = :feedUrl")
    void clearEpisodes(String feedUrl);

    @Query("UPDATE episodes SET is_played = 1 WHERE id = :id")
    void markPlayed(long id);
}
