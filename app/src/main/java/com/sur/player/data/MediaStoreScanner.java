package com.sur.player.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.sur.player.model.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * WEEK 10 TOPIC: using a Content Provider.
 *
 * Sur does not go hunting through folders for MP3 files. Android already
 * keeps an index of every audio file on the phone and publishes it through
 * a system Content Provider called MediaStore. We ask it with an ordinary
 * query() and get a Cursor back - exactly the same mechanism another app
 * would use to read OUR FavouritesProvider.
 *
 * This runs on a background thread: on a phone with a few thousand songs it
 * takes a second or two, which would freeze the UI.
 */
public class MediaStoreScanner {

    /** Base URI for album artwork. Appending the album id gives the image. */
    private static final Uri ALBUM_ART_BASE = Uri.parse("content://media/external/audio/albumart");

    /**
     * Folders that are not music: chat voice notes and phone recordings.
     * The match ignores upper and lower case, and works on the file path.
     */
    private static final String[] SKIPPED_FOLDERS = {
            "WhatsApp", "com.whatsapp", "Recordings", "Voice Recorder", "Call"
    };

    /** The full file path column. Deprecated for writing, but fine for filtering. */
    @SuppressWarnings("deprecation")
    private static final String DATA_COLUMN = MediaStore.Audio.Media.DATA;

    private final Context context;

    public MediaStoreScanner(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<Song> scan() {
        List<Song> songs = new ArrayList<>();

        // The columns we want. Asking for fewer columns is faster.
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION
        };

        // IS_MUSIC filters out ringtones, notification sounds and podcasts.
        // Anything under 30 seconds is almost always a sound effect.
        //
        // We also skip folders that hold voice notes and recordings rather
        // than music. The "?" marks are filled in from selectionArgs, which
        // is the safe way to put values into a query (never join strings).
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
                + MediaStore.Audio.Media.DURATION + " > 30000";
        String[] selectionArgs = new String[SKIPPED_FOLDERS.length];
        for (int i = 0; i < SKIPPED_FOLDERS.length; i++) {
            selection += " AND " + DATA_COLUMN + " NOT LIKE ?";
            selectionArgs[i] = "%/" + SKIPPED_FOLDERS[i] + "/%";
        }

        String sortOrder = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";

        ContentResolver resolver = context.getContentResolver();

        try (Cursor cursor = resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder)) {

            if (cursor == null) return songs;

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                long albumId = cursor.getLong(albumIdCol);

                Song song = new Song();
                song.setId(id);
                song.setTitle(cursor.getString(titleCol));
                song.setArtist(cursor.getString(artistCol));
                song.setAlbum(cursor.getString(albumCol));
                song.setDurationMs(cursor.getLong(durationCol));

                // The playable URI is built from the table URI plus the row id.
                song.setUri(ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString());

                song.setArtUri(ContentUris.withAppendedId(ALBUM_ART_BASE, albumId).toString());

                songs.add(song);
            }
        }

        return songs;
    }
}
