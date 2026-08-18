# Packaging Phoebe Desktop for NixOS

Date: 2026-08-18

## Context

This fork (`codingismy11to7/Phoebe`) diverged from upstream (`j-roskopf/Phoebe`) at
`3b8f1513` on 2026-07-15 to add Android Automotive OS support. That effort has been
dropped and moved to a separate project. Eighteen commits accumulated on the fork's
`main`; seventeen are AAOS work, CI version bumps, or a Nix dev shell for Android
builds that is no longer wanted.

The new goal is narrower: run the Phoebe desktop client on NixOS and install it from
the author's NixOS configuration at `/home/steven/dotfiles`, replacing Feishin with a
client that speaks Plex natively.

## Goals

- Reduce the fork's divergence from upstream to the minimum that serves this goal.
- Produce a Nix package for the Phoebe desktop (JVM) client.
- Install it from `dotfiles` alongside the other GUI packages.

## Non-goals

- Building the Android app on NixOS. The dev shell that did this is removed.
- Building the desktop app from source. Deferred; see "Future work".
- A NixOS or home-manager module. Phoebe is a GUI application, not a service, so it
  needs only a package.

## Part 1 — History cleanup

Move `main` onto `upstream/main`, keeping exactly one commit.

```
git tag archive/aaos c2034e7
git push origin archive/aaos
git checkout -B main upstream/main
git cherry-pick 134dae6
```

An interactive rebase (`git rebase -i --onto upstream/main 3b8f1513 main`) reaches the
same result and is equally valid. Reset-and-cherry-pick is preferred here only because
1 of 18 commits survives: it names the commit that is kept, rather than requiring 17
`pick` lines to be deleted correctly.

**Kept:** `134dae6` "Skip fork-incompatible deploy jobs in CI". It gates two deploy
jobs on `github.event.repository.fork == false`. Unrelated to AAOS, and it keeps this
fork's CI honest.

**Dropped:** eight AAOS commits (artwork `ContentProvider`, browse tree, manifest and
URI changes), `4633186` (retargets the dev shell emulator at AAOS), `f17f492` (the Nix
Android dev shell), and six `[skip ci]` version bumps. The `8cc3d8c` merge commit goes
with them, since only `134dae6` — the commit it merged — is replayed.

`f17f492` is dropped rather than kept because nothing in it survives the decision to
remove the Android dev shell: `flake.nix` is rewritten, `flake.lock` regenerated,
`docs/nixos-dev-setup.md` (254 lines) documents Android SDK setup, the design record
documents a reversed decision, and the `README.md` line points at the obsolete doc.
`.envrc` and the `.direnv/` gitignore entries go too, since no dev shell remains for
`use flake` to attach to.

The `archive/aaos` tag preserves the dropped work and makes the force-push to
`origin/main` recoverable. The stale `origin/aaos-artwork-provider` and
`origin/aaos-warm-plex-connection` branches are deleted.

Resulting diff against upstream:

```
.github/workflows/backend-production-config.yml
.github/workflows/pr-checks.yml
flake.nix
flake.lock
pkgs/default.nix
pkgs/phoebe/package.nix
docs/decisions/2026-08-18-phoebe-nixos-packaging-design.md
```

## Part 2 — The package

### Approach

Repackage the upstream `.deb` release rather than building from source.

Building from source is the author's default preference and remains the better
long-term answer, but the Phoebe desktop artifact is unusually well suited to a binary
repack: the bulk of the payload is JVM bytecode, which is architecture-independent and
contains no compiled-in paths. The parts that are not bytecode are enumerated and
handled below. This approach is time-boxed by the abort criteria.

### Layout

The `.deb` (166 MB, `release/2026.0816.2157`) unpacks to `/opt/phoebe`:

```
bin/Phoebe                     jpackage launcher, 17 KB ELF
lib/libapplauncher.so          jpackage support library
lib/app/*.jar                  application and dependency bytecode
lib/app/libskiko-linux-x64.so  Skiko renderer, shipped loose
lib/runtime/                   jlink'd JRE, JAVA_VERSION=22.0.2
lib/phoebe-Phoebe.desktop      desktop entry
lib/Phoebe.png                 icon
```

### Structure

`pkgs/phoebe/package.nix` takes `pkgs` arguments and is consumed via `callPackage`,
matching the author's `fleetdm-nix` and `sentinelone-nix` repositories:

```nix
# flake.nix
outputs = { self, nixpkgs }: {
  packages.x86_64-linux = import ./pkgs nixpkgs.legacyPackages.x86_64-linux;
};
```

A single `nixpkgs` input. No JDK is needed on the Nix side, because the `.deb` bundles
its own JDK 22.0.2 runtime. No `androidenv`, no `allowUnfree`, no
`android_sdk.accept_license`. `x86_64-linux` only, since the `.deb` is `amd64`.

`version` and `hash` are top-of-file constants; updating to a new upstream release is a
two-line change. The release asset URL encodes the slash in the tag:

```
https://github.com/j-roskopf/Phoebe/releases/download/release%2F<version>/Phoebe-<version>.deb
```

### Native code

Two classes, handled by different mechanisms. This split is the crux of the design.

**Patchable ELF** — `bin/Phoebe`, `lib/libapplauncher.so`,
`lib/app/libskiko-linux-x64.so`, and everything under `lib/runtime/`. These are real
files on disk at build time, so `autoPatchelfHook` rewrites their interpreter and
RPATH. Skiko shipping loose rather than inside a jar is the single largest de-risking
factor here; had it been jarred, the renderer would have been unpatchable.

Skiko ships a `libskiko-linux-x64.so.sha256` sidecar matching the shipped binary, which
patchelf necessarily invalidates. This is not expected to matter: `Phoebe.cfg` passes
`-Dskiko.library.path=$APPDIR`, so Skiko loads the library directly from the
application directory rather than taking the extract-from-jar path that consults the
sidecar. The sidecar is regenerated in `postFixup` anyway, because doing so costs two
lines and removes the need to be right about which code path Skiko takes.

`buildInputs` are taken from the `.deb`'s own `Depends:` field, which is authoritative
rather than guessed:

| Debian | nixpkgs |
| --- | --- |
| `libasound2t64` | `alsa-lib` |
| `libbrotli1` | `brotli` |
| `libbsd0` | `libbsd` |
| `libbz2-1.0` | `bzip2` |
| `libexpat1` | `expat` |
| `libfontconfig1` | `fontconfig` |
| `libfreetype6` | `freetype` |
| `libgcc-s1`, `libstdc++6` | `stdenv.cc.cc.lib` |
| `libgl1`, `libglvnd0`, `libglx0` | `libGL`, `libglvnd` |
| `libmd0` | `libmd` |
| `libpng16-16t64` | `libpng` |
| `libx11-6`, `libxau6`, `libxcb1`, `libxdmcp6` | `xorg.libX11`, `xorg.libXau`, `xorg.libxcb`, `xorg.libXdmcp` |
| `libxext6`, `libxi6`, `libxrender1`, `libxtst6` | `xorg.libXext`, `xorg.libXi`, `xorg.libXrender`, `xorg.libXtst` |
| `zlib1g` | `zlib` |
| `xdg-utils` | `xdg-utils` |

**Native libraries inside jars**, extracted to a temp directory at runtime. These are
invisible to `autoPatchelfHook`, so they are covered by `LD_LIBRARY_PATH` set through
`makeWrapper` — the same technique used by `modules/n64RecompLauncher-bin` in the
author's dotfiles.

| Jar | `.so` count | Requires |
| --- | --- | --- |
| `jogl-all`, `gluegen-rt` | 24 | `libGL` |
| `jna` | 21 | libc only |
| `sqlite-jdbc` | 18 | libc only |
| `javafx-media`, `javafx-graphics` | 21 | **gtk3, gstreamer** |
| `jnativehook` | 4 | `libX11`, `libXtst` |
| `filament-ffm` | 2 | libc, FFM API (JDK 22+) |

GTK and GStreamer are absent from the `.deb`'s `Depends:` because jpackage only scans
loose `.so` files, not jarred ones. JavaFX is the desktop playback *fallback* path
(upstream `751d5632`, "Fall back when JavaFX playback times out on desktop"), so it
stays dormant until the primary path times out and then fails in a way that is hard to
attribute. Both are therefore added to `LD_LIBRARY_PATH` preemptively rather than
reactively.

### Installation

The payload is copied to `$out/opt/phoebe` and `bin/Phoebe` is wrapped into `$out/bin`.
`lib/phoebe-Phoebe.desktop` is installed to `$out/share/applications` with `Exec`
rewritten to the wrapper path, and `lib/Phoebe.png` to
`$out/share/icons/hicolor/512x512/apps`.

Unlike `n64RecompLauncher-bin`, the payload is **not** copied to a writable directory
at runtime. Phoebe's storage root is `~/.phoebe`
(`core/platform/src/commonMain/kotlin/com/phoebe/app/platform/StorageNames.kt:17`), so
nothing is written inside the install prefix. The "writable SQLite storage" problem
fixed on upstream's `jr/flat` branch was specific to the Flatpak sandbox.

## Part 3 — Integration with dotfiles

Add the flake input, following its nixpkgs so the package builds against the
configuration's pinned `release-26.05` rather than adding a second nixpkgs to the
closure:

```nix
phoebe = {
  url = "github:codingismy11to7/Phoebe";
  inputs.nixpkgs.follows = "nixpkgs";
};
```

Expose it as `pkgs.phoebe` through an entry in `modules/overlays`, then add it to
`guiPackages` in `modules/packages/home.nix`, next to `feishin`. The overlay is
preferred over referencing `inputs.phoebe.packages.…` directly in the package list
because it matches how the existing list is written.

## Known limitations

The radio map's embedded browser is expected not to work. `jcef-api` and `jcefmaven`
are on the classpath, but no CEF native libraries ship in the `.deb`; `jcefmaven`
downloads the JCEF native bundle from GitHub on first use and extracts it at runtime.
Those downloaded binaries are unpatched ELF and will not run against the Nix store, and
because they arrive at runtime there is nothing for `autoPatchelfHook` to fix at build
time.

This affects only the radio map view. Core playback, browsing, and Plex sign-in are
unaffected. Verification should confirm the app degrades gracefully — that opening the
radio map fails to render the map rather than crashing the application.

Confirmed on 2026-08-18: the map does not render. The configuration's
`programs.nix-ld` shim, which lets some downloaded unpatched binaries run, is not
enough for Chromium's library set. The application stays running and remains fully
usable, so this is a missing feature rather than a defect.

Fixing this deliberately would mean pointing `jcefmaven` at a Nix-provided JCEF through
its install-directory setting, which is out of scope here and belongs with the source
build if it is ever wanted.

## Risks and abort criteria

Ship when the app launches, signs in to Plex, and plays a track.

Abandon the repack and pivot to a source build on either of:

- Skiko fails to initialize GPU rendering in a way `LD_LIBRARY_PATH` does not resolve.
- The bundled JDK 22.0.2 runtime fails to load `filament-ffm` after patchelf.

The first was the larger worry at design time and has since been substantially reduced
by the `-Dskiko.library.path` finding above, but it stays on the list because a
GPU-init failure is the one outcome that no amount of wrapper tuning fixes.

Both indicate the bundled runtime is fundamentally fighting the Nix store, which more
wrapper tuning will not fix. The tripwire exists to prevent open-ended debugging.

A known-good fallback if a working client is needed immediately: upstream publishes a
`.flatpak` each release, and `dotfiles` already has `nix-flatpak` wired up with a
`modules/flatpak`.

## Testing

1. `nix build` the package and confirm `autoPatchelfHook` reports no unresolved
   dependencies.
2. Launch the wrapper. On failure, run under
   `strace -f -e trace=openat` and read which `.so` the JVM is searching for; this
   converts a crash into a named missing library in one step.
3. Sign in to Plex and play a track, exercising the primary playback path.
4. Confirm `~/.phoebe` is created and its contents survive a restart.
5. Confirm the desktop entry appears in the launcher with its icon.

## Future work

A source build via `gradle.fetchDeps` (nixpkgs `gradle` 8.14.4 supports the mitm-cache
mechanism) would build this fork's own tree and remove the dependency on upstream's
release cadence.

Notably, this would **not** require restoring the `nixos-24.05` pin that the old flake
carried for JDK 22. That pin existed because `filament-ffm` requires "JVM runtime
version 22 or newer" and current nixpkgs dropped JDK 22 at end of life. But nixpkgs
carries `jdk25`, which satisfies "22 or newer". The pin was likely never necessary.

The main obstacle is instead the size of the dependency graph: Phoebe is a Kotlin
Multiplatform project with Android, iOS, desktop, and Wasm targets, so `fetchDeps` must
resolve all of them, and the resulting hash churns on every dependency bump.
