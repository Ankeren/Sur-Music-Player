package com.sur.player.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A playlist the user made. The songs inside it are NOT stored here - see
 * PlaylistSong, the junction table.
 */
@Entity(tableName = "playlists")
public class Playlist {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String name;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    /** Filled in by a query, not stored. @Ignore keeps it out of the table. */
    @androidx.room.Ignore
    private int songCount;

    public Playlist() {
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public int getSongCount() { return songCount; }
    public void setSongCount(int songCount) { this.songCount = songCount; }

    @Override
    public String toString() {
        return name;
    }
}
