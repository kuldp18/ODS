# ODS — Open Document Scanner

An offline document scanner for Android 8.0 and later. Scan or import pages,
adjust crops and filters, and save or share documents. No accounts, ads,
watermarks, or internet permission.

## Features

- Manual camera capture and optional stable-frame auto-capture with live edges.
- Image import through the file picker or Android share menu.
- Automatic corner proposals with DocAligner and an OpenCV fallback.
- Eight crop controls: four corners and four draggable edge midpoints.
- Magnifier, zoom/pan mode, fine nudges, rotate, straighten, and undo/redo.
- Original, Auto, B&W, Gray, Color, and model-assisted Low light filters.
- Contrast, brightness, B&W threshold, and applying adjustments to all pages.
- Document thumbnails, search, sorting, batch selection, merge, and deletion undo.
- Page selection, deletion, and accessible up/down ordering.
- PDF, JPEG, PNG, and ZIP export; Auto, A4, and Letter PDF sizes.
- Native Android sharing and system file/folder saving.
- System, light, and dark themes.

Original images are retained separately from crop and filter settings. Documents
stay in app-private storage and are removed when the app is uninstalled.

## Offline models

[DocAligner](https://github.com/DocsaidLab/DocAligner) LCNet050 proposes four
page corners. Predictions that fail confidence or geometry checks fall back to
OpenCV detection or manual adjustment.

[Zero-DCE](https://github.com/raspberrypi/AI_enhance) supplies an approximately
100 KB model for the optional Low light filter. Inference estimates bounded
illumination gains at reduced resolution, which are interpolated onto the page
without replacing its detail. The Auto filter uses local contrast enhancement.

Models are packaged into the APK at build time. They require no runtime download,
Google Play services, or paid API. Original files are never overwritten. OCR is
not included.

## Build

Requirements: JDK 17, Gradle 8.13, Android SDK Platform 36, Build-Tools 35.0.0,
and Python 3.11+ for model preparation. Set `ANDROID_HOME` or `sdk.dir` in
`local.properties`. A Gradle wrapper is not yet included.

```sh
python -m pip install onnxruntime==1.20.1 ai-edge-litert==1.4.0
python tools/prepare_models.py
gradle :app:testDebugUnitTest :app:assembleDebug
```

The model preparation script downloads upstream weights and licenses, checks
inference tensor contracts, and records SHA-256 hashes in the bundled
`models/provenance.json`. Upstream model revisions are not yet locked across
builds. Local model assets are ignored by Git.

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### GitHub Actions

**Build Android APK** prepares and smoke-tests the models, runs unit tests, and
uploads the debug APK. It runs on pushes and pull requests to `main` or `master`,
and can also be started manually from the Actions tab.

## Development status

The current vision and UI upgrade needs Android build and device validation.
Detection accuracy, camera coordinate alignment, and memory use on long documents
have not been measured. PDFs contain page images, without a searchable text layer.

Backup/restore, automatic original-file cleanup, and complete recovery of captures
interrupted before their initial database write remain unfinished. Library deletion
retains originals. See [STATUS.md](STATUS.md) for remaining validation work.

## Technology

Kotlin, Compose, Material 3, CameraX, OpenCV, ONNX Runtime, LiteRT, Room, and DataStore.

## License

[Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for dependency and model notices.
