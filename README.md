# ODS — Open Document Scanner

An offline document scanner for Android 8.0 and later. Scan or import pages,
adjust crops and filters, and save or share documents. No accounts, ads,
watermarks, or internet permission.

## Features

- Manual camera capture with flash and multiple pages per document.
- Image import through the file picker or Android share menu.
- Four-corner perspective crop with undo, redo, and full-image reset.
- Original, Auto, B&W, Gray, and Color filters with contrast adjustment.
- Local document library with search, rename, and batch selection.
- Page rotation and ordering.
- PDF, JPEG, PNG, and ZIP export, including merged PDFs.
- Native Android sharing and saving through the system folder picker.
- System, light, and dark themes.

Original images are retained separately from crop and filter settings. Documents
stay in app-private storage and are removed when the app is uninstalled.

## Build

Requirements:

- JDK 17
- Gradle 8.13
- Android SDK Platform 36
- Android SDK Build-Tools 35.0.0

Set `ANDROID_HOME` to the SDK directory, or set `sdk.dir` in `local.properties`.
The repository does not currently include a Gradle wrapper.

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### GitHub Actions

The **Build Android APK** workflow runs on pushes and pull requests to `main` or
`master`, and can also be started manually from the Actions tab. It runs unit
tests and uploads the debug APK as a downloadable artifact.

## Development status

ODS is under development. Automatic document detection and capture, advanced crop
tools, OCR, and backup/restore are not implemented yet.

Exports are currently limited to 50 pages. PDFs contain images without a
searchable text layer; ZIP export packages PDFs. Library deletion currently
retains original image files, and temporary export cleanup is not yet automated.

## Technology

Kotlin, Jetpack Compose, Material 3, CameraX, OpenCV, Room, and DataStore.
Image processing and storage run locally; the app has no backend.

## License

[Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for dependency notices.
