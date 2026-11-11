package com.sur.player;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.sur.player.databinding.ActivityLoginBinding;
import com.sur.player.firebase.Accounts;
import com.sur.player.util.ThemeHelper;

/**
 * WEEK 11 TOPIC: Firebase Authentication, and WEEK 8: explicit Intents and
 * the Activity lifecycle.
 *
 * The first screen on a fresh install. It offers three ways forward:
 * create an account, sign in to one, or carry on without either.
 *
 * That third button matters. Sur is an offline music player, so refusing to
 * open a library of songs that are already on the phone just because there
 * is no internet would be the wrong design. An account only unlocks the
 * cloud extras: playlist backup, and play counts. Everything else works
 * signed out.
 *
 * Once the user has signed in, or has chosen to skip, this screen stops
 * appearing and the app opens straight into MainActivity.
 */
public class LoginActivity extends AppCompatActivity {

    /**
     * Put this on the Intent when opening the screen on purpose, for example
     * from the Sign in button in Settings.
     *
     * Without it, the checks in onCreate would forward a user who had already
     * tapped "Continue without an account" straight back to the library, so
     * once skipped they could never reach the form again.
     */
    public static final String EXTRA_FORCE = "force_login";

    private static final String PREFS = "sur_prefs";
    private static final String KEY_SKIPPED = "auth_skipped";

    private ActivityLoginBinding views;

    /** True when Settings sent us here, rather than this being app startup. */
    private boolean openedOnPurpose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);

        openedOnPurpose = getIntent().getBooleanExtra(EXTRA_FORCE, false);

        // On app startup only: already signed in, or chose to skip last time,
        // so do not ask again. Firebase being missing entirely counts as
        // "carry on" too, so a broken google-services.json can never lock the
        // user out of their own music.
        if (!openedOnPurpose
                && (!Accounts.isAvailable() || Accounts.isSignedIn() || hasSkipped())) {
            openMain();
            return;
        }

        views = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(views.getRoot());

        views.signInButton.setOnClickListener(v -> submit(false));
        views.signUpButton.setOnClickListener(v -> submit(true));

        views.skipButton.setOnClickListener(v -> {
            prefs().edit().putBoolean(KEY_SKIPPED, true).apply();
            done();
        });
    }

    /** Shared by both buttons, because only one Firebase call differs. */
    private void submit(boolean creatingAccount) {
        String email = views.emailInput.getText() == null
                ? "" : views.emailInput.getText().toString().trim();
        String password = views.passwordInput.getText() == null
                ? "" : views.passwordInput.getText().toString();

        // Checked here so the user gets an instant answer instead of waiting
        // for a round trip to Firebase just to be told the box was empty.
        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            views.emailLayout.setError(getString(R.string.error_email));
            return;
        }
        views.emailLayout.setError(null);

        if (password.length() < 6) {
            views.passwordLayout.setError(getString(R.string.error_password));
            return;
        }
        views.passwordLayout.setError(null);

        setBusy(true);

        Accounts.Callback callback = (success, errorMessage) -> {
            // The callback arrives on the main thread, but the user may have
            // left the screen in the meantime.
            if (isFinishing() || isDestroyed()) return;
            setBusy(false);

            if (success) {
                done();
            } else {
                views.errorText.setVisibility(View.VISIBLE);
                views.errorText.setText(errorMessage == null
                        ? getString(R.string.error_generic) : errorMessage);
            }
        };

        if (creatingAccount) {
            Accounts.signUp(email, password, callback);
        } else {
            Accounts.signIn(email, password, callback);
        }
    }

    private void setBusy(boolean busy) {
        views.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        views.signInButton.setEnabled(!busy);
        views.signUpButton.setEnabled(!busy);
        views.skipButton.setEnabled(!busy);
        if (busy) views.errorText.setVisibility(View.GONE);
    }

    /**
     * Where to go once the user is finished here.
     *
     * Opened from Settings, we just close and the user lands back on
     * Settings, which refreshes its account line in onResume. Opened at
     * startup, we move on to the library.
     */
    private void done() {
        if (openedOnPurpose) {
            finish();
        } else {
            openMain();
        }
    }

    /** WEEK 8: an explicit Intent, naming the exact class to open. */
    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        // finish() so the back button from the library leaves the app rather
        // than coming back here.
        finish();
    }

    private boolean hasSkipped() {
        return prefs().getBoolean(KEY_SKIPPED, false);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    /** Called from Settings after signing out, so the screen shows again. */
    public static void clearSkip(android.content.Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean(KEY_SKIPPED, false).apply();
    }
}
