package com.sur.player.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * One audio file on the phone.
 *
 * WEEK 6 / WEEK 11 TOPIC: this class IS the "songs" table. Room reads the
 * annotations below and writes the CREATE TABLE statement for us.
 *
 * The id is the same id MediaStore gave the file, so we never end up with
 * two rows for one file.
 *
 * Every field is private with a getter and a setter (encapsulation).
 */
@Entity(tableName = "songs")
public class Song extends MediaItem implements Playable {

    @PrimaryKey
    private long id;

    private String artist;
    private String album;

    @ColumnInfo(name = "duration_ms")
    private long durationMs;

    /** content:// URI of the audio file. */
    private String uri;

    @ColumnInfo(name = "art_uri")
    private String artUri;

    /** Shown with a heart, and published by FavouritesProvider. */
    @ColumnInfo(name = "is_favourite")
    private boolean favourite;

    @ColumnInfo(name = "play_count")
    private int playCount;

    public Song() {
        // Room needs a public constructor with no arguments.
    }

    // ---- fields ----------------------------------------------------------

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getArtUri() { return artUri; }
    public void setArtUri(String artUri) { this.artUri = artUri; }

    public boolean isFavourite() { return favourite; }
    public void setFavourite(boolean favourite) { this.favourite = favourite; }

    public int getPlayCount() { return playCount; }
    public void setPlayCount(int playCount) { this.playCount = playCount; }

    // ---- what the parent class demanded ----------------------------------

    @NonNull
    @Override
    public String getKey() {
        return String.valueOf(id);
    }

    @Override
    public String getSubtitle() {
        return artist == null || artist.isEmpty() ? "Unknown artist" : artist;
    }

    // ---- what the Playable interface demanded ----------------------------

    @Override
    public String getPlayUri() {
        return uri;
    }
}
