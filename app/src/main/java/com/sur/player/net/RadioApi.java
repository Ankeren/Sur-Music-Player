package com.sur.player.net;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;

/**
 * WEEK 11 TOPIC: HTTP / API requests.
 *
 * With Retrofit you describe the request as a Java interface and it writes
 * the networking code for you. The annotation below turns
 *
 *     searchStations("dhaka", 40)
 *
 * into
 *
 *     GET /json/stations/search?name=dhaka&limit=40&hidebroken=true
 *         &order=clickcount&reverse=true
 *
 * The Radio Browser API is free and needs no key, which is exactly what you
 * want on demo day.
 */
public interface RadioApi {

    /** The API asks every app to identify itself. */
    @Headers("User-Agent: SurPlayer/1.0")
    @GET("json/stations/search?hidebroken=true&order=clickcount&reverse=true")
    Call<List<StationDto>> searchStations(@Query("name") String name,
                                          @Query("limit") int limit);

    /** Used for the first screenful before the user types anything. */
    @Headers("User-Agent: SurPlayer/1.0")
    @GET("json/stations/search?hidebroken=true&order=clickcount&reverse=true")
    Call<List<StationDto>> stationsByCountry(@Query("country") String country,
                                             @Query("limit") int limit);
}
