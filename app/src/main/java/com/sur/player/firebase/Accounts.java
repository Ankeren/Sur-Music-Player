package com.sur.player.firebase;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * WEEK 11 TOPIC: Firebase Authentication.
 *
 * Everything that touches FirebaseAuth goes through this one class, for two
 * reasons. The rest of the app never imports a Firebase type, and every call
 * here is wrapped so that a missing or broken google-services.json cannot
 * crash the app. If Firebase is not set up, isAvailable() returns false and
 * the app carries on as an ordinary offline player.
 *
 * Sur does not force anyone to sign in. An account only unlocks the cloud
 * features: backing up playlists and keeping play counts. The music itself
 * always works offline, which is the whole point of the app.
 */
public final class Accounts {

    /** Something to hand a result back to the screen that asked. */
    public interface Callback {
        void onDone(boolean success, @Nullable String errorMessage);
    }

    private Accounts() {
    }

    @Nullable
    private static FirebaseAuth auth() {
        try {
            return FirebaseAuth.getInstance();
        } catch (Exception e) {
            // Thrown when google-services.json is missing or malformed.
            return null;
        }
    }

    /** False when Firebase was never configured for this build. */
    public static boolean isAvailable() {
        return auth() != null;
    }

    public static boolean isSignedIn() {
        FirebaseAuth a = auth();
        return a != null && a.getCurrentUser() != null;
    }

    /**
     * The signed in user's unique id, or null. Everything this app writes to
     * the cloud is filed under this id, so two people sharing a phone never
     * see each other's playlists.
     */
    @Nullable
    public static String userId() {
        FirebaseAuth a = auth();
        if (a == null) return null;
        FirebaseUser user = a.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    @Nullable
    public static String email() {
        FirebaseAuth a = auth();
        if (a == null) return null;
        FirebaseUser user = a.getCurrentUser();
        return user == null ? null : user.getEmail();
    }

    /** Makes a new account. Firebase rejects passwords under 6 characters. */
    public static void signUp(String email, String password, Callback callback) {
        FirebaseAuth a = auth();
        if (a == null) {
            callback.onDone(false, "Firebase is not set up in this build.");
            return;
        }
        a.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> callback.onDone(
                        task.isSuccessful(), messageOf(task.getException())));
    }

    public static void signIn(String email, String password, Callback callback) {
        FirebaseAuth a = auth();
        if (a == null) {
            callback.onDone(false, "Firebase is not set up in this build.");
            return;
        }
        a.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> callback.onDone(
                        task.isSuccessful(), messageOf(task.getException())));
    }

    public static void signOut() {
        FirebaseAuth a = auth();
        if (a != null) a.signOut();
    }

    @Nullable
    private static String messageOf(@Nullable Exception e) {
        if (e == null) return null;
        String message = e.getLocalizedMessage();
        return message == null ? e.toString() : message;
    }
}
