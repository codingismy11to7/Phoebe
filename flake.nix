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
      platformVersion = "36";
      buildToolsVersion = "36.1.0";

      androidComposition = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ platformVersion ];
        buildToolsVersions = [ buildToolsVersion ];
        includeEmulator = false;
        includeSystemImages = false;
        includeSources = false;
      };

      androidSdk = "${androidComposition.androidsdk}/libexec/android-sdk";
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk21
          androidComposition.androidsdk
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
          echo "Phoebe dev shell: JDK $(java -version 2>&1 | head -n1 | cut -d'\"' -f2), Android SDK ${platformVersion}"
        '';
      };
    };
}
