package com.sur.player.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A live internet radio station.
 *
 * Stations arrive from the Radio Browser API as JSON, get converted into
 * these objects (see net/StationDto.java) and are then cached in Room, so
 * the tab still shows something when there is no internet.
 */
@Entity(tableName = "radio_stations")
public class RadioStation extends MediaItem implements Playable {

    /** The API gives every station a UUID, so we use it as the primary key. */
    @PrimaryKey
    @NonNull
    private String id = "";

    private String country;
    private String tags;

    @ColumnInfo(name = "stream_url")
    private String streamUrl;

    @ColumnInfo(name = "favicon_url")
    private String faviconUrl;

    @ColumnInfo(name = "is_favourite")
    private boolean favourite;

    public RadioStation() {
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }

    public String getFaviconUrl() { return faviconUrl; }
    public void setFaviconUrl(String faviconUrl) { this.faviconUrl = faviconUrl; }

    public boolean isFavourite() { return favourite; }
    public void setFavourite(boolean favourite) { this.favourite = favourite; }

    @NonNull
    @Override
    public String getKey() {
        return id;
    }

    @Override
    public String getSubtitle() {
        StringBuilder sb = new StringBuilder();
        if (country != null && !country.isEmpty()) sb.append(country);
        if (tags != null && !tags.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(tags);
        }
        return sb.length() == 0 ? "Live radio" : sb.toString();
    }

    @Override
    public String getPlayUri() {
        return streamUrl;
    }
}
