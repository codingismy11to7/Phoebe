# Nix Dev Environment for Android Builds

## Goal

Build and run the Android app on a NixOS machine with no globally installed
JDK or Android SDK. Entering the repo directory should be enough: `direnv`
loads a flake dev shell that provides every tool the Android build needs.

## Problem

The machine is a blank slate — no `java`, no `ANDROID_HOME`, no `~/.gradle`,
no `local.properties`. Two NixOS-specific obstacles block a normal Android
build:

1. **The project pins JDK 22, which nixpkgs no longer ships.** JDK 22 reached
   end of life and was removed; nixpkgs carries 21, 23, 24, and 25. Gradle
   toolchains require an exact major-version match, so no other JDK satisfies
   `jvmToolchain(22)`. CI sidesteps this by installing JDK 21 and letting the
   foojay resolver download a Temurin 22 — a prebuilt binary that cannot run on
   NixOS, which has no dynamic loader at the path such binaries expect.
2. **AGP downloads `aapt2` from Maven.** That prebuilt binary fails on NixOS for
   the same reason. The SDK must come from `androidenv`, whose binaries are
   patched, and AGP must be told to use it.

## Decisions

| Decision | Choice | Rationale |
| --- | --- | --- |
| JDK 22 problem | Move the project to JDK 21 | 21 is LTS and already what CI installs, so CI stops downloading a second JDK. Pinning an old nixpkgs for 22 was rejected as building on an EOL JDK. |
| SDK source | nixpkgs `androidenv.composeAndroidPackages` | No extra flake inputs; binaries patched for NixOS; nixpkgs already carries platform 36, build-tools 36.1.0, and an API 36 system image. |
| Shell scope | Android build path plus emulator | Enough to build, install, and run without a physical device. |
| Systems | `x86_64-linux` only | Nix is not used on a Mac here. Darwin can be added later in a few lines. |
| System image | `36 / google_apis / x86_64` | The app uses Google Maps and Cast, which need Play services. `google_apis` supplies them while keeping `adb root` available, unlike `google_apis_playstore`. |

## Design

### `flake.nix`

A single `nixpkgs` input, `x86_64-linux` only, with `allowUnfree` and
`android_sdk.accept_license` enabled. It exposes one `devShells.default`.

The Android SDK is composed with:

- `platformVersions = [ "36" ]` — matches `compileSdk = 36`
- `buildToolsVersions = [ "36.1.0" ]`
- `systemImageTypes = [ "google_apis" ]`, `abiVersions = [ "x86_64" ]`
- emulator, platform-tools, and cmdline-tools included

### Dev shell

Provides and exports:

- `jdk21` as `JAVA_HOME`, also on `PATH`
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` pointing at the composed SDK
- `GRADLE_OPTS` carrying `android.aapt2FromMavenOverride`, aimed at the
  Nix-patched `aapt2` in build-tools — without this, AGP fetches an unrunnable
  `aapt2` from Maven
- `phoebe-avd`, which creates the API 36 AVD if it does not already exist
- `phoebe-emulator`, which boots that AVD

Gradle itself is **not** provided by Nix. The repo's wrapper (Gradle 9.4.1) is
pure JVM code and runs unpatched. AVDs live in the standard `~/.android/avd`
so Android Studio can find them later.

### `.envrc`

Contains `use flake`. `.direnv/` is added to `.gitignore`. nix-direnv is
already active on this machine, so shell entry is cached after the first load.

### JDK 22 to 21 migration

Five source sites plus one Dockerfile:

- `build-logic/convention/src/main/kotlin/phoebe/ConventionHelpers.kt:71`
- `build-logic/convention/src/main/kotlin/phoebe.backend.gradle.kts:11`
- `build-logic/convention/src/main/kotlin/phoebe.backend.library.gradle.kts:10`
- `composeApp/build.gradle.kts:52` (`desktopJavaLanguageVersion`)
- `composeApp/build.gradle.kts:149` (`jvmToolchain`)
- `Dockerfile.vercel` — `temurin:22-jdk` and `temurin:22-jre` become 21

CI needs no change: `JAVA_VERSION` is already 21 and every workflow installs
Temurin 21. The bump removes the foojay JDK download from CI as a side effect.

The repo contains only two Java files, both desktop-only
(`DesktopSpaceKeyHandler.java`, `MacMediaSession.java`), and neither uses
Java 22 language or API features, so this is a safe bytecode downgrade.

## Out of Scope

Node and the web target, ffmpeg/gstreamer desktop-audio dependencies,
Playwright, iOS tooling, Android Studio, and `local.properties` — the
environment variables replace the latter. The flake is structured so this
tooling can be added to the same shell later.

## Risks

- AGP 9.2.1's default `buildToolsVersion` may not be 36.1.0. If it differs,
  either install the version AGP wants or pin `buildToolsVersion` explicitly.
- `android.aapt2FromMavenOverride` may have been renamed or dropped in AGP 9.
  If so, find the current override mechanism.
- KMP's iOS targets may misbehave during Gradle configuration on Linux.
- Emulator GPU mode is unverified. Try `-gpu host` first, since `/dev/dri`
  is present, and fall back to swiftshader.

## Verification

1. `java -version` inside the shell reports 21.
2. `./gradlew :androidApp:assembleDebug` succeeds and produces an APK.
3. `phoebe-avd` then `phoebe-emulator` boots an emulator.
4. `adb install` places the APK on the emulator and the app launches.
