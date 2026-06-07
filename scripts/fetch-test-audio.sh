#!/usr/bin/env bash
# Downloads small, redistributable audio fixtures for local Phoebe testing.
# See docs/agent-local-media-testing.md for licenses and manual validation steps.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/composeApp/src/commonTest/resources/test-audio"
ANDROID_DEVICE_TEST_ASSETS="${ROOT}/composeApp/src/androidDeviceTest/assets/test-audio"
mkdir -p "${OUT}"

echo "Downloading test audio into ${OUT} ..."

download_audio() {
  local dest="$1"
  local url="$2"
  local tmp="${TMPDIR:-/tmp}/phoebe-test-audio-download.$$-$(basename "${dest}")"
  if curl -fsSL --retry 3 --retry-delay 2 --retry-connrefused --connect-timeout 10 \
      -o "${tmp}" \
      "${url}"; then
    mv "${tmp}" "${dest}"
    return 0
  fi
  rm -f "${tmp}"
  return 1
}

# 1) Wikimedia Commons — Example.ogg (MP3 transcode, 180 kbps). License: CC BY-SA 3.0 (+ GFDL; see file page).
download_audio \
  "${OUT}/wikimedia-example.mp3" \
  "https://upload.wikimedia.org/wikipedia/commons/transcoded/c/c8/Example.ogg/Example.ogg.mp3"
sleep 1

# 2) Same work, Ogg Vorbis original (useful to verify .ogg local playback).
download_audio \
  "${OUT}/wikimedia-example.ogg" \
  "https://upload.wikimedia.org/wikipedia/commons/c/c8/Example.ogg"
sleep 1

# 3) MDN Web Docs interactive examples — CC0 1.0 Universal (public domain dedication).
short_mp3_title="T-Rex Roar"
short_mp3_artist="MDN / Mozilla (CC0)"
short_mp3_album="MDN interactive examples"
if ! download_audio \
  "${OUT}/mdn-t-rex-roar-cc0.mp3" \
  "https://interactive-examples.mdn.mozilla.net/media/cc0-audio/t-rex-roar.mp3"; then
  echo "Warning: MDN audio download failed; deriving short MP3 fixture from wikimedia-example.ogg." >&2
  if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "Error: ffmpeg is required to derive the fallback short MP3 fixture." >&2
    exit 1
  fi
  tmp="${TMPDIR:-/tmp}/phoebe-test-audio-mdn-fallback.$$-mdn-t-rex-roar-cc0.mp3"
  if ffmpeg -y -nostdin -loglevel error \
      -i "${OUT}/wikimedia-example.ogg" \
      -map 0:a -t 2.5 -c:a libmp3lame -q:a 4 \
      "${tmp}" 2>/dev/null && mv "${tmp}" "${OUT}/mdn-t-rex-roar-cc0.mp3"; then
    short_mp3_title="Example (short MP3 fallback)"
    short_mp3_artist="Wikimedia Commons"
    short_mp3_album="Example.ogg (short MP3 fixture)"
  else
    rm -f "${tmp}"
    echo "Error: could not derive fallback short MP3 fixture." >&2
    exit 1
  fi
fi

# 4) WAV / FLAC / M4A — same decoded audio as wikimedia-example.ogg (same Commons license).
#    Requires ffmpeg. Lets you verify scanning + playback for common lossless / container paths
#    without additional remote URLs.
derive_wikimedia_fixtures() {
  if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "Note: ffmpeg not found; skipping WAV/FLAC/M4A derived fixtures." >&2
    return 0
  fi
  local src="${OUT}/wikimedia-example.ogg"
  if [[ ! -f "${src}" ]]; then
    echo "Error: missing ${src}; cannot derive other formats." >&2
    return 1
  fi
  transcode_one() {
    local dest="$1"
    shift
    local tmp="${TMPDIR:-/tmp}/phoebe-test-audio-tc.$$-$(basename "${dest}")"
    if ffmpeg -y -nostdin -loglevel error -i "${src}" "$@" "${tmp}" 2>/dev/null && mv "${tmp}" "${dest}"; then
      echo "Derived $(basename "${dest}") from $(basename "${src}")"
    else
      rm -f "${tmp}"
      echo "Warning: could not derive $(basename "${dest}")" >&2
    fi
  }
  transcode_one "${OUT}/wikimedia-example.wav" -map 0:a -c:a pcm_s16le
  transcode_one "${OUT}/wikimedia-example.flac" -map 0:a -c:a flac
  transcode_one "${OUT}/wikimedia-example.m4a" -map 0:a -c:a aac -b:a 128k
}

derive_wikimedia_fixtures

# Optional: embed predictable ID3/Vorbis metadata so the app can group by artist/album on
# desktop (jaudiotagger) and Android (MediaMetadataRetriever). Remote bytes often have
# empty or inconsistent tags. Requires ffmpeg on PATH; if missing, tags are whatever the
# upstream files contain.
embed_test_tags() {
  local path="$1" title="$2" artist="$3" album="$4"
  if ! command -v ffmpeg >/dev/null 2>&1; then
    return 0
  fi
  # Temp name must keep a real extension (e.g. .mp3) or ffmpeg cannot pick a muxer.
  local tmp="${TMPDIR:-/tmp}/phoebe-test-audio-tag.$$-$(basename "${path}")"
  local ext
  ext="$(basename "${path}")"
  ext="${ext##*.}"
  ext="$(printf '%s' "${ext}" | tr '[:upper:]' '[:lower:]')"
  local fmt=""
  case "${ext}" in
    mp3) fmt=mp3 ;;
    ogg|opus) fmt=ogg ;;
    m4a|aac) fmt=ipod ;;
    flac) fmt=flac ;;
    wav) fmt=wav ;;
  esac
  local fmt_args=()
  if [[ -n "${fmt}" ]]; then
    fmt_args=(-f "${fmt}")
  fi
  if ffmpeg -y -nostdin -loglevel error -i "${path}" -map 0 -c copy \
      "${fmt_args[@]}" \
      -metadata "title=${title}" -metadata "artist=${artist}" -metadata "album=${album}" \
      "${tmp}" 2>/dev/null && mv "${tmp}" "${path}"; then
    echo "Embedded test tags: $(basename "${path}")"
  else
    rm -f "${tmp}"
    echo "Warning: ffmpeg could not embed tags for $(basename "${path}"); leaving upstream metadata." >&2
  fi
}

embed_test_tags "${OUT}/wikimedia-example.mp3" \
  "Example (MP3 transcode)" "Wikimedia Commons" "Example.ogg (MP3 fixture)"
embed_test_tags "${OUT}/wikimedia-example.ogg" \
  "Example (Ogg original)" "Wikimedia Commons" "Example.ogg (Ogg fixture)"
embed_test_tags "${OUT}/mdn-t-rex-roar-cc0.mp3" \
  "${short_mp3_title}" "${short_mp3_artist}" "${short_mp3_album}"
if [[ -f "${OUT}/wikimedia-example.wav" ]]; then
  embed_test_tags "${OUT}/wikimedia-example.wav" \
    "Example (WAV)" "Wikimedia Commons" "Example.ogg (WAV fixture)"
fi
if [[ -f "${OUT}/wikimedia-example.flac" ]]; then
  embed_test_tags "${OUT}/wikimedia-example.flac" \
    "Example (FLAC)" "Wikimedia Commons" "Example.ogg (FLAC fixture)"
fi
if [[ -f "${OUT}/wikimedia-example.m4a" ]]; then
  embed_test_tags "${OUT}/wikimedia-example.m4a" \
    "Example (AAC/M4A)" "Wikimedia Commons" "Example.ogg (M4A fixture)"
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "Note: install ffmpeg for derived WAV/FLAC/M4A fixtures and for consistent embedded tags." >&2
fi

mkdir -p "${ANDROID_DEVICE_TEST_ASSETS}"
find "${OUT}" -maxdepth 1 -type f ! -name ".gitignore" -exec cp {} "${ANDROID_DEVICE_TEST_ASSETS}/" \;
echo "Mirrored Android device-test assets into ${ANDROID_DEVICE_TEST_ASSETS}"

echo "Done. Files:"
ls -la "${OUT}"
