# Sur — Offline-first Music Player with Live Radio

An Android music player written in **Java**. It plays the songs already on
your phone with no internet at all, and adds live internet radio and podcast
shows for when there is data.

Built for the Advanced Programming Laboratory project (week 13 demo).

---

## Run it

1. **Android Studio → Open** → choose this folder (the one with `settings.gradle`).
2. Let Gradle sync. The first sync downloads Gradle and the libraries, so give
   it a few minutes on a slow connection.
3. Plug in a **real phone** with a handful of MP3 files on it and press Run.

> **Use a real phone, not the emulator.** A fresh emulator has no music, so
> the library will be empty, and audio in the emulator is unreliable. If you
> must use the emulator, drag two or three MP3 files onto it first.

On first launch the app asks for two permissions — music, and notifications.
Both are needed: without the notification permission, playback still works but
the notification with the play/pause buttons never appears.

Firebase is switched **off** by default so the project builds immediately.
To turn it on, follow `firebase/SETUP.md` (about 15 minutes).

---

## What is in it

| Screen | What it does |
|---|---|
| **Library** | Every song on the phone, with search and favourites |
| **Playlists** | Create, rename, delete; add and remove songs |
| **Listen live** | Radio stations (JSON API) and podcast shows (RSS/XML) |
| **Player** | Full-screen player with a hand-drawn circular seek bar |
| **Settings** | Sleep timer, library rescan, Firebase backup |

Music keeps playing when you close the app, with controls in the notification
and on the lock screen.

---

## Where each topic lives

Bring this table to the demo. It is the answer to "show me where you
implemented X".

| Topic | File |
|---|---|
| Abstract class, inheritance | `model/MediaItem.java` |
| Interface (generic behaviour) | `model/Playable.java` |
| Encapsulation | every class in `model/` |
| Thread and Runnable, thread pools, executors | `util/AppExecutors.java` |
| Synchronization, shared resource | `service/PlaybackQueue.java` |
| SQLite tables, CREATE | `data/AppDatabase.java` |
| Insert, update, delete, query | `data/SongDao.java`, `data/PlaylistDao.java` |
| Raw SQL at run time | `Repository.getMostPlayedSince()` |
| Many-to-many relationship | `model/PlaylistSong.java` |
| **Service** (foreground, background playback) | `service/MusicService.java` |
| **Broadcast Receiver**, registered in code | `service/NoisyReceiver.java` |
| **Broadcast Receiver**, a system setting (airplane mode) | `service/AirplaneModeReceiver.java` |
| **Broadcast Receiver**, declared in manifest | `service/BootReceiver.java` |
| Notification buttons → receiver → service | `service/PlaybackActionReceiver.java` |
| **Content Provider**, consumed (with selection args and folder filter) | `data/MediaStoreScanner.java` |
| **Content Provider**, written by us | `data/FavouritesProvider.java` |
| Activity ↔ Service binding | `ui/BoundActivity.java` |
| Permissions at run time | `MainActivity.askForPermissions()` |
| RecyclerView, adapter, ViewHolder | `ui/SongAdapter.java` |
| Drag to reorder, swipe to delete (`ItemTouchHelper`) | `PlaylistDetailActivity.java` |
| Second adapter, "Up next" queue sheet | `ui/QueueAdapter.java`, `PlayerActivity.showQueue()` |
| **RelativeLayout** (the course's other layout manager) | `res/layout/item_queue.xml` |
| Second custom view, animated | `ui/EqualizerBarsView.java` |
| Custom `Drawable` (letter tiles for missing album art) | `util/LetterTile.java` |
| Colours read from an image (Palette) | `util/ArtworkTheme.java`, `PlayerActivity.applyArtworkTheme()` |
| **Firebase Authentication**, email and password | `LoginActivity.java`, `firebase/Accounts.java` |
| **Cloud Firestore**, write and read | `firebase/CloudBackup.java` (playlist backup and restore) |
| **Realtime Database**, write and read | `firebase/PlayCounts.java`, `MostPlayedActivity.java` |
| Streaming formats (HLS, DASH) | `app/build.gradle`, handled in `MusicService.startCurrent()` |
| **Custom view** | `ui/CircularSeekBar.java` |
| Responsive layout | `res/layout/activity_main.xml`, `res/layout-land/activity_player.xml` |
| Reusable component | `res/layout/mini_player.xml` |
| Themes and styles, SharedPreferences | `res/values/themes.xml`, `util/ThemeHelper.java` |
| Explicit intent | `MainActivity`, `PlaylistsFragment` |
| Implicit intent (share) | `ListenLiveFragment.onStationMenu()` |
| HTTP request, **JSON** parsing | `net/RadioApi.java`, `net/StationDto.java` |
| HTTP request, **XML** parsing | `net/RssParser.java` |
| Firebase | `firebase/BackupHelper.java` (optional) |

---

## Proving the Content Provider works

This is the one component that is hard to show on screen, because the whole
point is that *another* app reads it. Easiest proof, with the phone plugged in:

```bash
adb shell content query --uri content://com.sur.player.favourites/favourites
```

Favourite two or three songs in the app first. The command prints them,
straight out of your database, with Sur closed.

---

## Things that are deliberately not here

Say these out loud in the demo. Scoping decisions score better than gaps.

- **No streaming of commercial music.** Licensing. All online audio is free
  public radio and open podcast feeds.
- **No login screen.** Firebase signs you in anonymously instead.
- **No live cross-device sync.** A manual backup button, which needs no
  conflict handling.
- **No equalizer.** Impressive for ten seconds, costs two days.

---

## Known limitations

- Album art comes from the MediaStore album-art index. Some files have none,
  and those rows show the grey placeholder.
- Radio stations come from a public directory and some of them are dead. The
  app filters on the "last check ok" flag and skips a stream that fails, but
  **test your three demo stations before recording**.
- Restoring a Firebase backup onto a different phone gives you the playlist
  names but not the songs, because the song files are not on that phone.
- The sleep timer uses an inexact alarm on Android 12+ unless the user grants
  the exact-alarm permission. A minute either way does not matter here.

---

## Before you record the demo

1. Delete the app and reinstall, so the permission dialogs appear on camera.
2. Put 8–10 MP3 files on the phone.
3. Make one playlist with three songs in it, ready to be shown.
4. Check the radio tab actually plays, and note three stations that work.
5. Paste a podcast RSS URL into the Shows segment and check it loads.
6. Practise the headphone-unplug moment — it is the clearest proof of the
   BroadcastReceiver, and it takes one second.

---

## One honest note

You will be asked to explain the architecture, the data flow and your design
decisions. Read `MusicService.java`, `Repository.java` and `AppExecutors.java`
until you could redraw them from memory — those three files are where almost
every question will land.


---

## Firebase notes (week 11)

### What Firebase is

A set of ready made backend services from Google. Instead of writing a
server, hosting it and keeping it running, the app talks to Google's
servers through a library. Sur uses three of those services:
Authentication for accounts, Cloud Firestore for playlist backup, and
Realtime Database for play counts.

`google-services.json` is what ties this app to one Firebase project. The
`com.google.gms.google-services` Gradle plugin reads that file at build
time. Without it the libraries have no idea which project to talk to.

### Why this app uses BOTH databases

This is the comparison the course material asks about, and the honest
answer is that each one fits a different job.

| | Cloud Firestore | Realtime Database |
|---|---|---|
| Shape of the data | Documents and collections, which can hold nested arrays | One large JSON tree |
| Used in Sur for | Playlists | Play counts |
| Why | A playlist is naturally a document: a name plus a list of songs. Firestore stores that nesting directly. | A play count is one number that goes up constantly. `ServerValue.increment` adds to it on the server. |
| Billing | Per document read and write | By how much data moves |
| Queries | Rich, with indexes | Limited, one `orderByChild` at a time |

Putting playlists in Realtime Database would mean flattening the song
list into keys and rebuilding it on the way out. Putting play counts in
Firestore would mean paying for a write on every single play.

### Why there is a "Continue without an account" button

Sur is an offline music player. Requiring an account before the user can
play songs that are already on their phone would break the one thing the
app promises. So the sign in screen offers a way past it, and an account
only unlocks the two cloud features. Everything else works signed out and
with no internet at all.

### Security rules

Both databases were created in **test mode**, which allows any read and
write for 30 days. That is correct for a student project and wrong for a
real app, where the rules would restrict each user to their own data,
something like:

    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }


## Broadcast receivers: why two are in code and one is in the manifest

| Receiver | Registered | Why |
|---|---|---|
| `NoisyReceiver` | In code, from `MusicService.onCreate()` | Only matters while something is playing. Pauses when headphones are pulled out. |
| `AirplaneModeReceiver` | In code, from `MusicService.onCreate()` | Since Android 8, most implicit broadcasts no longer reach receivers declared only in the manifest. `ACTION_AIRPLANE_MODE_CHANGED` is one of them, so a manifest entry would never fire. |
| `BootReceiver` | In the manifest | Has to work when the app is not running at all, so there is nothing alive to register it. |
| `PlaybackActionReceiver` | In the manifest | Receives the app's own notification button presses. |

Airplane mode is also the clearest demonstration of what this app is for.
Turn it on while a **song** is playing and nothing happens, because the file
is on the phone. Turn it on while a **radio station** is playing and
playback stops with a message, because the stream is gone.
