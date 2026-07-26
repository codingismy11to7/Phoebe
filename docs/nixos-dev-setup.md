# NixOS development environment for Android builds

This document explains how the Android app builds and runs on a NixOS
machine with **no globally installed JDK and no globally installed Android
SDK**. Everything comes from a Nix flake dev shell (`flake.nix` at the repo
root) plus `direnv`. If you've forgotten why any of this exists, read the
"why" callouts below before changing anything.

Design record: `docs/decisions/2026-07-25-nix-android-dev-env-design.md`

## What `direnv allow` sets up

`.envrc` at the repo root guards `use flake` behind `has nix`, so contributors
without Nix (this repo also has macOS/Windows contributors) get a no-op
instead of a failure on every `cd`. On a machine with Nix, running `direnv
allow` once lets direnv load `flake.nix`'s `devShells.x86_64-linux.default`
automatically every time you `cd` into the repo — no manual `nix develop`
needed. direnv re-prompts for `direnv allow` only when `.envrc` itself
changes; this machine has `nix-direnv` active, which also reloads the shell
automatically when `flake.nix`/`flake.lock` change (no re-allow needed for
those), and after the first load the shell is cached and reappears almost
instantly on later `cd`s.

If you'd rather not use direnv, `nix develop --command <cmd>` (or
`nix develop` for an interactive shell) gives you the identical environment
on demand.

The shell exports:

- `JAVA_HOME` — JDK 22, from the pinned `nixpkgs-jdk22` input (see "JDK version" below).
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` — both point at the same
  Nix-composed, NixOS-patched Android SDK (`androidenv.composeAndroidPackages`
  output), ending in `/libexec/android-sdk`.
- `GRADLE_OPTS` — carries
  `-Dorg.gradle.project.android.aapt2FromMavenOverride=<sdk>/build-tools/36.0.0/aapt2`.
  See "Why the aapt2 override exists" below.
- `PATH` — the SDK package's own `bin/` directory, which provides `adb`,
  `avdmanager`, `sdkmanager`, `emulator`, `apkanalyzer`, `d8`, and `r8`.
  **`aapt2` is deliberately not among them** — it lives at
  `$ANDROID_HOME/build-tools/36.0.0/aapt2` and must be referenced by that
  full path (or a glob like `$ANDROID_HOME/build-tools/*/aapt2`), not called
  bare.
- Two helper commands, `phoebe-avd` and `phoebe-emulator` (see below).

Entering the shell prints a banner: `Phoebe dev shell: JDK 22, Android
SDK 36`.

`local.properties` is never created or read in this workflow. The Android
Gradle Plugin normally reads the SDK path from `local.properties`, but here
`ANDROID_HOME` / `ANDROID_SDK_ROOT` from the dev shell environment serve the
same purpose, so there is nothing to put in that file. Do not create one.

## Why the aapt2 override exists

By default AGP downloads its own `aapt2` binary from Maven at build time.
That prebuilt binary is a normal dynamically-linked ELF executable built for
a standard Linux distribution's dynamic loader path (`/lib64/ld-linux-*.so`);
NixOS doesn't have one there, so the Maven binary cannot execute.

`nixpkgs`'s `androidenv` ships an `aapt2` inside its `build-tools` directory
that has been **patched** (via Nix's `autoPatchelf` machinery — see
`pkgs/development/mobile/androidenv/build-tools.nix:18,41` in nixpkgs) to use
the Nix store's dynamic loader instead, so it actually runs on NixOS. The
`GRADLE_OPTS` line tells AGP, via the `android.aapt2FromMavenOverride`
Gradle property, to use that patched binary instead of fetching its own.

## Why build-tools is pinned to 36.0.0, not the newest 36.x

`compileSdk = 36` in `androidApp/build.gradle.kts`. AGP 9.2.1's default
behavior, when no `buildToolsVersion` is set explicitly in Gradle (and this
project doesn't set one), is to demand build-tools revision
**`"<compileSdk>.0.0"`** specifically — i.e. `36.0.0` — not just any `36.x`
release. `nixpkgs` also carries newer point releases (`36.1.0` was tried
first here), but provisioning only `36.1.0` meant AGP tried to auto-install
`36.0.0` at build time into the SDK's Nix store path, which is read-only, and
the build failed with "The SDK directory is not writable." The fix is to
provision exactly the revision AGP will ask for: `flake.nix` pins
`buildToolsVersion = "36.0.0"`, and that one value feeds both
`buildToolsVersions` (what Nix provisions) and the `GRADLE_OPTS` aapt2 path,
so the two can't drift apart.

## JDK version

The project's Gradle toolchain is pinned to **JDK 22**, and the dev shell
supplies it from a second flake input pinned to `nixos-24.05`:

```nix
nixpkgs-jdk22.url = "github:NixOS/nixpkgs/nixos-24.05";
```

**Why an old nixpkgs instead of a current JDK.** JDK 22 reached end of life
and was removed from current nixpkgs, which ships 21, 23, 24, and 25. Gradle
toolchains require an exact major-version match, so none of those satisfy
`jvmToolchain(22)`. The obvious move — lower the project to JDK 21 — was
tried and **reverted**, because the pin turns out to be load-bearing:

```
Dependency resolution is looking for a library compatible with JVM runtime
version 21, but 'io.github.erkko68.filament-ffm:filament-ffm:0.1.3-beta02'
is only compatible with JVM runtime version 22 or newer.
```

`feature:playback` depends on `filament-compose`, which pulls in
`filament-ffm`. That artifact uses the Foreign Function & Memory API,
finalized in JDK 22, and publishes Gradle metadata demanding JVM 22+. On
JDK 21 the desktop target fails dependency resolution before compiling a
single file — all four desktop CI jobs go red while Android, backend, and
wasm stay green (Android is unaffected because it targets `JVM_17`).

So the JDK 22 pin is a real constraint, not an accident. Do not "modernize"
it without first checking whether the Filament dependency still requires 22.

The old-nixpkgs closure costs roughly 470 MB and is fully cached — it
substitutes, nothing builds from source. This is the same EOL JDK that CI
already uses via foojay auto-provisioning; the flake just makes it explicit
and runnable on NixOS, where the foojay-downloaded Temurin cannot execute.

## Building the debug APK

```bash
nix develop --command ./gradlew :androidApp:assembleDebug
```

(Or just `./gradlew :androidApp:assembleDebug` directly if you're already in
a direnv-loaded shell.) The first run also downloads the Gradle 9.4.1
distribution and dependencies and can take several minutes; later runs are
much faster.

Output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`, package
`com.phoebe.app.debug`.

To sanity-check the APK without a device:

```bash
nix develop --command bash -c 'aapt2=$(echo "$ANDROID_HOME"/build-tools/*/aapt2); "$aapt2" dump badging androidApp/build/outputs/apk/debug/androidApp-debug.apk | head -5'
```

(Globbing `build-tools/*/aapt2` avoids hardcoding the build-tools version in
your own commands.)

## Creating and booting the emulator

**Prerequisite:** the emulator needs access to `/dev/kvm` for CPU
acceleration — typically membership in the `kvm` group. Without it, the
emulator either fails to start or falls back to software CPU emulation slow
enough to be unusable.

The emulator runs **Android Automotive OS**, which is the app's real target
in the car, not a phone image.

`phoebe-avd` creates an AVD named `phoebe-aaos-api33` (API 33,
`android-automotive`, `x86_64`, on the `automotive_1080p_landscape` device
profile) if it doesn't already exist; it's safe to run repeatedly:

```bash
nix develop --command phoebe-avd
```

`phoebe-emulator` boots that AVD. Any extra arguments are passed straight
through to the underlying `emulator` binary:

```bash
nix develop --command phoebe-emulator
```

### Why the emulator is API 33 while `compileSdk` is 36

nixpkgs carries no `android-automotive` system image at API 36 — the newest
are 33 and `35x`, and only 33 is verified here. So `flake.nix` provisions
**two** platforms: 36 to compile against, 33 for the emulator to run. `minSdk`
is 26, so the app runs fine on the older platform. Expect the emulator to be
a slightly older Android than a current production vehicle.

Under software rendering the AAOS UI is noticeably heavier than a phone
image; first boot takes minutes and the emulator logs `Your GPU drivers may
have a bug` before falling back to SwiftShader/lavapipe. That's expected.

### Simulating a moving car

Distraction-optimization restrictions only engage when the car thinks it's
driving. `cmd car_service enable-uxr` is gated behind a platform signature
and won't work from a shell even as root, but injecting vehicle properties
does:

```bash
# start driving
adb shell cmd car_service inject-vhal-event 0x11400400 8    # gear -> DRIVE
adb shell cmd car_service inject-vhal-event 0x11600207 30   # speed -> 30

# park again
adb shell cmd car_service inject-vhal-event 0x11400400 4    # gear -> PARK
adb shell cmd car_service inject-vhal-event 0x11600207 0    # speed -> 0
```

Verify via the transition log, **not** `get-property-value` — that reads the
VHAL's backing value and does not reflect injected events, which makes it
look like the injection silently failed:

```bash
adb shell dumpsys car_service | grep "DO changed" | tail -3
```

While "driving", Phoebe's own UI is blocked with "You can't use this feature
while driving", because it declares no distraction-optimized activities
(`cmd car_service get-do-activities com.phoebe.app.debug` reports none). That
is correct for a media app: the rich UI is a parked experience, and the
system-rendered browse tree is the driving surface.

### Why both scripts pin `ANDROID_AVD_HOME`

`avdmanager` (used by `phoebe-avd`) honors `$XDG_CONFIG_HOME` when it's set
and will otherwise write new AVDs under `~/.config/.android/avd`. The
`emulator` binary's own default search order (`$ANDROID_AVD_HOME`,
`$ANDROID_SDK_HOME/avd`, `$HOME/.android/avd`) never looks there. On a
machine with `$XDG_CONFIG_HOME` set (common on NixOS/Linux desktops), that
mismatch means `avdmanager` and `emulator` disagree about where the AVD
lives, and `phoebe-emulator` fails immediately with:

```
ERROR | Unknown AVD name [phoebe-aaos-api33], use -list-avds to see valid list.
```

Both `phoebe-avd` and `phoebe-emulator` export `ANDROID_AVD_HOME="$HOME/.android/avd"`
to force them to agree, regardless of the host's XDG settings. That alone
isn't quite enough, though: `avdmanager` **silently ignores**
`ANDROID_AVD_HOME` and falls back to the XDG path if the target directory
doesn't already exist — so `phoebe-avd` also runs `mkdir -p
"$ANDROID_AVD_HOME"` before calling `avdmanager create avd`.

### GPU rendering

No `-gpu` flag is baked into `phoebe-emulator`; pass one at invocation time
if you need it, e.g. `nix develop --command phoebe-emulator -gpu host`. On
this machine the emulator auto-detected a problem with the host GPU drivers
and fell back to software rendering on its own, logging:

```
WARNING | Your GPU drivers may have a bug. Switching to software rendering.
```

...and selecting SwiftShader/llvmpipe (`vulkan_mode_selected:lavapipe
gles_mode_selected:swangle`). Boot was still fast (under 2 minutes) because
`/dev/kvm` CPU acceleration is independent of GPU rendering. If you hit a
GL/Vulkan error trying `-gpu host` yourself, `-gpu swiftshader_indirect` is
the usual fallback. This doc only covers the emulator running headless
(verified below); a windowed run is a separate thing to confirm on your own
display setup.

### Headless boot

```bash
nohup nix develop --command phoebe-emulator -no-window -no-audio -no-snapshot > /tmp/emulator.log 2>&1 &
nix develop --command adb wait-for-device
```

Then poll until boot completes:

```bash
nix develop --command adb shell getprop sys.boot_completed
```

Repeat until it prints `1` (took under 2 minutes on this machine, thanks to
KVM acceleration even with software-rendered graphics).

### Install and launch

```bash
nix develop --command bash -c 'adb wait-for-device && adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk && adb shell monkey -p com.phoebe.app.debug -c android.intent.category.LAUNCHER 1'
```

Expect `Success` from the install, then the app opens on the emulator.

### Shutting down

```bash
nix develop --command adb emu kill
```

## Miscellaneous SDK layout notes

- `cmdline-tools` inside the composed SDK is a **version directory**, not
  `latest` — check `ls "$ANDROID_HOME/cmdline-tools"` for the current version
  rather than assuming one; don't assume `cmdline-tools/latest` exists if
  you're reaching into the SDK directly instead of using the `bin/`
  wrappers.
- `build-tools` contains exactly the one pinned version (`36.0.0`).
- `platforms` contains both `android-33` and `android-36`.
- `system-images` contains `android-33` (`android-automotive`/`x86_64`) once
  the emulator packages have been fetched.

## Quick reference

| Task | Command |
| --- | --- |
| Load the dev shell | `direnv allow` (once), then just `cd` into the repo |
| Check the JDK | `nix develop --command java -version` |
| Check the SDK | `nix develop --command bash -c 'ls "$ANDROID_HOME"'` |
| Build debug APK | `nix develop --command ./gradlew :androidApp:assembleDebug` |
| Create the AVD | `nix develop --command phoebe-avd` |
| Boot the emulator | `nix develop --command phoebe-emulator` |
| Install + launch | see "Install and launch" above |
