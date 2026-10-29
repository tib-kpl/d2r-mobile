# Switch 1.0.33.0: original controller profile on Android

The `original133` profile runs the recognized 1.0.33 program set on Android using
the original Switch UI and a physical controller. The 1.0.34 touch profile remains
separate. Game archives, programs, keys and generated shaders stay local and are
never included in the APK or uploaded to GitHub.

| Module | Recognized build ID |
|---|---|
| `main` | `194361731c7b9f30b38425b244429e25` |
| `sdk` | `2d369d9999e3e10e665106f840ffff9b` |
| `subsdk0` | `507f19d1240d09d00992248e8a0fd77e` |

The main program has different addresses. Only its two verified offline-entry
patches are applied, with original-byte assertions. The 1.0.34 touch/gameplay,
save-throttle, renderer and heap-diagnostic addresses are not used. The SDK and
movie module match the existing port. Unknown or mixed module sets are rejected.

The shader index is reconstructed from the owner's manifest and program by
control hashes. All 6,927 shaders match the public interface facts; translation
and Vulkan validation succeeded for all of them. Four project query kernels make
6,931 shader bundles. Rewriter equivalence tests passed for all 25,091 main sites
and 11,975 SDK sites, with no divergence and all 100 mutation checks detected in
each module. Offline patches are intentional changes and are recorded separately.

## Hardware checks, 28 October 2026

On an Odin 2 Portal (Android 13, Adreno 740), with the complete game resources and
prepared runtime read directly from the removable SD card:

- French startup, calibration, menus, character classes and inventory rendered.
- Created the offline barbarian `OdinTest`, including the Android name keyboard.
- Entered the Rogue Encampment, moved using controller axes and opened Warriv's dialogue.
- Used Save and Exit, stopped the process, installed the integrated APK, then
  reloaded the same character into the camp through the remembered SD directory.
- The French audio manifest identifies `08-00000001.data` and `08-00000002.data`;
  a decoded French entry passed its manifest MD5 check.
- The original Audio options show French selected for both voice and text.
- Controller movement and returning from the Android launcher also passed with
  the integrated APK. The eight Android unit tests passed under Linux.

## Limits

This is the original controller UI: no touch controls, custom camera or 1.0.34
gameplay extensions. APKs built with FFmpeg decode VP8 movies; the startup logo
and animated title background were verified on Odin 2 Portal. Synthetic ARM64
tests also verify all decoded frames, loops, skipping and teardown. All acts'
cinematics have not been individually tested. The original save throttle is
retained, so use Save and Exit before stopping the app. Saves and GPU caches stay
in app storage; the SD folder must remain available.

These checks cover startup and the beginning of Act I, not every class, act,
endgame system or a long stability session. The profile is Android-only.

Prepare it using the same command as the other profile:

```sh
python3 dm/tools/d2rm_android.py /path/to/Update /path/to/output
```

The generator chooses `original133` automatically and writes it in `pack_info.txt`.
Keep the resulting `android` folder beside `data`, including the French archives,
and select their parent folder in the app. See [the French guide](ANDROID.fr.md).
