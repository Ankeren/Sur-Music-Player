package com.sur.player.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.sur.player.model.Playlist;
import com.sur.player.model.PlaylistSong;
import com.sur.player.model.Song;

import java.util.List;

/**
 * CRUD for playlists, plus the queries that walk across the junction table.
 *
 * The JOIN in getSongsIn() is the relationship doing real work: it starts at
 * playlist_songs, hops to songs through the foreign key, and returns the
 * songs in the order the user arranged them.
 */
@Dao
public interface PlaylistDao {

    // ---------- CREATE ----------------------------------------------------

    /** Returns the id SQLite generated for the new row. */
    @Insert
    long insert(Playlist playlist);

    // ---------- READ ------------------------------------------------------

    @Query("SELECT p.*, " +
           "(SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlist_id = p.id) AS songCount " +
           "FROM playlists p ORDER BY p.created_at DESC")
    List<PlaylistWithCount> getAllWithCounts();

    @Query("SELECT * FROM playlists WHERE id = :id")
    Playlist getById(long id);

    /** The many-to-many JOIN, in the user's own order. */
    @Query("SELECT s.* FROM songs s " +
           "INNER JOIN playlist_songs ps ON s.id = ps.song_id " +
           "WHERE ps.playlist_id = :playlistId " +
           "ORDER BY ps.position")
    List<Song> getSongsIn(long playlistId);

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlist_id = :playlistId")
    int nextPosition(long playlistId);

    // ---------- UPDATE ----------------------------------------------------

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    void rename(long id, String name);

    @Query("UPDATE playlist_songs SET position = :position " +
           "WHERE playlist_id = :playlistId AND song_id = :songId")
    void setPosition(long playlistId, long songId, int position);

    // ---------- DELETE ----------------------------------------------------

    /**
     * Deletes the playlist only. The rows in playlist_songs go with it
     * because the foreign key was declared onDelete = CASCADE.
     */
    @Query("DELETE FROM playlists WHERE id = :id")
    void deletePlaylist(long id);

    // ---------- the junction table ---------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void addSong(PlaylistSong link);

    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId AND song_id = :songId")
    void removeSong(long playlistId, long songId);
}
