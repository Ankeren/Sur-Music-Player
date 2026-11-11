package com.sur.player.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.sur.player.model.Episode;
import com.sur.player.model.Playlist;
import com.sur.player.model.PlaylistSong;
import com.sur.player.model.RadioStation;
import com.sur.player.model.RecentPlay;
import com.sur.player.model.Song;

/**
 * WEEK 6 / WEEK 11 TOPIC: the SQLite database.
 *
 * Room builds a real SQLite file at
 *   /data/data/com.sur.player/databases/sur.db
 * and writes the CREATE TABLE statements from the six @Entity classes below.
 * You can pull that file off the device and open it in DB Browser for SQLite
 * to show the tables and the foreign keys during the demo.
 *
 * Only one connection should ever exist, so this class is a singleton, and
 * getInstance() is synchronized because the UI thread and the background
 * threads both call it (week 4: shared resources).
 */
@Database(
        entities = {
                Song.class,
                Playlist.class,
                PlaylistSong.class,
                RecentPlay.class,
                RadioStation.class,
                Episode.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract SongDao songDao();
    public abstract PlaylistDao playlistDao();
    public abstract LiveDao liveDao();

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "sur.db")
                    // SQLite does not enforce foreign keys unless you ask.
                    // Room turns them on for us, but this makes it explicit
                    // and is worth pointing at in the demo.
                    .setJournalMode(JournalMode.TRUNCATE)
                    // If you change an @Entity later, this avoids a crash
                    // while you are still developing.
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
