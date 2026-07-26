# AAOS Emulator: First Increment Toward Android Automotive Support

## Goal

Boot an Android Automotive OS emulator from the Nix dev shell, install the
existing Phoebe debug APK on it unmodified, and record what AAOS does with
the app as it stands today.

This is increment 1 of a larger effort to make Phoebe usable in an Android
Automotive vehicle. It deliberately changes no application code.

## Why this project exists

No Plex or Navidrome client exists for Android Automotive. Phoebe already
supports both, which makes it the natural base to build one on.

## What already exists

The media-app groundwork is substantially done, which is why this effort is
smaller than it first appears:

- `PlaybackService` is a `MediaLibraryService` implementing the full browse
  contract: `onGetLibraryRoot`, `onGetItem`, `onGetChildren`, `onSearch`,
  `onGetSearchResult`.
- The manifest declares the `android.media.browse.MediaBrowserService` and
  media3 `MediaLibraryService` intent filters.
- `composeApp/src/androidMain/res/xml/automotive_app_desc.xml` exists and
  declares `<uses name="media" />`.
- The manifest sets the `com.google.android.gms.car.application` meta-data
  and an `androidx.car.app.TintableAttributionIcon`.

AAOS consumes the same browse tree as Android Auto, so the hard part — a
browse tree over Plex and Navidrome — is already built.

## Distribution: resolved, and it is not sideloading

The target vehicle is a 2026 Cadillac Lyriq-V (GM "Google built-in"). We
investigated sideloading directly and it is not viable:

- Developer options **can** be enabled, and USB debugging appears in them.
- There is **no** wireless-debugging option, and no ADB port is listening on
  the vehicle's network interface. The car is reachable by ARP but filters
  ICMP and exposes no ADB port.
- USB debugging over a cable did not produce a device. Car USB ports are
  host-mode, which is the wrong direction for ADB.
- USB-drive installation failed: a file manager with "All files access"
  granted could not see an APK copied to the drive. Every USB drive is
  claimed by the SurroundVision recorder app.

Google's documentation does permit "Allow unknown sources" for media apps
(the prohibition applies to Android for Cars App Library apps), so the
platform rule was not the obstacle — GM's implementation is.

**Distribution will therefore be Google Play Closed Testing.** Internal
Testing is reported to reject artifacts declaring
`android.hardware.type.automotive`; only Closed Testing accepts the
automotive form factor, and it requires review against the car app quality
guidelines. Media apps have the shortest such checklist, with no custom-UI
review, because Google renders the browse tree.

Two consequences for later increments, recorded here so they are not
rediscovered:

- The automotive form-factor declaration and a separate automotive artifact
  are **required**, not optional.
- Automotive media apps must ship both **x86_64 and ARM**.

Because the review loop is measured in days, **the emulator is the
development loop** and the vehicle is only the deployment target.

## Decisions

| Decision | Choice | Rationale |
| --- | --- | --- |
| Dev loop | AAOS emulator | Sideloading is unavailable and Play review takes days. The car cannot be the iteration target. |
| Emulator API level | 33 (`android-automotive`) | Verified to compose and build. nixpkgs has no automotive image at 36, and the newer `35x` is untested. `minSdk` is 26, so 33 runs the app fine. |
| Shell layout | Replace the phone emulator | AAOS is the actual target. Keeps the closure smaller than carrying both images. Accepted cost: the phone emulator is no longer available. |
| App changes | None | This increment observes current behavior. Declaring the automotive feature requires `required="true"`, which makes the artifact automotive-only and needs a build variant — that is increment 2. |

## Design

### `flake.nix`

The existing `androidComposition` changes so that:

- `platformVersions = [ "33" "36" ]` — 36 for `compileSdk`, 33 for the
  automotive system image. Verified: androidenv handles the absence of an
  automotive image at 36 without error.
- `systemImageTypes = [ "android-automotive" ]` replaces `google_apis`.
- `abiVersions`, `includeEmulator`, and `includeSystemImages` are unchanged.
- `buildToolsVersion` stays `36.0.0` — AGP 9.2.1 requires it.

`phoebe-avd` and `phoebe-emulator` keep their names but now target an
automotive AVD, since only one emulator exists after this change:

- AVD name becomes `phoebe-aaos-api33`.
- Device profile becomes `automotive_1080p_landscape`, which is tagged
  `android-automotive` and so matches the image.
- The `ANDROID_AVD_HOME` pin and its `mkdir -p` are retained unchanged.

### Orphaned AVD

The existing `phoebe-api36` AVD records
`image.sysdir.1=system-images/android-36/google_apis/x86_64/` as a path
relative to `ANDROID_SDK_ROOT`. Removing the phone image leaves that AVD
pointing at a path that no longer exists, so it must be deleted rather than
left to fail confusingly.

### Documentation

`docs/nixos-dev-setup.md` describes the phone emulator and its API 36 AVD
throughout. It is updated to describe the automotive emulator, and to record
that sideloading to the vehicle is not possible and why.

## Out of Scope

Application code of any kind: the automotive feature declaration, a build
variant or product flavor, browse-tree tuning for automotive, sign-in flows,
ARM ABI support, Play Console setup, and automotive screenshots. Each is a
later increment.

## Risks

- The AAOS emulator is heavier than the phone emulator and this machine has
  no working GPU acceleration — the emulator logs "Your GPU drivers may have
  a bug" and falls back to SwiftShader/lavapipe. Boot is slower than the
  phone emulator's ~25 seconds.
- `automotive_1080p_landscape` is confirmed present in `avdmanager list
  device`. (Resolved: AVD creation against the automotive image works.)

## Findings

All of the following were established on the emulator built by this flake.

**The app runs on AAOS unmodified**, and its layout adapts correctly to
automotive landscape — sidebar navigation, provider picker, transport bar.
No automotive-specific UI work was needed.

**Sign-in works without a browser.** Plex uses a PIN flow (`plex.tv/api/v2/pins`
then polling); the UI displays the code for approval on a phone and never
tries to launch a browser. Subsonic/Navidrome is a credentials form, typeable
while parked.

**Two app-side changes were required** to appear in the car's media source
list. They are not part of this increment and ship separately:

1. AAOS needs `androidx.car.app.launchable` on the `MediaBrowserService`. The
   Android Auto opt-in the app already had is a *different* declaration, so
   AAOS logged "No opt-in info found" and skipped the service as belonging to
   a "non media template app".
2. Browse artwork URIs must use the resource *name* form. The numeric-id form
   made AAOS call `getDrawable(0)`, throwing
   `Resources$NotFoundException: Resource ID #0x0` and killing the entire
   `com.android.car.media` process.

With those, the browse tree renders in the AAOS media template with real
library content, and browse, search and playback all work. **Per-item album
and artist artwork does not render** — that remains open.

**Drive-mode blocking behaves correctly.** The app declares no
distraction-optimized activities, so its own UI is blocked while driving and
the system-rendered browse tree serves the driver. That is the right shape
for a media app.

## Verification

1. `nix develop` provides an SDK containing `system-images/android-33`
   (`android-automotive`) and platforms for both 33 and 36. ✅
2. `phoebe-avd` creates `phoebe-aaos-api33` and is idempotent on re-run. ✅
3. The emulator boots to `sys.boot_completed=1`. ✅
4. `adb install` of the debug APK succeeds. ✅
5. Recorded observation of what AAOS does with the app — see Findings. ✅
