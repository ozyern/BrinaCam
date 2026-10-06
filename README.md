BrinaCam
========

A camera app for Android with a OnePlus-style capture screen, written in Kotlin
with Jetpack Compose and CameraX. Controls use liquid-glass surfaces from
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).

- Photo, Video and Hi-Res modes, plus Night and Portrait where the phone exposes
  them to apps (CameraX extensions)
- Zoom presets with a drag-to-zoom dial, pinch to zoom, and the ultra-wide lens
  when the camera reports it
- Tap to focus, then drag up or down to adjust exposure
- Flash, timer, EV, grid, aspect ratio (4:3 / 16:9 / 1:1), Auto HDR, mirrored
  selfies and colour filters
- Saves to `DCIM/BrinaCam`

Requires Android 10 or newer.

## Building

```
./gradlew assembleRelease
```

The app lives in `app/`. Every push builds a signed APK in GitHub Actions,
smoke-tests it on an emulator, and attaches the APK, screenshots and logs to a
pre-release.
