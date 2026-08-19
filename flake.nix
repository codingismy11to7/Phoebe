{
  description = "Phoebe desktop development environment for NixOS";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";

      pkgs = import nixpkgs { inherit system; };

      # JDK 25 rather than the 22 the build config originally asked for.
      #
      # Gradle's jvmToolchain() is an exact match, not a floor, and nixpkgs has
      # no JDK 22 under any name -- it reached end of life and was removed. The
      # only real constraint is filament-ffm, which requires "JVM runtime
      # version 22 or newer" and is satisfied by 25, so composeApp's toolchain
      # and desktop jvmTarget are set to 25 instead.
      #
      # Deliberately from the same nixpkgs as the libraries below. Sourcing the
      # JDK from an older pinned nixpkgs works in isolation but breaks the
      # moment LD_LIBRARY_PATH is set: the newer libm and libmount shadow the
      # older glibc the JDK is linked against, and java itself stops running
      # with "version GLIBC_ABI_DT_X86_64_PLT not found".
      jdk = pkgs.jdk25;

      # Gradle downloads Phoebe's native dependencies from Maven as unpatched
      # ELF and dlopens them at runtime, so nothing here can be fixed by
      # patchelf at build time -- it has to resolve through LD_LIBRARY_PATH.
      #
      # Determined empirically by running the app and ldd-ing what it extracted:
      #
      #   Skiko        the renderer; needs libGL and the font/image stack
      #   JavaFX       libglassgtk3.so, the desktop playback fallback path.
      #                Needs the *full* GTK stack: gtk3 supplies only
      #                libgtk-3/libgdk-3, so pango, cairo, atk, gdk-pixbuf and
      #                glib (libgthread, libgobject, libgio) are all separate.
      #                gstreamer is for javafx-media.
      #   JNativeHook  global media keys; needs X11 input plumbing including
      #                libxkbcommon-x11, libxt and libxinerama.
      nativeLibs = with pkgs; [
        alsa-lib
        atk
        brotli
        bzip2
        cairo
        expat
        fontconfig
        freetype
        gdk-pixbuf
        glib
        gtk3
        libGL
        libbsd
        libglvnd
        libmd
        libpng
        libx11
        libxau
        libxcb
        libxdmcp
        libxext
        libxi
        libxinerama
        libxkbcommon
        libxrender
        libxt
        libxtst
        pango
        stdenv.cc.cc.lib
        zlib
        gst_all_1.gstreamer
        gst_all_1.gst-plugins-base
      ];
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          jdk
          pkgs.xdg-utils
        ];

        JAVA_HOME = "${jdk.home}";

        LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath nativeLibs;

        shellHook = ''
          echo "Phoebe desktop dev shell: JDK $(java -version 2>&1 | head -n1 | awk -F'"' '{print $2}')" >&2
        '';
      };
    };
}
