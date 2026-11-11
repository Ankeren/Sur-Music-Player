package com.sur.player.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * WEEK 10 TOPIC: writing your own Content Provider.
 *
 * MediaStoreScanner READS a system provider. This class is the other half:
 * it PUBLISHES Sur's favourite songs so that a completely different app can
 * read them, without Sur being open and without touching our database file.
 *
 * It is deliberately read-only: insert, update and delete all refuse. Other
 * apps may look at the favourites, never change them.
 *
 * Try it from another app (or from adb):
 *     adb shell content query --uri content://com.sur.player.favourites/favourites
 *
 * The authority string here must match the one in AndroidManifest.xml.
 */
public class FavouritesProvider extends ContentProvider {

    public static final String AUTHORITY = "com.sur.player.favourites";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/favourites");

    private static final int CODE_FAVOURITES = 1;

    /** Matches an incoming URI against the patterns we understand. */
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "favourites", CODE_FAVOURITES);
    }

    private SongDao songDao;

    @Override
    public boolean onCreate() {
        // Do not open the database here with heavy work: onCreate runs on the
        // main thread when the process starts. Getting the Room instance is
        // cheap because Room opens the file lazily on first query.
        songDao = AppDatabase.getInstance(getContext()).songDao();
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri,
                        @Nullable String[] projection,
                        @Nullable String selection,
                        @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {

        if (MATCHER.match(uri) != CODE_FAVOURITES) {
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        // Room hands us a Cursor straight from SQLite, which is exactly what
        // a ContentProvider is supposed to return.
        Cursor cursor = songDao.favouritesCursor();

        // Tells the caller to refresh automatically if this data changes.
        if (cursor != null && getContext() != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        if (MATCHER.match(uri) == CODE_FAVOURITES) {
            return "vnd.android.cursor.dir/vnd.com.sur.player.favourite";
        }
        return null;
    }

    // ---- read-only: the three writing methods all refuse -----------------

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("Favourites are read-only to other apps.");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Favourites are read-only to other apps.");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Favourites are read-only to other apps.");
    }
}
