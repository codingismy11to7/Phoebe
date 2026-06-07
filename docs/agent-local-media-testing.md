# Agent checklist: local media sources and playback

This document is for **AI agents and humans** validating Phoebe’s **pluggable media sources**, **local folder** scanning, and **error handling** when files go missing. It complements the product UI brief in `AGENTS.md`.

## Prerequisites

- Repo root: run **`./scripts/fetch-test-audio.sh`** once (requires **`curl`**; **`ffmpeg`** recommended so the script can derive **WAV / FLAC / M4A** from the Wikimedia Ogg sample and embed consistent tags). Audio lands in
  `composeApp/src/commonTest/resources/test-audio/` and is mirrored into `composeApp/src/androidDeviceTest/assets/test-audio/` for Android instrumented tests.
- **Desktop** (recommended first): run the desktop Compose app from the project’s usual entry (e.g. Gradle `run` task for desktop, per your environment).

## Test fixtures (licenses)

| File | Source | License |
|------|--------|---------|
| `wikimedia-example.mp3` | [Commons:Example.ogg](https://commons.wikimedia.org/wiki/File:Example.ogg) (MP3 transcode) | **CC BY-SA 3.0** (and GFDL per file page; attribute when redistributing). |
| `wikimedia-example.ogg` | Same file, original Ogg | Same as above. |
| `wikimedia-example.wav`, `wikimedia-example.flac`, `wikimedia-example.m4a` | Same audio as `wikimedia-example.ogg`, produced locally by **`ffmpeg`** (PCM, FLAC, AAC). | Same license as the Ogg source above. |
| `mdn-t-rex-roar-cc0.mp3` | [MDN `t-rex-roar.mp3`](https://interactive-examples.mdn.mozilla.net/media/cc0-audio/t-rex-roar.mp3); if MDN is unreachable, the script derives a short MP3 fallback from `wikimedia-example.ogg` | **CC0 1.0** for the MDN source; the fallback uses the Wikimedia Commons license above. |

Do **not** commit large copyrighted commercial tracks. The script only curls the two upstream URLs above; other formats are derived from the Commons Ogg.

## Validation steps (desktop)

1. **Start the app** and ensure the wide layout (sidebar visible) is shown.
2. **Expand the profile row** at the bottom of the sidebar (avatar + name + chevron).
3. Under **Media sources**, choose **Add local folder** and select the directory  
   `composeApp/src/commonTest/resources/test-audio` (or the folder where the script wrote files).
4. Tap **Rescan** if the library does not update immediately.
5. Open **Your Library** (or Home) and confirm **new tracks** appear from the local folder (titles and artists should reflect **embedded tags** on desktop and Android when present; otherwise filenames and folder names are used).
6. **Play** a local track and confirm audio plays.
7. **Missing file UX**: quit the app, **delete or rename** one of the downloaded fixture files on disk, restart, try to play that track — you should see a **user-visible message** (via app messaging) rather than a silent failure, and a rescan should **omit** missing paths on refresh.

## Plex profile row

1. Sign in to Plex (pin flow) if testing that path.
2. Collapsed profile should show **`PlexSession.userName`** (or **Guest** when logged out).
3. Expanded section should list **server** and **library** when present.
4. **Sign out of Plex** should clear the session, return to the sign-in experience, and drop Plex content from the catalog. If no local folders remain enabled, the library should be **empty** (no bundled demo artists/playlists).

## Android / iOS

- **Android**: use **Add local folder** (SAF tree); confirm persistable URI permission survives an app restart where applicable.
- **iOS**: folder picking may be limited on some builds; if **Add local folder** does nothing, note that in the test report and still validate **desktop** local playback. **Embedded tags** for local files are read fully on **desktop** (via jaudiotagger) and **Android** (`MediaMetadataRetriever`); on **iOS**, **duration** is read from `AVURLAsset` and title/artist/album fall back to filename / folder heuristics until richer AVFoundation metadata wiring is added.

## Automated tests (Gradle)

From the repo root:

- **`./gradlew :composeApp:desktopTest`** — JVM/desktop tests: in-memory SQLDelight, `MediaSourcesRepository`, `CatalogRepository.refreshAggregated` (no Plex session), `PlexClient` with Ktor `MockEngine`, `LocalFolderCatalogBuilder` against a temp folder, and shared `commonTest` cases (e.g. `CatalogMerge`, player state, Plex JSON).
- **`./gradlew :composeApp:connectedAndroidDeviceTest`** — Android **instrumented** tests on a device or emulator (Compose UI smoke for fake playback, `MediaSourcesRepository` against an app-context SQLite DB). Requires a connected device or running AVD.
- **`./gradlew :composeApp:wasmJsTest`** — Runs **common** tests plus wasm test sources in the JS/Wasm test runner (logic that compiles on Wasm; no SQLDelight web worker in these tests).
- **`npm run web:e2e`** — Playwright browser test against `/?e2e=localLibrary` (indexes wasm test-folder MP3s and verifies playback starts). Requires the wasm dev server (Playwright config starts it automatically).

Hermetic JVM/desktop and Android tests can redirect lightweight file prefs via **`System.setProperty("phoebe.storage.root", "/path/to/temp")`** so `PlatformStorage` does not touch the real user home (desktop) or default app files dir (Android).

## Definition of done

- [ ] Script ran without errors; with **ffmpeg**, expect **`.mp3`**, **`.ogg`**, **`.wav`**, **`.flac`**, **`.m4a`** from the Wikimedia line plus the MDN **`.mp3`**.
- [ ] Local folder appears under Media sources; tracks visible in library.
- [ ] Playback works for at least one fixture.
- [ ] Deliberately removed file yields a clear error or skip after rescan / play attempt.
- [ ] Plex sign-out from the profile row works when a session exists.
