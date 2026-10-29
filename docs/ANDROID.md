# Playing on an Android phone

The release page has an **APK that contains no game content**. Prepare a game pack on a computer from your own game files, place it beside the resources on internal storage or an SD card, then choose that folder in the app. Its location is remembered and the files are read in place. No adb, Android Studio, or local APK build is required.

中文：[ANDROID.zh-CN.md](ANDROID.zh-CN.md)

**Where it stands:**

| Step | Status |
|---|---|
| Making the import pack on a computer | Works on a Mac: about 5 minutes from scratch (on an Apple silicon Mac; translating the shaders is more than half of it), an import pack of about 670 MB. Not yet tried on Windows / Linux; please open an Issue and tell us how it goes |
| Shaders in the import pack | The 6,927 game shader bundles are byte-for-byte identical to the set the port's authors tested on their phones |
| Installing the APK, importing, playing | Works on a vivo X200 Ultra (Android 16): permission, import, character creation, playing, panels, NPC dialogs |
| Frame rate | Measured by the port's authors: median 60 fps in the camp and in fights on a Redmi K50 Ultra (Snapdragon 8+ Gen 1); playable on a OnePlus 13 |

## Phone requirements

- ARM64, Android 12 (API 31) or later, a GPU with Vulkan 1.1
- About 7 GB free: about 5.6 GB of game resources plus about 0.7 GB of imported content (after the import you can delete the import pack on the phone and get 0.7 GB back)

## What you need

- Your own `ExeFS` and `RomFS`: Switch 1.0.34.0 (enhanced touch profile) or 1.0.33.0 (Android original UI, physical controller required). See [GAME_FILES.md](GAME_FILES.md) and [1.0.33 limits](VERSION_1_0_33.md).
- The source of this repository **from the same release as the APK** (the App checks and tells you if they differ)
- Python 3.9 or later with these packages: `python3 -m pip install pillow numpy lz4 capstone unicorn`
- The .NET SDK (to build the shader translation tool): <https://dotnet.microsoft.com/download>
- `spirv-opt`, `spirv-val` and `glslc` from the Vulkan SDK: the [LunarG Vulkan SDK](https://vulkan.lunarg.com/) on Windows / Linux, `brew install spirv-tools shaderc` on macOS. `spirv-opt` must be new enough to support `--trim-capabilities` (the tool checks); the copy that ships with the Android NDK is too old, so do not let it come first on `PATH`. `glslc` from the Android NDK works too
- About 2 GB of disk space: about 0.9 GB of caches and intermediate products (in `DM_ROOT`, default `~/dm-work`) and about 0.7 GB for the import pack

The tool lists anything missing at the start and says where to get it.

## Step 1: make the import pack on a computer

From the repository root:

```
python3 dm/tools/d2rm_android.py <game files directory> <output directory>
```

`<game files directory>` is the recognized game's `Update` directory with `ExeFS` and `RomFS`. The tool selects the matching profile, rewrites the program and translates its shaders. Version 1.0.34 also generates the touch overlay and data tables. Version 1.0.33 uses the original controller UI and has no overlay. Both write `<output directory>/android/`:

```
android/out/          the rewritten game program and its tables
android/shaders/      Vulkan shader bundles (6,931)
android/overlay/      touch overlay and UI art
android/derived.tsv   a few data tables generated from the game files
android/pack_info.txt which release this import pack is for
```

Steps already done are skipped on the next run. This folder contains game content; keep it to yourself and do not distribute it.

## Step 2: choose where to store the game

On internal shared storage or an SD card, create a game folder containing:

```
My D2R Game/android/   complete generated pack from step 1
My D2R Game/data/      your resource archives and .build.config
```

The folder name and location are up to you. Keep the French audio archives printed
by the pack generator as well as the base resources. Copying the whole RomFS/data
directory includes all available voice languages. The old 18-archive minimal set
does not contain the French voices.

## Step 3: install and choose the folder

Install `D2RMobile-android.apk` from this fork's GitHub Releases and grant all-files
access as requested. Tap **Choose game folder**, then select the local folder in
Android's directory picker. Selecting its `data` child works too. The app remembers
the folder and reads `data` and `android` in place, without importing another copy.
Keep both directories. Cloud/virtual providers cannot be used by the native host.

An existing internal imported runtime is retained as a migration fallback if no
prepared external runtime exists. Saves, logs and Vulkan caches remain internal.
If a folder becomes unavailable, choose it again. The Android Menu key also opens
the folder picker. A folder change after the native host has loaded takes effect
after fully closing and reopening the app.

French UI selects French voices only when every archive recorded for French in
the prepared pack is readable; otherwise it uses English voices. Regenerate older
packs to include the voice metadata. Switch 1.0.33.0 uses the `original133` profile;
its generator was tested under WSL/Ubuntu and its runtime on Odin 2 Portal.
See [the compatibility tests and limits](VERSION_1_0_33.md).
Current CI APKs include FFmpeg VP8 decoding, including the animated
title background. Movie audio, subtitles and timing remain controlled by the game.

## Moving to a new release

Each new release needs both: **download the new APK**, and **redo step 1 with the source of the same release**, copying the new `android` folder over the old one. The new APK installs over the old one and keeps your saves; when opened, the App reads the updated prepared pack from the saved folder. The `data` folder stays as it is.

If the APK and the import pack are from different releases, the App says "This import pack does not match this version of the app"; redo the import pack as it says.

## Saves and uninstalling

Saves live in the App's internal storage. **Uninstalling the App deletes the saves with it.** In these cases you have to uninstall before you can install our APK, so find a way to back up your saves first:

- the phone has an earlier build signed by the port's authors (same package name, different signature)
- you built the APK yourself from source (signed with the key on your computer)

The other way round holds too: as long as you upgrade with our released APK, your saves stay.

## When something goes wrong

| Message | What to do |
|---|---|
| Needs "All files access" | Tap the screen, turn it on in Settings, come back to the App |
| Game resources were not found | Choose a readable local folder containing the game archives |
| The .build.config file is missing | Copy `.build.config` from your game files' `RomFS/data` into `data` too |
| Game content has not been imported yet | Keep the complete prepared `android` folder next to `data`, then select their parent |
| This import pack does not match this version of the app | Redo step 1 with the source of the same release as the APK |
| The import did not finish | Usually not enough free space; free about 1 GB and open the App again |

To build the APK yourself from source, or for the layout of the Android source, see [dm/android/README.md](../dm/android/README.md).
