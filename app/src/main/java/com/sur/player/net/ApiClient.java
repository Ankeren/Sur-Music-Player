package com.sur.player.net;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Builds the Retrofit object once and hands the same one to everybody.
 * Creating a Retrofit instance per request would open a new connection pool
 * every time, which is slow and wasteful.
 */
public final class ApiClient {

    /**
     * Radio Browser runs several mirrors. If this one is ever unreachable,
     * swap in one of the others:
     *   https://de1.api.radio-browser.info/
     *   https://at1.api.radio-browser.info/
     *   https://nl1.api.radio-browser.info/
     */
    private static final String BASE_URL = "https://de1.api.radio-browser.info/";

    private static Retrofit retrofit;

    private ApiClient() {
    }

    public static synchronized RadioApi radio() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    // GsonConverterFactory is what turns the JSON body into
                    // List<StationDto> for us.
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(RadioApi.class);
    }
}
