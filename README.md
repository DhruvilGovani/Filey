# Glimpse (Filey)

A lightweight, glassmorphic mobile file manager built as a single-file web app with native Android bridge support[span_0](start_span)[span_0](end_span)[span_1](start_span)[span_1](end_span).

## Features
- **Storage Dashboard:** Visual storage usage breakdown with an interactive donut chart and category shortcuts (Images, Videos, APKs, Archives, Downloads, Documents, Audio)[span_2](start_span)[span_2](end_span).
- **Full File Operations:** Browse folders, multi-select items, copy, move, rename, compress (`.zip`, `.7z`, `.rar`), inspect/extract archives, and restore deleted files from a timed Recycle Bin[span_3](start_span)[span_3](end_span).
- **Power Tools:** Built-in wireless FTP server toggle with QR code generation, instant global search, large-file finder (>50 MB), and recent activity view[span_4](start_span)[span_4](end_span).
- **Customizable UI:** Auto/Light/Dark themes, adjustable glass blur intensity, custom accent colors, and hidden-file toggling[span_5](start_span)[span_5](end_span).

## Installation & Usage

### Web Preview
Open `index.html` in any mobile browser or visit the live GitHub Pages deployment (`https://dhruvilgovani.github.io/Filey/`)[span_6](start_span)[span_6](end_span)[span_7](start_span)[span_7](end_span). When run without the native Android wrapper, Glimpse loads an interactive demo file tree[span_8](start_span)[span_8](end_span).

### Android APK
1. Download the latest `app-debug.apk` from the **Releases** tab.
2. Open the file on your Android device and allow **Install unknown apps** when prompted.

## Native Android Bridge (`window.GlimpseNative`)
To connect the UI to real device storage (`MANAGE_EXTERNAL_STORAGE`), wrap `index.html` in an Android WebView that exposes `window.GlimpseNative` with the following methods[span_9](start_span)[span_9](end_span):
- `list(pathArray)` — Returns folder entries (`{ n, dir, s, d }`)[span_10](start_span)[span_10](end_span).
- `info()` — Returns `{ used, total, breakdown }` storage metrics[span_11](start_span)[span_11](end_span).
- `open(pathArray, filename)` — Opens a file with the system intent chooser[span_12](start_span)[span_12](end_span).
- `trash(pathArray, names)` / `rename(pathArray, oldName, newName)`[span_13](start_span)[span_13](end_span)
- `move(srcPath, names, destPath)` / `copy(srcPath, names, destPath)`[span_14](start_span)[span_14](end_span)
- `mkdir(pathArray, name)` / `createFile(pathArray, name)`[span_15](start_span)[span_15](end_span)
- `ftp(action, config)` — Starts or stops the local FTP server (`port`, `user`, `pass`)[span_16](start_span)[span_16](end_span).

## License
Open-source under the **MIT License**.

