# NixOS development environment for Android builds

This document explains how the Android app builds and runs on a NixOS
machine with **no globally installed JDK and no globally installed Android
SDK**. Everything comes from a Nix flake dev shell (`flake.nix` at the repo
root) plus `direnv`. If you've forgotten why any of this exists, read the
"why" callouts below before changing anything.

Design record: `docs/superpowers/specs/2026-07-25-nix-android-dev-env-design.md`
Implementation plan: `docs/superpowers/plans/2026-07-25-nix-android-dev-env.md`

## What `direnv allow` sets up

`.envrc` at the repo root contains a single line, `use flake`. Running
`direnv allow` once (per repo, or after `.envrc`/`flake.nix` change) lets
direnv load `flake.nix`'s `devShells.x86_64-linux.default` automatically
every time you `cd` into the repo — no manual `nix develop` needed. This
machine has `nix-direnv` active, so after the first load the shell is cached
and reappears almost instantly on later `cd`s.

If you'd rather not use direnv, `nix develop --command <cmd>` (or
`nix develop` for an interactive shell) gives you the identical environment
on demand.

The shell exports:

- `JAVA_HOME` — `pkgs.jdk21` (JDK 21).
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

Entering the shell prints a banner: `Phoebe dev shell: JDK 21.0.12, Android
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
that has been **patched** (via Nix's `autoPatchelf` machinery) to use the
Nix store's dynamic loader instead, so it actually runs on NixOS. The
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

The project's Gradle toolchain is pinned to **JDK 21** (an LTS release
nixpkgs actually ships as `pkgs.jdk21`). It was previously pinned to JDK 22,
which reached end of life and was removed from nixpkgs; nixpkgs currently
carries 21, 23, 24, and 25, and Gradle toolchains require an exact
major-version match, so no other available JDK would have satisfied
`jvmToolchain(22)`.

The migration touched 9 Kotlin call sites across 4 files, plus one
Dockerfile:

- `build-logic/convention/src/main/kotlin/phoebe/ConventionHelpers.kt` (2 sites)
- `build-logic/convention/src/main/kotlin/phoebe.backend.gradle.kts` (2 sites)
- `build-logic/convention/src/main/kotlin/phoebe.backend.library.gradle.kts` (2 sites)
- `composeApp/build.gradle.kts` (3 sites: `desktopJavaLanguageVersion`,
  `jvmToolchain`, and desktop's `jvmTarget`)
- `Dockerfile.vercel` (`eclipse-temurin:22-*` → `21-*`)

**The Android target's bytecode level was deliberately left alone.** It
already compiles to `JvmTarget.JVM_17` (`composeApp/build.gradle.kts:161`),
and only the desktop and backend JVM targets moved to 21 — the toolchain
version (which JDK builds the project) is a separate axis from the bytecode
target (what `.class` file version Android gets), and only the former
needed to change for Android builds to work at all.

If you ever need to raise the JDK version again, check `nix search` (or
browse nixpkgs) first to confirm nixpkgs actually ships that major version as
a package — pinning an old nixpkgs revision just to keep an EOL JDK around is
not the intended path here.

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

`phoebe-avd` creates an AVD named `phoebe-api36` (API 36, `google_apis`,
`x86_64`) if it doesn't already exist; it's safe to run repeatedly:

```bash
nix develop --command phoebe-avd
```

`phoebe-emulator` boots that AVD. Any extra arguments are passed straight
through to the underlying `emulator` binary:

```bash
nix develop --command phoebe-emulator
```

### Why both scripts pin `ANDROID_AVD_HOME`

`avdmanager` (used by `phoebe-avd`) honors `$XDG_CONFIG_HOME` when it's set
and will otherwise write new AVDs under `~/.config/.android/avd`. The
`emulator` binary's own default search order (`$ANDROID_AVD_HOME`,
`$ANDROID_SDK_HOME/avd`, `$HOME/.android/avd`) never looks there. On a
machine with `$XDG_CONFIG_HOME` set (common on NixOS/Linux desktops), that
mismatch means `avdmanager` and `emulator` disagree about where the AVD
lives, and `phoebe-emulator` fails immediately with:

```
ERROR | Unknown AVD name [phoebe-api36], use -list-avds to see valid list.
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

### Headless boot (what was actually verified)

```bash
nohup nix develop --command phoebe-emulator -no-window -no-audio -no-snapshot > emulator.log 2>&1 &
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

- `cmdline-tools` inside the composed SDK is a **version directory**
  (currently `21.0` on this machine — `flake.nix`'s own comment says `19.0`,
  which was apparently never actually verified; trust `ls
  "$ANDROID_HOME/cmdline-tools"` over that comment), not `latest` — don't
  assume `cmdline-tools/latest` exists if you're reaching into the SDK
  directly instead of using the `bin/` wrappers.
- `build-tools` contains exactly the one pinned version (`36.0.0`).
- `platforms` contains `android-36`.
- `system-images` contains `android-36` (`google_apis`/`x86_64`) once the
  emulator packages have been fetched.

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
