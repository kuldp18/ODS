# Development status

## Completed source stages / intended commit boundaries

1. `feat: scaffold offline Android scanner and reversible processing pipeline`
   Build configuration, manifest, Room models, import validation, EXIF-aware
   decode, geometry and OpenCV rendering, CameraX capture, export engine.
2. `feat: add library, crop, enhancement and native export screens`
   Compose application, selection/search, document tools, crop handles with
   history, enhancement controls, native sharing and directory saving.
3. `fix: harden crop persistence, cancellation and Unicode filenames`
   Saved document ID, DataStore appearance, cancellation checks, export memory
   guard, production geometry/naming checks, documentation and notices.

Git could not create its index lock due to sandbox permissions. These are intended
boundaries, not existing commits. Do not claim a clean committed history.

## Validation actually performed

- 11 production geometry/naming assertions compiled and passed using the existing
  Kotlin compiler; outputs were written under ignored `project/build/checks`.
- Checked repository identity and boundary: only `project` is a repository.
- Reviewed manifest: no network/storage permissions; only camera requested.
- Android build, Gradle JUnit/lint, device tests and native ABI checks NOT run.
  User explicitly requested no environment setup because C: space is limited.

## Next milestone

- Compile/lint Android sources and resolve any integration issues before use.
- Add official Gradle wrapper and checksum, dependency verification/locking and CI
  when network/toolchain setup is authorized. Verify target SDK/distribution rules.
- Detector: reduced-resolution quadrilateral scoring, confidence, camera mapping,
  stability/blur/glare checks and one-shot re-arming before enabling auto-capture.
- Crop: edge dragging, accessible nudge controls, pan/zoom/magnifier, straighten,
  auto-detect and composed transforms. Current crop only provides corner dragging.
- Enhancement: illumination normalization, brightness and threshold controls,
  hold-to-compare and thumbnail cache. Auto is currently modest global contrast.
- Add page removal, full bidirectional reorder sheet and page-level export selection.
- Deletion undo and explicit orphan/original cleanup. Current library deletion
  retains image files; there is no space reclamation UI yet.
- Capture journaling/reconciliation for death between image write and Room insert.
  Committed pages survive restarts; the in-flight capture recovery gate is unmet.
- Import failures keep earlier successfully imported pages; expose partial progress.
- Restore screen/page selection across process death and serialize incoming intents
  rather than dropping them while an operation is busy.
- Retention-based cache cleanup, export progress, output sizing (Auto/A4/Letter),
  image ZIP, save-single-file picker, disk estimates and retries.
- Profile PdfDocument memory and 100-page sessions before raising the export cap.
- Optional lazy OCR, bundled English, language import, TXT and local text search.
- Versioned backup/restore with zip-slip, size and count protections.
- Full accessibility/TalkBack/200% font/tablet/insets pass; localize UI strings.
- Device corpus: all EXIF rotations, identity/perspective crop, shadows, handwriting,
  low memory, cancellation and low storage; validate actual recipient-app grants.
- Release R8, ABI splits, 16 KB native alignment checks, baseline/startup profiles,
  dependency/model notices and signed APK process. No release performance claims.
