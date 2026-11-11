package com.sur.player.firebase;

import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.sur.player.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WEEK 11 TOPIC: Realtime Database, writing and reading.
 *
 * Counts how many times each song has been played, and reads back the most
 * played ones.
 *
 * WHY REALTIME DATABASE AND NOT FIRESTORE, which is the comparison the
 * course asks about: a play count is one number that goes up very often,
 * once per song every few minutes. Realtime Database can add one to a number
 * on the server with ServerValue.increment, so the phone never has to read
 * the old value first, and it is billed by how much data moves rather than
 * per operation. Firestore would charge a write every single time. Playlists
 * went the other way, to Firestore, for the reasons in CloudBackup.
 *
 * The shape in the database:
 *
 *   playCounts/{uid}/{songKey}
 *       title   "Midhe Boiar"
 *       artist  "Jibon Dewan"
 *       count   12
 */
public final class PlayCounts {

    public interface TopCallback {
        void onDone(boolean success, @Nullable List<Entry> entries,
                    @Nullable String errorMessage);
    }

    /** One row of the Most played list. */
    public static class Entry {
        public final String title;
        public final String artist;
        public final long count;

        Entry(String title, String artist, long count) {
            this.title = title;
            this.artist = artist;
            this.count = count;
        }
    }

    /**
     * The database lives in Singapore rather than the default US region, and
     * a google-services.json downloaded before the database existed does not
     * carry its address. This is the fallback for that case.
     */
    private static final String DATABASE_URL =
            "https://surr-5d88c-default-rtdb.asia-southeast1.firebasedatabase.app";

    private PlayCounts() {
    }

    @Nullable
    private static DatabaseReference root() {
        try {
            return FirebaseDatabase.getInstance().getReference();
        } catch (Exception first) {
            try {
                return FirebaseDatabase.getInstance(DATABASE_URL).getReference();
            } catch (Exception second) {
                return null;
            }
        }
    }

    /**
     * Adds one to a song's count.
     *
     * ServerValue.increment does the addition on Firebase's own servers. If
     * the phone read the number, added one and wrote it back, two devices
     * playing at once would both read the same old value and one of the
     * plays would be lost.
     */
    public static void addPlay(String userId, Song song) {
        DatabaseReference root = root();
        if (root == null || song == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", song.getTitle());
        updates.put("artist", song.getSubtitle());
        updates.put("count", ServerValue.increment(1L));

        root.child("playCounts").child(userId).child(keyFor(song)).updateChildren(updates);
    }

    /** Reads every count for this user and returns the biggest ones first. */
    public static void top(String userId, int limit, TopCallback callback) {
        DatabaseReference root = root();
        if (root == null) {
            callback.onDone(false, null, "Firebase is not set up in this build.");
            return;
        }

        root.child("playCounts").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<Entry> entries = new ArrayList<>();

                        for (DataSnapshot child : snapshot.getChildren()) {
                            Long count = child.child("count").getValue(Long.class);
                            if (count == null) continue;

                            String title = child.child("title").getValue(String.class);
                            String artist = child.child("artist").getValue(String.class);
                            entries.add(new Entry(
                                    title == null ? "Unknown" : title,
                                    artist == null ? "" : artist,
                                    count));
                        }

                        // Sorted here rather than with orderByChild, because
                        // sorting on the server needs an index rule and the
                        // list is small enough that it makes no difference.
                        Collections.sort(entries, (a, b) -> Long.compare(b.count, a.count));
                        if (entries.size() > limit) {
                            entries.subList(limit, entries.size()).clear();
                        }
                        callback.onDone(true, entries, null);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        callback.onDone(false, null, error.getMessage());
                    }
                });
    }

    /**
     * A key Firebase will accept. Realtime Database keys cannot contain
     * . $ # [ ] or / , and the song's own database id is no good here
     * because MediaStore gives the same song a different id on every phone.
     * Title and artist together survive a reinstall.
     */
    /**
     * The name of the row this song's count lives under.
     *
     * This used to be the title and artist glued together, and that was
     * wrong. Those are things the user can edit. Rename a song in a tag
     * editor and the app would look under a new name, find nothing, start
     * a second count at zero, and leave the old one stranded. One song,
     * two rows, both half right.
     *
     * The id comes from MediaStore and stays the same when tags change, so
     * the count follows the file rather than the words printed on it. The
     * title and artist are still stored, but as plain fields that get
     * overwritten on the next play, so a renamed song shows its new name
     * and keeps its history.
     *
     * The honest limit: a MediaStore id belongs to one phone. The same song
     * on another device has a different id, so counts do not follow a user
     * across phones. Fixing that properly needs a fingerprint of the audio
     * itself, which is well beyond what this project needs.
     */
    private static String keyFor(Song song) {
        return song.getKey().replaceAll("[.$#\\[\\]/]", "_");
    }
}
