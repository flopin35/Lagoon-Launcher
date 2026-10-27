# Lagoon Launcher

A calm turquoise home screen for Android tablets, built with Kotlin and Jetpack Compose.

- Home screen with clock, greeting, pinned apps and a floating dock
- Swipe up for the app list (loaded live from installed apps, with search)
- Swipe down for notifications, long-press the home screen for settings
- Long-press an app to pin it, add it to the dock, hide it, open App info or uninstall
- Five drawn wallpapers, the device wallpaper, or your own photo
- Six accent colours, four icon shapes, colour or mono icons, adjustable icon size
- Motion: off, gentle or lively. All settings are saved on the device.

## Download site

`site/` is a static page that serves `site/download/LagoonLauncher-v1.0.apk`.
On Vercel, set **Root Directory** to `site` and **Framework Preset** to **Other**.

## Building

Requires the Android SDK (compileSdk 36) and JDK 17+.

```bash
./gradlew assembleRelease
```

Release signing reads `keystore.properties` and the `.jks` file in the project root. Both are kept out of git;
back them up, because future updates must be signed with the same key to install over this version.
