package com.sur.player.data;

import android.content.Context;

import androidx.sqlite.db.SimpleSQLiteQuery;

import com.sur.player.model.Episode;
import com.sur.player.model.Playlist;
import com.sur.player.model.PlaylistSong;
import com.sur.player.model.RadioStation;
import com.sur.player.model.RecentPlay;
import com.sur.player.model.Song;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The single place that knows where data comes from.
 *
 * An Activity or Fragment never opens the database and never calls the
 * network itself; it asks the Repository, and the Repository decides whether
 * the answer comes from Room, from MediaStore or from the internet. That is
 * the "data flow" you will be asked to explain in week 13.
 *
 * IMPORTANT: every method here is SLOW and must be called from a background
 * thread. The UI always wraps these calls in AppExecutors.diskThenMain(...).
 */
public class Repository {

    private final SongDao songDao;
    private final PlaylistDao playlistDao;
    private final LiveDao liveDao;
    private final MediaStoreScanner scanner;

    public Repository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.songDao = db.songDao();
        this.playlistDao = db.playlistDao();
        this.liveDao = db.liveDao();
        this.scanner = new MediaStoreScanner(context);
    }

    // =====================================================================
    // SONGS
    // =====================================================================

    /**
     * Reads every audio file from MediaStore and writes it into our own
     * table. Called on first launch and from Settings > Rescan.
     *
     * @return how many songs are in the library afterwards
     */
    /**
     * Brings the stored library back in line with what is actually on the
     * phone: new files are added, and files the user has deleted are removed.
     *
     * The removal half used to be missing, so a song deleted from the phone
     * stayed in Sur's own database forever and kept showing in the list.
     *
     * Note the guard on an empty scan. If the scan comes back with nothing,
     * which happens when the media permission has just been revoked, that is
     * not proof the phone is empty, and wiping the whole library on the
     * strength of it would be destructive. In that case we change nothing.
     */
    public int rescanLibrary() {
        List<Song> found = scanner.scan();
        if (found.isEmpty()) return songDao.count();

        songDao.insertAll(found);   // REPLACE, so re-scanning is safe

        Set<Long> stillOnPhone = new HashSet<>();
        for (Song song : found) {
            stillOnPhone.add(song.getId());
        }

        List<Long> gone = new ArrayList<>();
        for (Long storedId : songDao.getAllIds()) {
            if (!stillOnPhone.contains(storedId)) gone.add(storedId);
        }

        // SQLite caps how many values one query may carry, so a very large
        // library is deleted in batches rather than in a single statement.
        for (int from = 0; from < gone.size(); from += DELETE_BATCH) {
            int to = Math.min(from + DELETE_BATCH, gone.size());
            songDao.deleteByIds(gone.subList(from, to));
        }

        return songDao.count();
    }

    private static final int DELETE_BATCH = 900;

    public List<Song> getAllSongs() {
        return songDao.getAll();
    }

    public List<Song> searchSongs(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return songDao.getAll();
        }
        return songDao.search(keyword.trim());
    }

    public Song getSong(long id) {
        return songDao.getById(id);
    }

    public List<Song> getFavourites() {
        return songDao.getFavourites();
    }

    public void setFavourite(long songId, boolean favourite) {
        songDao.setFavourite(songId, favourite);
    }

    /** Called by the service each time a song starts. */
    public void notePlayed(long songId) {
        songDao.incrementPlayCount(songId);
        songDao.addRecentPlay(new RecentPlay(songId, System.currentTimeMillis()));
    }

    public List<Song> getRecentlyPlayed() {
        return songDao.getRecentlyPlayed();
    }

    /**
     * WEEK 6 TOPIC: raw SQL built at run time.
     *
     * The time window is chosen by the caller, so the statement is assembled
     * here instead of being fixed at compile time. The value still travels
     * as a bound argument, never glued into the string, so this is not open
     * to SQL injection.
     */
    public List<Song> getMostPlayedSince(long sinceMillis, int limit) {
        String sql =
                "SELECT s.* FROM songs s " +
                "INNER JOIN recent_plays r ON r.song_id = s.id " +
                "WHERE r.played_at >= ? " +
                "GROUP BY s.id " +
                "ORDER BY COUNT(r.id) DESC " +
                "LIMIT ?";
        SimpleSQLiteQuery query = new SimpleSQLiteQuery(sql, new Object[]{sinceMillis, limit});
        return songDao.runRawQuery(query);
    }

    // =====================================================================
    // PLAYLISTS  (create - read - update - delete)
    // =====================================================================

    public List<Playlist> getPlaylists() {
        List<PlaylistWithCount> rows = playlistDao.getAllWithCounts();
        List<Playlist> result = new ArrayList<>();
        for (PlaylistWithCount row : rows) {
            row.playlist.setSongCount(row.songCount);
            result.add(row.playlist);
        }
        return result;
    }

    public long createPlaylist(String name) {
        Playlist playlist = new Playlist();
        playlist.setName(name);
        playlist.setCreatedAt(System.currentTimeMillis());
        return playlistDao.insert(playlist);
    }

    public Playlist getPlaylist(long id) {
        return playlistDao.getById(id);
    }

    public void renamePlaylist(long id, String newName) {
        playlistDao.rename(id, newName);
    }

    public void deletePlaylist(long id) {
        // The rows in playlist_songs disappear on their own: the foreign key
        // was declared onDelete = CASCADE.
        playlistDao.deletePlaylist(id);
    }

    public List<Song> getSongsInPlaylist(long playlistId) {
        return playlistDao.getSongsIn(playlistId);
    }

    public void addSongToPlaylist(long playlistId, long songId) {
        int position = playlistDao.nextPosition(playlistId);
        playlistDao.addSong(new PlaylistSong(playlistId, songId, position));
    }

    public void removeSongFromPlaylist(long playlistId, long songId) {
        playlistDao.removeSong(playlistId, songId);
    }

    /** Rewrites every position after the user drags a row. */
    public void savePlaylistOrder(long playlistId, List<Song> inNewOrder) {
        for (int i = 0; i < inNewOrder.size(); i++) {
            playlistDao.setPosition(playlistId, inNewOrder.get(i).getId(), i);
        }
    }

    // =====================================================================
    // RADIO AND SHOWS  (cached copies of what came off the internet)
    // =====================================================================

    public void cacheStations(List<RadioStation> stations) {
        liveDao.clearUnfavouritedStations();
        liveDao.insertStations(stations);
    }

    public List<RadioStation> getCachedStations() {
        return liveDao.getStations();
    }

    public void setStationFavourite(String stationId, boolean favourite) {
        liveDao.setStationFavourite(stationId, favourite);
    }

    public void cacheEpisodes(String feedUrl, List<Episode> episodes) {
        liveDao.clearEpisodes(feedUrl);
        liveDao.insertEpisodes(episodes);
    }

    public List<Episode> getCachedEpisodes(String feedUrl) {
        return liveDao.getEpisodes(feedUrl);
    }

    public void markEpisodePlayed(long episodeId) {
        liveDao.markPlayed(episodeId);
    }
}
