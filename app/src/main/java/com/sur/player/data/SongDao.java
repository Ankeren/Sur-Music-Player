package com.sur.player.data;

import android.database.Cursor;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Update;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.sur.player.model.RecentPlay;
import com.sur.player.model.Song;

import java.util.List;

/**
 * WEEK 6 TOPIC: insert, update, delete and query - the four CRUD operations.
 *
 * A DAO ("Data Access Object") is just an interface. Room generates the real
 * class at compile time, so every one of these queries is checked BEFORE the
 * app runs: a typo in the SQL below is a build error, not a crash.
 *
 * None of these methods may be called on the main thread. The Repository
 * always runs them on a background thread (see util/AppExecutors).
 */
@Dao
public interface SongDao {

    // ---------- READ ------------------------------------------------------

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    List<Song> getAll();

    @Query("SELECT * FROM songs WHERE id = :id")
    Song getById(long id);

    /** The search box. The ? placeholders make SQL injection impossible. */
    @Query("SELECT * FROM songs " +
           "WHERE title LIKE '%' || :keyword || '%' " +
           "   OR artist LIKE '%' || :keyword || '%' " +
           "ORDER BY title COLLATE NOCASE")
    List<Song> search(String keyword);

    @Query("SELECT * FROM songs WHERE is_favourite = 1 ORDER BY title COLLATE NOCASE")
    List<Song> getFavourites();

    /**
     * Returns a raw Cursor instead of objects. FavouritesProvider hands this
     * straight to whichever app asked for the favourites list, because a
     * ContentProvider must answer with a Cursor.
     */
    @Query("SELECT id AS _id, title, artist, album FROM songs " +
           "WHERE is_favourite = 1 ORDER BY title COLLATE NOCASE")
    Cursor favouritesCursor();

    @Query("SELECT COUNT(*) FROM songs")
    int count();

    // ---------- CREATE ----------------------------------------------------

    /** REPLACE means re-scanning the phone updates rows instead of failing. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Song> songs);

    // ---------- UPDATE ----------------------------------------------------

    @Update
    void update(Song song);

    @Query("UPDATE songs SET is_favourite = :favourite WHERE id = :id")
    void setFavourite(long id, boolean favourite);

    @Query("UPDATE songs SET play_count = play_count + 1 WHERE id = :id")
    void incrementPlayCount(long id);

    // ---------- DELETE ----------------------------------------------------

    @Delete
    void delete(Song song);

    @Query("DELETE FROM songs")
    void deleteAll();

    /** Every id currently in the table, used to spot songs that have gone. */
    @Query("SELECT id FROM songs")
    List<Long> getAllIds();

    /**
     * Removes songs whose files are no longer on the phone.
     *
     * playlist_songs has a CASCADE foreign key on song_id, so a song removed
     * here also disappears from any playlist that held it, which is right:
     * the file does not exist any more.
     */
    @Query("DELETE FROM songs WHERE id IN (:ids)")
    void deleteByIds(List<Long> ids);

    // ---------- listening history ----------------------------------------

    @Insert
    void addRecentPlay(RecentPlay play);

    @Query("SELECT * FROM songs WHERE id IN " +
           "(SELECT song_id FROM recent_plays ORDER BY played_at DESC LIMIT 20)")
    List<Song> getRecentlyPlayed();

    /**
     * WEEK 6 TOPIC: raw SQL.
     *
     * Everything above is checked by Room at compile time. Sometimes you want
     * to build the SQL yourself at run time - for example "most played since
     * a date the user picked". @RawQuery lets you do that. Repository builds
     * the statement with SimpleSQLiteQuery.
     */
    @RawQuery
    List<Song> runRawQuery(SupportSQLiteQuery query);
}
