# Opening Sur in Android Studio Dolphin 2021.3.1

This copy has been downgraded from the original (AGP 8.5.2 / Gradle 8.7 /
compileSdk 34 / Java 17) so that Dolphin can build it.

## What changed

| | Before | Now |
|---|---|---|
| Android Gradle Plugin | 8.5.2 | 7.3.1 |
| Gradle | 8.7 | 7.4 |
| compileSdk / targetSdk | 34 | 33 |
| Java source/target | 17 | 8 |

All androidx dependencies were moved down to the last release published for
compileSdk 33. No `.java` file was touched — the source was already plain
Java 8 and needed no changes.

## Before you open it

The archive has no Gradle wrapper (`gradlew`, `gradlew.bat`,
`gradle/wrapper/gradle-wrapper.jar`). Copy those three from the empty
project Dolphin generated for you:

    From: C:\Users\LENOVO\AndroidStudioProjects\FirstTrial\
      gradlew
      gradlew.bat
      gradle\wrapper\gradle-wrapper.jar

    To:   <this folder>\
      gradlew
      gradlew.bat
      gradle\wrapper\gradle-wrapper.jar

Do **not** copy `gradle-wrapper.properties` — the one here is already correct.

## Then

1. Android Studio → **File → Open** → select this folder (the one with
   `settings.gradle`). Do not use Import.
2. Let Gradle sync. API 33 and Build-Tools 33.0.1 must be installed
   (Tools → SDK Manager).
3. Refuse every "Android Gradle Plugin upgrade available" prompt. AGP 7.3.1
   is the ceiling Dolphin supports; accepting breaks the build.
4. Plug in a real phone with a few MP3 files on it and press Run.

## If a dependency fails

An error reading

    Dependency 'androidx.<something>:<version>' requires libraries and
    applications that depend on it to compile against version 34 or later

means that one library version is still too new. Lower it one minor release
in `app/build.gradle` and sync again. Everything else stays as it is.
