# NRO Offline — Native Android v0.1

This is a NEW Android Java application (Canvas rendering, **no J2ME emulator**).
It packages the repository's existing `../data/` directory as offline assets.

## What is implemented

- Native fullscreen landscape Activity and game loop.
- Load real standard PNG artwork from the original data archive.
- Local move / jump / attack / enemy / HP simulation with camera follow.
- Touch and multitouch controls.
- No Internet permission, external server, or Termux dependency.

## Still missing

Original NRO sprite formats, gameplay rules, character animations, NRO map
decoder and collision tiles, quests, inventory, account state, save/load.
Some original files named .png are actually cRBC-encoded, not PNG data;
OfflineAssets currently rejects them safely until a decoder is ported.

**This APK is a native Android proof of concept; it is NOT yet a full
conversion of the original NRO game.**

## Build

This branch includes a GitHub Actions workflow:
[Build NRO Android Native APK](../../actions/workflows/android-native.yml).
Each push to android-native/** triggers a build. Once the job succeeds,
download artifact `NRO-Offline-Native-v0.1-debug` containing app-debug.apk.

Or build locally with Java 17, Android SDK platform 35 and Gradle 8.10.2:

```sh
cd android-native
gradle :app:assembleDebug
```

Source and CI are committed ONLY to `offline-j2me`, not `main`.
