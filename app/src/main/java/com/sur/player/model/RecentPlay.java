package com.sur.player.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One line in the listening history: "this song was played at this time".
 *
 * A one-to-many relationship: one song, many rows here. It feeds the
 * "most played this week" query in SongDao, which is written as real SQL.
 */
@Entity(
        tableName = "recent_plays",
        foreignKeys = @ForeignKey(
                entity = Song.class,
                parentColumns = "id",
                childColumns = "song_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("song_id")}
)
public class RecentPlay {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "song_id")
    private long songId;

    @ColumnInfo(name = "played_at")
    private long playedAt;

    public RecentPlay() {
    }

    @androidx.room.Ignore
    public RecentPlay(long songId, long playedAt) {
        this.songId = songId;
        this.playedAt = playedAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getSongId() { return songId; }
    public void setSongId(long songId) { this.songId = songId; }

    public long getPlayedAt() { return playedAt; }
    public void setPlayedAt(long playedAt) { this.playedAt = playedAt; }
}
