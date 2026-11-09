# Development status

## Current upgrade

- Crop editor: eight controls, draggable edges, magnifier, explicit zoom/pan mode,
  named handle selection, pixel nudges, rotation, straighten, undo/redo, re-detection.
- Detection: bundled DocAligner LCNet050 corner model, bounded convex geometry,
  confidence rejection, OpenCV fallback, automatic crop on capture/import.
- Camera: throttled latest-frame analysis, preview boundary guidance, sharp/stable
  capture gate, single-shot rearming, batch count, manual shutter and flash.
- Enhancement: local contrast Auto filter, optional neural low-light gains,
  brightness/contrast/threshold, preview cache, apply-to-all, original comparison.
- Organization: thumbnails, name/recent sorting, document merge, deletion undo,
  page selection, deletion confirmation and up/down reorder sheet.
- Export: selected pages, separate/merged PDFs, PDF/image ZIP, page sizing and
  single-file or multi-file system saving. No fixed page-count limit.
- Persistence: non-destructive Room migrations 1→2→3; page metadata is saved before
  corner inference so canceling detection does not remove the saved capture.

## Validation completed locally

- Eight JUnit tests passed using the installed Kotlin compiler and JUnit library.
  Includes 5,000 randomized control drags, edge movement, invalid geometry,
  filename handling and stable-capture rearming.
- Twenty-four standalone production geometry/capture/filename checks passed.
- Model provisioning Python syntax checked. No SDKs or dependencies installed.

## Required before claiming this upgrade works on devices

- Run the updated GitHub workflow: model acquisition and inference smoke tests,
  dependency resolution, Kotlin compilation, Room processing and APK assembly.
- Validate both neural pipelines on a representative scan corpus; tune confidence,
  blur and stability thresholds based on observations. No accuracy claims yet.
- Verify camera buffer strides, sensor/EXIF rotations, FIT_CENTER mapping, flash,
  lifecycle transitions and one-page rearming on several actual cameras.
- Verify crop hit targets and inverse coordinate transforms while zoomed/rotated,
  landscape layouts, TalkBack, and large font settings.
- Compare full-resolution processing with preview, including straighten bounds,
  signatures/stamps and low-light contrast. Measure latency and peak memory.
- Verify Room upgrade from the previously installed version without losing scans.
- Test PDF/JPEG/PNG/ZIP sharing with actual receiver apps, SAF cancellation/partial
  writes, low storage and 100-page sessions. PdfDocument can retain page resources.
- Verify every packaged native ABI on 4 KB and 16 KB systems; no device claim yet.

## Remaining functionality

- Backup/restore with a versioned manifest and bounded, traversal-safe extraction.
- Capture journaling for death between file write and initial metadata commit.
- Persistent deletion undo and explicit reclamation of unreferenced originals.
- Retention-based export cache cleanup and storage settings.
- Persist page selection and editor drafts across process death; queue incoming
  image intents when another operation is busy.
- Lock model hashes/revisions after the model smoke test succeeds. Current builds
  record source URLs, sizes and hashes but resolve upstream weights at build time.
- Full UI/device comparison against the supplied designs, release signing,
  baseline profiles and physical-device performance measurements.

OCR is excluded from the current scope. No text-recognition model is included.
