package com.sur.player.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * One episode of a show, read out of an RSS feed.
 *
 * WEEK 11 TOPIC: XML parsing. RssParser turns each <item> element of the
 * feed into one of these objects.
 */
@Entity(tableName = "episodes")
public class Episode extends MediaItem implements Playable {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "feed_url")
    private String feedUrl;

    @ColumnInfo(name = "feed_name")
    private String feedName;

    @ColumnInfo(name = "audio_url")
    private String audioUrl;

    @ColumnInfo(name = "published_at")
    private String publishedAt;

    @ColumnInfo(name = "is_played")
    private boolean played;

    public Episode() {
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getFeedUrl() { return feedUrl; }
    public void setFeedUrl(String feedUrl) { this.feedUrl = feedUrl; }

    public String getFeedName() { return feedName; }
    public void setFeedName(String feedName) { this.feedName = feedName; }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }

    public boolean isPlayed() { return played; }
    public void setPlayed(boolean played) { this.played = played; }

    @NonNull
    @Override
    public String getKey() {
        return "ep-" + id;
    }

    @Override
    public String getSubtitle() {
        return publishedAt == null ? "" : publishedAt;
    }

    @Override
    public String getPlayUri() {
        return audioUrl;
    }
}
