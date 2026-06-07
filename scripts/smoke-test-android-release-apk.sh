#!/usr/bin/env bash
set -euo pipefail

mkdir -p smoke-logs

apk="$(find androidApp/build/outputs/apk/release -name '*.apk' -print -quit)"
if [[ -z "${apk}" ]]; then
  echo "::error::Could not find release APK"
  exit 1
fi

adb install -r "${apk}"
adb logcat -c
adb shell am start \
  -n com.phoebe.app/.AndroidPlaybackSmokeActivity \
  --el phoebe.playbackSmoke.timeoutMs 30000

deadline=$((SECONDS + 60))
while (( SECONDS < deadline )); do
  adb logcat -d -s PhoebePlaybackSmoke:I '*:S' > smoke-logs/android-release-apk.log
  if grep -q 'PHOEBE_PLAYBACK_SMOKE_OK' smoke-logs/android-release-apk.log; then
    cat smoke-logs/android-release-apk.log
    exit 0
  fi
  if grep -q 'PHOEBE_PLAYBACK_SMOKE_FAILED' smoke-logs/android-release-apk.log; then
    cat smoke-logs/android-release-apk.log
    exit 1
  fi
  sleep 1
done

cat smoke-logs/android-release-apk.log
echo "::error::Timed out waiting for Android playback smoke result"
exit 1
