# ODS — Open Document Scanner

Native, offline Android document scanner. Kotlin, Compose/Material 3, CameraX,
Room, DataStore and the official OpenCV Android AAR. Android 8+ (API 26).
No accounts, internet permission, analytics, ads or paid features.

## Current implementation

- Local document library with name search, selection, rename and confirmed removal.
- Manual CameraX capture, flash, sequential captures and ordered image import.
- Incoming single/multiple image share intents.
- EXIF orientation normalization, convex four-corner crop with undo/redo and reset.
- Reversible Original/Auto/B&W/Gray/Color filters, contrast and apply-to-all.
- Page preview, page selection, rotate and move-up ordering.
- PDF/JPEG/PNG exports, separate or merged PDF, and ZIP containing PDFs.
- Native Sharesheet with content URIs, ClipData and temporary read grants.
- Save generated files to a system-picked directory. Final size before sharing.
- System/light/dark appearance persisted in DataStore; originals retained privately.

This is the first development milestone, **not a release-ready implementation of
the complete plan**. Read [STATUS.md](STATUS.md) for limitations and next work.

## Build later

Environment setup was explicitly deferred. No SDKs, Gradle distributions or Maven
dependencies were downloaded during implementation. A Gradle wrapper is not yet
included; do not mistake this for a reproducibly bootstrapped release build.

When ready, use JDK 17, Gradle 8.13, Android SDK Platform 36 and the build tools
required by AGP 8.13.2. Open this directory in Android Studio, configure its Gradle
installation, then run:

```powershell
gradle testDebugUnitTest lintDebug assembleDebug
```

Set `ANDROID_HOME` or an ignored `local.properties` with `sdk.dir`. Keep SDK,
Gradle user home and build caches on a drive with sufficient space. Generate and
check in the official Gradle wrapper once toolchain installation is authorized.
All dependency versions are pinned in the Gradle files. Dependency resolution and
the full Android build still need validation.

## Lightweight checks without Android setup

`tools/GeometryChecks.kt` runs against the actual production geometry and naming
code with an existing Kotlin compiler:

```powershell
kotlinc app/src/main/java/org/ods/scanner/Geometry.kt tools/GeometryChecks.kt -include-runtime -d geometry-checks.jar
java -jar geometry-checks.jar
```

Equivalent JUnit cases are in `app/src/test`. On 9 November 2026, the standalone
checks passed using an already installed Kotlin compiler. No Android compilation,
emulator testing or device performance measurement has been performed.

## Data and export behavior

App-private files hold originals; Room stores metadata and normalized crop/filter
recipes. Processing reads originals and never overwrites them. Import validates
content and commits via a temporary file. Native rendering is serialized and Mats
are released in `finally` blocks. Exports use frozen document/page values and
delete incomplete output on failure/cancellation; successful share files are
retained for delayed reads. No automatic export-cache expiry yet.

Exports currently allow at most 50 pages as a conservative pre-profiling limit.
PDF presets change resolution; JPEG presets also change encoder quality. PNG is
lossless after rendering. PDF uses each page's aspect ratio, with no text layer.
ZIP currently packages PDF only. Saving several files can partially succeed if a
provider fails midway; the library and originals remain intact.

## Version control

Only this `project` directory is the repository. The sibling `docs` directory is
outside it. Git identity is repository-local:
`kuldeepsolanki1854 <kuldeepsolanki1854@gmail.com>`.

The sandbox denied writes to `.git/index.lock` when the first checkpoint was
attempted. Consequently no commits could be created in this session. The intended
checkpoints are recorded in STATUS.md; commit from a writable host Git session.

## License

ODS source is licensed under Apache-2.0. See [LICENSE](LICENSE) and
[NOTICE](NOTICE). Do not distribute a release without the dependency notices,
native ABI/page-size verification, and release gates in STATUS.md.
