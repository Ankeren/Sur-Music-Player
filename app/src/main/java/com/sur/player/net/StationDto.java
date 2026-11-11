package com.sur.player.net;

import com.google.gson.annotations.SerializedName;
import com.sur.player.model.RadioStation;

import java.util.ArrayList;
import java.util.List;

/**
 * WEEK 7 / WEEK 11 TOPIC: turning JSON into Java objects.
 *
 * This class is shaped like the JSON the Radio Browser API sends back, not
 * like our database table. @SerializedName maps the API's names
 * ("url_resolved", "stationuuid") onto sensible Java field names, and Gson
 * fills them in automatically.
 *
 * Keeping the API shape (this class) separate from the storage shape
 * (RadioStation) means a change at either end does not break the other.
 * That separation is a design decision worth mentioning in the viva.
 *
 * A raw response looks like:
 * [
 *   {
 *     "stationuuid": "9617a958-...",
 *     "name": "Radio Foorti",
 *     "url_resolved": "https://.../stream",
 *     "country": "Bangladesh",
 *     "tags": "pop,bangla",
 *     "favicon": "https://.../logo.png",
 *     "lastcheckok": 1
 *   }
 * ]
 */
public class StationDto {

    @SerializedName("stationuuid")
    public String uuid;

    @SerializedName("name")
    public String name;

    /** The stream that actually plays; "url" may be a redirect page. */
    @SerializedName("url_resolved")
    public String urlResolved;

    @SerializedName("country")
    public String country;

    @SerializedName("tags")
    public String tags;

    @SerializedName("favicon")
    public String favicon;

    /** 1 when the API last managed to connect to the stream. */
    @SerializedName("lastcheckok")
    public int lastCheckOk;

    /** One DTO -> one database row. */
    public RadioStation toEntity() {
        RadioStation station = new RadioStation();
        station.setId(uuid == null ? String.valueOf(hashCode()) : uuid);
        station.setTitle(name == null ? "Unknown station" : name.trim());
        station.setCountry(country);
        station.setTags(tags);
        station.setStreamUrl(urlResolved);
        station.setFaviconUrl(favicon);
        return station;
    }

    /**
     * Converts a whole response, dropping the rubbish on the way:
     * stations with no playable URL, and ones the API knows are dead.
     */
    public static List<RadioStation> toEntities(List<StationDto> dtos) {
        List<RadioStation> stations = new ArrayList<>();
        if (dtos == null) return stations;
        for (StationDto dto : dtos) {
            if (dto == null) continue;
            if (dto.lastCheckOk != 1) continue;
            if (dto.urlResolved == null || dto.urlResolved.isEmpty()) continue;
            stations.add(dto.toEntity());
        }
        return stations;
    }
}
