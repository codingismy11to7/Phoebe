{
  description = "Phoebe Android development environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";

      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

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
      # cmdline-tools, whose directory is versioned ("19.0") and not "latest".
      avdmanager = "${androidComposition.androidsdk}/bin/avdmanager";

      phoebe-avd = pkgs.writeShellScriptBin "phoebe-avd" ''
        set -euo pipefail
        export ANDROID_HOME="${androidSdk}"
        export ANDROID_SDK_ROOT="${androidSdk}"
        export JAVA_HOME="${pkgs.jdk21}"
        # avdmanager honors $XDG_CONFIG_HOME and would otherwise write the AVD
        # to ~/.config/.android/avd, while the emulator binary's own default
        # search order never looks there. Pin both tools to the same
        # ~/.android/avd so they agree regardless of the host's XDG settings.
        # avdmanager only respects ANDROID_AVD_HOME if the directory already
        # exists, silently falling back to the XDG path otherwise, so create
        # it first.
        export ANDROID_AVD_HOME="$HOME/.android/avd"
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
        # Must match phoebe-avd's AVD location; see comment there.
        export ANDROID_AVD_HOME="$HOME/.android/avd"
        exec "${androidComposition.androidsdk}/bin/emulator" -avd "${avdName}" "$@"
      '';
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk21
          androidComposition.androidsdk
          phoebe-avd
          phoebe-emulator
        ];

        JAVA_HOME = "${pkgs.jdk21}";
        ANDROID_HOME = androidSdk;
        ANDROID_SDK_ROOT = androidSdk;

        # AGP otherwise downloads an aapt2 binary from Maven that cannot run on
        # NixOS. Point it at the Nix-patched one instead.
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/build-tools/${buildToolsVersion}/aapt2";

        # androidComposition.androidsdk ships a bin/ directory containing adb,
        # avdmanager, sdkmanager, emulator, d8, r8 and friends, so no PATH
        # manipulation is needed here.
        shellHook = ''
          echo "Phoebe dev shell: JDK $(java -version 2>&1 | head -n1 | awk -F'"' '{print $2}'), Android SDK ${platformVersion}"
        '';
      };
    };
}
