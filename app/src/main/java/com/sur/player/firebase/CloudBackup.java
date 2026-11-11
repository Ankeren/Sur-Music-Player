package com.sur.player.firebase;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.sur.player.model.Playlist;
import com.sur.player.model.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WEEK 11 TOPIC: Cloud Firestore, writing and reading.
 *
 * Backs playlists up to the cloud and brings them back.
 *
 * WHY FIRESTORE AND NOT REALTIME DATABASE, which is the comparison the
 * course asks about: a playlist is a document with a name and a list of
 * songs inside it, and Firestore stores documents with nested arrays
 * directly. Realtime Database is one big JSON tree, so the same thing would
 * have to be flattened into keys and stitched back together on the way out.
 * Firestore also charges per document read, which suits something saved by
 * hand now and then. Play counts went the other way, to Realtime Database,
 * for the reasons in PlayCounts.
 *
 * The shape in Firestore:
 *
 *   users/{uid}/playlists/{playlistId}
 *       name     "Evening songs"
 *       savedAt  1762300000000
 *       songs    [ {title, artist, uri}, {title, artist, uri}, ... ]
 */
public final class CloudBackup {

    public interface BackupCallback {
        void onDone(boolean success, int playlistCount, @Nullable String errorMessage);
    }

    public interface RestoreCallback {
        void onDone(boolean success, @Nullable List<RestoredPlaylist> playlists,
                    @Nullable String errorMessage);
    }

    /** One playlist read back out of the cloud, before it is written to Room. */
    public static class RestoredPlaylist {
        public final String name;
        public final List<String> songUris = new ArrayList<>();

        RestoredPlaylist(String name) {
            this.name = name;
        }
    }

    private CloudBackup() {
    }

    @Nullable
    private static FirebaseFirestore db() {
        try {
            return FirebaseFirestore.getInstance();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Writes every playlist. A WriteBatch sends them as ONE request that
     * either all succeeds or all fails, instead of one request per playlist.
     */
    public static void backup(String userId, List<Playlist> playlists,
                              Map<Long, List<Song>> songsByPlaylist,
                              BackupCallback callback) {
        FirebaseFirestore firestore = db();
        if (firestore == null) {
            callback.onDone(false, 0, "Firebase is not set up in this build.");
            return;
        }

        WriteBatch batch = firestore.batch();

        // The parent users/{uid} document. Firestore lets a collection hang
        // under a document that has no fields of its own, but the console
        // then shows it greyed out as "this document does not exist", which
        // is confusing. Writing the email and a timestamp makes it real and
        // records when the last backup happened.
        Map<String, Object> account = new HashMap<>();
        account.put("email", Accounts.email());
        account.put("lastBackup", System.currentTimeMillis());
        batch.set(firestore.collection("users").document(userId), account);

        for (Playlist playlist : playlists) {
            List<Map<String, Object>> songs = new ArrayList<>();
            List<Song> inThisPlaylist = songsByPlaylist.get(playlist.getId());

            if (inThisPlaylist != null) {
                for (Song song : inThisPlaylist) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("title", song.getTitle());
                    entry.put("artist", song.getArtist());
                    entry.put("uri", song.getUri());
                    songs.add(entry);
                }
            }

            Map<String, Object> document = new HashMap<>();
            document.put("name", playlist.getName());
            document.put("savedAt", System.currentTimeMillis());
            document.put("songs", songs);

            batch.set(firestore.collection("users")
                    .document(userId)
                    .collection("playlists")
                    .document(String.valueOf(playlist.getId())), document);
        }

        final int count = playlists.size();
        batch.commit()
                .addOnSuccessListener(ignored -> callback.onDone(true, count, null))
                .addOnFailureListener(e -> callback.onDone(false, 0, e.getLocalizedMessage()));
    }

    /** Reads every playlist back. The caller decides what to do with them. */
    public static void restore(String userId, RestoreCallback callback) {
        FirebaseFirestore firestore = db();
        if (firestore == null) {
            callback.onDone(false, null, "Firebase is not set up in this build.");
            return;
        }

        firestore.collection("users")
                .document(userId)
                .collection("playlists")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<RestoredPlaylist> out = new ArrayList<>();

                    for (QueryDocumentSnapshot document : snapshot) {
                        String name = document.getString("name");
                        if (name == null) continue;

                        RestoredPlaylist playlist = new RestoredPlaylist(name);

                        Object raw = document.get("songs");
                        if (raw instanceof List) {
                            for (Object element : (List<?>) raw) {
                                if (!(element instanceof Map)) continue;
                                Object uri = ((Map<?, ?>) element).get("uri");
                                if (uri != null) playlist.songUris.add(uri.toString());
                            }
                        }
                        out.add(playlist);
                    }
                    callback.onDone(true, out, null);
                })
                .addOnFailureListener(e -> callback.onDone(false, null, e.getLocalizedMessage()));
    }
}
