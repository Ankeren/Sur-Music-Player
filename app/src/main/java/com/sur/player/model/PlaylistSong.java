package com.sur.player.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/**
 * WEEK 6 / WEEK 11 TOPIC: table relationships.
 *
 * A playlist holds many songs, and one song can sit in many playlists.
 * That is a MANY-TO-MANY relationship, and SQL cannot store it in either
 * table - it needs a third table in between. This is that table.
 *
 * Each row means "song X is at position N inside playlist Y".
 *
 * The two foreign keys use CASCADE, so deleting a playlist deletes its rows
 * here automatically, and the songs themselves are untouched.
 */
@Entity(
        tableName = "playlist_songs",
        primaryKeys = {"playlist_id", "song_id"},
        foreignKeys = {
                @ForeignKey(
                        entity = Playlist.class,
                        parentColumns = "id",
                        childColumns = "playlist_id",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(
                        entity = Song.class,
                        parentColumns = "id",
                        childColumns = "song_id",
                        onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("song_id"), @Index("playlist_id")}
)
public class PlaylistSong {

    @androidx.room.ColumnInfo(name = "playlist_id")
    private long playlistId;

    @androidx.room.ColumnInfo(name = "song_id")
    private long songId;

    private int position;

    public PlaylistSong() {
    }

    @androidx.room.Ignore
    public PlaylistSong(long playlistId, long songId, int position) {
        this.playlistId = playlistId;
        this.songId = songId;
        this.position = position;
    }

    public long getPlaylistId() { return playlistId; }
    public void setPlaylistId(long playlistId) { this.playlistId = playlistId; }

    public long getSongId() { return songId; }
    public void setSongId(long songId) { this.songId = songId; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
