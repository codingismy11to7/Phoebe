{
  lib,
  stdenv,
  fetchurl,
  dpkg,
  autoPatchelfHook,
  makeWrapper,
  alsa-lib,
  atk,
  brotli,
  bzip2,
  cairo,
  expat,
  fontconfig,
  freetype,
  gdk-pixbuf,
  glib,
  gst_all_1,
  gtk3,
  libGL,
  libbsd,
  libglvnd,
  libmd,
  libpng,
  libx11,
  libxau,
  libxcb,
  libxdmcp,
  libxext,
  libxi,
  libxinerama,
  libxkbcommon,
  libxrender,
  libxt,
  libxtst,
  pango,
  xdg-utils,
  zlib,
}:

let
  version = "2026.0816.2157";

  # Taken from the .deb's own Depends: field. jpackage generates that list by
  # scanning loose .so files, so it is authoritative for everything
  # autoPatchelfHook can see -- and silent about anything inside a jar.
  debDepends = [
    alsa-lib
    brotli
    bzip2
    expat
    fontconfig
    freetype
    libGL
    libbsd
    libglvnd
    libmd
    libpng
    stdenv.cc.cc.lib
    zlib
    libx11
    libxau
    libxcb
    libxdmcp
    libxext
    libxi
    libxrender
    libxtst
  ];

  # Native libraries that ship inside jars and are unpacked at runtime -- JavaFX
  # into ~/.openjfx/cache, JNativeHook into ~/.cache/phoebe/native.
  # autoPatchelfHook never sees these files, so they have to resolve through
  # LD_LIBRARY_PATH instead. None of this appears in the .deb's Depends:,
  # because jpackage generates that list by scanning loose .so files only.
  #
  # Determined empirically: run the app, then ldd the extracted libraries. As of
  # 2026-08-18 that is libglassgtk3.so (the JavaFX GTK backend, used on the
  # desktop playback fallback path) and libJNativeHook (global media keys).
  runtimeDeps = debDepends ++ [
    # libglassgtk3.so: the full GTK stack. gtk3 alone is not enough -- it
    # supplies only libgtk-3/libgdk-3, while pango, cairo, atk, gdk-pixbuf and
    # glib (libgthread, libgobject, libgio) are separate derivations.
    atk
    cairo
    gdk-pixbuf
    glib
    gtk3
    pango

    # libJNativeHook: X11 input plumbing beyond what Depends: listed.
    libxinerama
    libxkbcommon
    libxt

    # javafx-media playback.
    gst_all_1.gstreamer
    gst_all_1.gst-plugins-base
  ];
in
stdenv.mkDerivation {
  pname = "phoebe";
  inherit version;

  src = fetchurl {
    url = "https://github.com/j-roskopf/Phoebe/releases/download/release%2F${version}/Phoebe-${version}.deb";
    hash = "sha256-0EFRnHJu/xIpHlUf6aYu0hYzAO46vmvWHJ9GI+Elc8o=";
  };

  nativeBuildInputs = [
    autoPatchelfHook
    dpkg
    makeWrapper
  ];

  buildInputs = debDepends;

  unpackPhase = ''
    runHook preUnpack
    dpkg-deb -x "$src" .
    runHook postUnpack
  '';

  dontConfigure = true;
  dontBuild = true;

  installPhase = ''
    runHook preInstall

    mkdir -p "$out/opt"
    cp -r opt/phoebe "$out/opt/phoebe"

    makeWrapper "$out/opt/phoebe/bin/Phoebe" "$out/bin/Phoebe" \
      --prefix LD_LIBRARY_PATH : "${lib.makeLibraryPath runtimeDeps}" \
      --prefix PATH : "${lib.makeBinPath [ xdg-utils ]}"

    install -Dm644 opt/phoebe/lib/Phoebe.png \
      "$out/share/icons/hicolor/512x512/apps/phoebe.png"

    install -Dm644 opt/phoebe/lib/phoebe-Phoebe.desktop \
      "$out/share/applications/phoebe.desktop"

    substituteInPlace "$out/share/applications/phoebe.desktop" \
      --replace-fail "Exec=/opt/phoebe/bin/Phoebe" "Exec=$out/bin/Phoebe" \
      --replace-fail "Icon=/opt/phoebe/lib/Phoebe.png" "Icon=phoebe" \
      --replace-fail "Categories=Unknown" "Categories=AudioVideo;Audio;Player;"

    runHook postInstall
  '';

  # autoPatchelfHook rewrites libskiko-linux-x64.so, invalidating the sidecar
  # hash shipped next to it. Phoebe.cfg passes -Dskiko.library.path=$APPDIR, so
  # Skiko loads straight from this directory and never consults the sidecar --
  # but regenerating it is cheap and means not having to be right about that.
  #
  # This must NOT be postFixup: runHook evaluates the postFixup *variable*
  # before running the postFixupHooks array, and autoPatchelfHook registers
  # itself in that array. A postFixup here therefore hashes the library before
  # it is patched and writes a sidecar that is stale the moment autoPatchelf
  # runs. A phase appended via postPhases runs after fixupPhase completes, which
  # is late enough.
  postPhases = [ "regenSkikoSidecar" ];

  regenSkikoSidecar = ''
    (
      cd "$out/opt/phoebe/lib/app"
      sha256sum libskiko-linux-x64.so | cut -d' ' -f1 > libskiko-linux-x64.so.sha256
    )
  '';

  meta = {
    description = "Compose Multiplatform music client for Plex, Jellyfin, Emby, and Subsonic servers";
    homepage = "https://github.com/j-roskopf/Phoebe";
    license = lib.licenses.mit;
    mainProgram = "Phoebe";
    platforms = [ "x86_64-linux" ];
    sourceProvenance = [ lib.sourceTypes.binaryNativeCode ];
  };
}
