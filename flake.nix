{
  description = "Phoebe Android development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

    # JDK 22 reached end of life and was removed from current nixpkgs, but the
    # project genuinely requires it: the desktop target pulls in
    # io.github.erkko68.filament:filament-compose, whose filament-ffm artifact
    # uses the Foreign Function & Memory API finalized in JDK 22 and publishes
    # metadata requiring "JVM runtime version 22 or newer". Building against
    # JDK 21 fails dependency resolution outright. This older nixpkgs exists
    # solely to supply that JDK.
    nixpkgs-jdk22.url = "github:NixOS/nixpkgs/nixos-24.05";
  };

  outputs =
    { self, nixpkgs, nixpkgs-jdk22 }:
    let
      system = "x86_64-linux";

      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      jdk = (import nixpkgs-jdk22 {
        inherit system;
        config.allowUnfree = true;
      }).jdk22;

      # Must match compileSdk / targetSdk in androidApp/build.gradle.kts.
      # AGP defaults to build-tools "<compileSdk>.0.0" unless a
      # buildToolsVersion is set explicitly in Gradle, so this must track
      # that default (36.0.0), not just the latest 36.x release.
      platformVersion = "36";
      buildToolsVersion = "36.0.0";
      systemImageType = "google_apis";
      abiVersion = "x86_64";
      avdName = "phoebe-api${platformVersion}";

      androidComposition = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ platformVersion ];
        buildToolsVersions = [ buildToolsVersion ];
        systemImageTypes = [ systemImageType ];
        abiVersions = [ abiVersion ];
        includeEmulator = true;
        includeSystemImages = true;
        includeSources = false;
      };

      androidSdk = "${androidComposition.androidsdk}/libexec/android-sdk";

      # Use the wrappers in the package's bin/ rather than reaching into
      # cmdline-tools, whose directory is versioned (not "latest").
      avdmanager = "${androidComposition.androidsdk}/bin/avdmanager";

      # avdmanager honors $XDG_CONFIG_HOME and would otherwise write the AVD
      # to ~/.config/.android/avd, while the emulator binary's own default
      # search order never looks there. Pin both tools to the same
      # ~/.android/avd so they agree regardless of the host's XDG settings.
      # Shared by the shellHook (so avdmanager/emulator invoked directly in
      # the dev shell also agree) and both helper scripts below (so they
      # still work when invoked outside the dev shell).
      avdHomeExport = ''export ANDROID_AVD_HOME="$HOME/.android/avd"'';

      phoebe-avd = pkgs.writeShellScriptBin "phoebe-avd" ''
        set -euo pipefail
        export ANDROID_HOME="${androidSdk}"
        export ANDROID_SDK_ROOT="${androidSdk}"
        export JAVA_HOME="${jdk.home}"
        ${avdHomeExport}
        # avdmanager only respects ANDROID_AVD_HOME if the directory already
        # exists, silently falling back to the XDG path otherwise, so create
        # it first.
        mkdir -p "$ANDROID_AVD_HOME"
        if "${avdmanager}" list avd -c | grep -qx "${avdName}"; then
          echo "AVD '${avdName}' already exists."
          exit 0
        fi
        echo "no" | "${avdmanager}" create avd \
          --name "${avdName}" \
          --package "system-images;android-${platformVersion};${systemImageType};${abiVersion}"
        echo "Created AVD '${avdName}'."
      '';

      phoebe-emulator = pkgs.writeShellScriptBin "phoebe-emulator" ''
        set -euo pipefail
        export ANDROID_HOME="${androidSdk}"
        export ANDROID_SDK_ROOT="${androidSdk}"
        ${avdHomeExport}
        exec "${androidComposition.androidsdk}/bin/emulator" -avd "${avdName}" "$@"
      '';
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          jdk
          androidComposition.androidsdk
          phoebe-avd
          phoebe-emulator
        ];

        JAVA_HOME = "${jdk.home}";
        ANDROID_HOME = androidSdk;
        ANDROID_SDK_ROOT = androidSdk;

        # AGP otherwise downloads an aapt2 binary from Maven that cannot run on
        # NixOS. Point it at the Nix-patched one instead.
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/build-tools/${buildToolsVersion}/aapt2";

        # androidComposition.androidsdk ships a bin/ directory containing adb,
        # avdmanager, sdkmanager, emulator, d8, r8 and friends, so no PATH
        # manipulation is needed here.
        shellHook = ''
          ${avdHomeExport}
          echo "Phoebe dev shell: JDK $(java -version 2>&1 | head -n1 | awk -F'"' '{print $2}'), Android SDK ${platformVersion}" >&2
        '';
      };
    };
}
