<div align="center">
  <img src="icons/glimpse-192.png" width="96" alt="Glimpse icon">
  <h1>Glimpse</h1>
  <p>A liquid-glass file manager for Android, built as a single-file web UI with an optional native shell.</p>
</div>

## What it is
- **`index.html`** is the whole app UI. It runs in any mobile browser on demo data. Open it directly or host it on GitHub Pages.
- **`android/`** is a small Kotlin WebView app. It loads the same `index.html` and gives it real storage access through `window.GlimpseNative`.

## Features
- Liquid-glass navigation pill and buttons over a rich gradient wallpaper
- Home: storage card, eight category shortcuts, Recents carousel
- Storage analytics with an interactive donut chart
- Browse: breadcrumb navigation, long-press multi-select, copy, move, rename, share, delete to Recycle Bin
- New folder and new file (+ button in Browse)
- Real-time search across files, folders and categories
- Recycle Bin, archive inspect, extract and compress (simulated in demo mode), FTP server toggle with QR code
- Settings: theme, blur, accent colour, hidden files, sort order, bin auto-empty

## Run the web version
Open `index.html` in a browser, or enable **Settings > Pages > Deploy from branch > `main` / root** and visit `https://<your-username>.github.io/<repo>/`.

Without the native shell the app shows demo data and the "Open with" sheet is a lookalike.

## Build the Android APK
**With GitHub Actions (no setup):**
1. Push this repo to GitHub.
2. Open the **Actions** tab, choose **Build APK**, then **Run workflow**.
3. Download `Glimpse-debug-apk` from the finished run, unzip it and install the APK.
4. On first launch, grant **All files access** when Android asks.

**With Android Studio:** copy `index.html` to `android/app/src/main/assets/index.html`, open the `android/` folder and run the `app` configuration.

## Native bridge
The WebView exposes `window.GlimpseNative`. All paths are arrays of folder names under the storage root, for example `["DCIM","Camera"]`.

| Method | Does |
|---|---|
| `list(path)` | Returns `[{n, dir, s, d}]` for a folder |
| `info()` | Returns `{total, used, breakdown:[{name, bytes}]}` |
| `open(path, name)` | Opens the file in Android's chooser |
| `trash(path, names)` | Moves items to `.glimpse_trash` |
| `rename(path, old, new)` | Renames an item |
| `move / copy(src, names, dest)` | Moves or copies items |
| `mkdir / createFile(path, name)` | Creates a folder or an empty file |
| `ftp("start" or "stop", {port, user, pass})` | Starts or stops the FTP server and returns `{ip}` |

If `window.GlimpseNative` is missing, the page falls back to demo data.

## Known gaps
- Restoring and purging the Recycle Bin, real search, real Recents, and real archive extract and compress still need native hooks.
- The FTP server stops when the app is in the background. A foreground service would fix that.
- Android 11+ blocks `Android/data` and `Android/obb` even with all-files access.
- Google Play only allows `MANAGE_EXTERNAL_STORAGE` for apps that qualify as file managers. Sideloading is unaffected.
- The debug APK is unsigned for release. Add a signing config before publishing.

## Structure
```
index.html                  web UI (single file)
android/                    Kotlin WebView shell and native bridge
icons/                      app icon, 512 / 192 / 108 px
.github/workflows/          APK build workflow
```
