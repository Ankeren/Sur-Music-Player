# Firebase setup for Sur

Firebase is now **switched on** in this project. The Gradle plugin and the
three libraries are active in `build.gradle` and `app/build.gradle`.

## The one file you must add yourself

`app/google-services.json`

It is not in this zip, because it belongs to one Firebase project and one
Google account. Without it **the build fails** with a message about the
google-services plugin not finding its configuration.

Get it from the Firebase console:
**Settings gear → General → Your apps → google-services.json**

Put it at `app/google-services.json`, next to `app/build.gradle`.

## What the console needs to have

| Service | Setting |
|---|---|
| Authentication | Sign-in method → **Email/Password** enabled |
| Cloud Firestore | Database created, **test mode** rules |
| Realtime Database | Database created, **test mode** rules |

The Android app in the console must be registered with the package name
`com.sur.player`, exactly.

## Download google-services.json again if you add a database later

The file records which services existed when it was downloaded. If the
Realtime Database was created after the download, the file has no database
URL in it and the app fails with "Can't determine Firebase Database URL".

`PlayCounts.java` carries the URL as a fallback for exactly this case, but
re-downloading the file is the proper fix.

## If a cloud button does nothing

Check the database rules first. Locked rules reject every read and write,
and the failure is quiet. In the console, Firestore and Realtime Database
each have a **Rules** tab.

Test mode looks like this in Firestore:

    allow read, write: if request.time < timestamp.date(2026, 12, 7);

and like this in Realtime Database:

    { "rules": { ".read": true, ".write": true } }

## Note on the repository

`app/google-services.json` is committed to this repository on purpose, so
that anyone cloning it can build the project straight away. In a real app
it would be kept out, and the security rules would be locked down instead
of left in test mode.

## What each service is used for

- **Authentication** - `LoginActivity`, `firebase/Accounts.java`
- **Firestore** - playlist backup and restore, `firebase/CloudBackup.java`
- **Realtime Database** - play counts and the Most played screen,
  `firebase/PlayCounts.java`

See the Firebase notes at the bottom of the main README for why the
playlists and the play counts use different databases.
