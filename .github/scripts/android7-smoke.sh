#!/usr/bin/env bash
# Runs inside an Android 7.0 (API 24) emulator. Installs the APK, opens the app,
# waits for the first-run question import, then fails if the app crashed.
set -u
APK="$1"
PKG=com.pastq.cbt
OUT=android7-results
mkdir -p "$OUT"

echo "Android release: $(adb shell getprop ro.build.version.release) (API $(adb shell getprop ro.build.version.sdk))" | tee "$OUT/device.txt"

adb logcat -c
if ! adb install -r "$APK" 2>&1 | tee "$OUT/install.txt" | grep -q Success; then
  echo "::error::APK did not install on Android 7"
  exit 1
fi

adb shell am start -W -n "$PKG/.MainActivity" | tee "$OUT/start.txt"
# First launch imports ~6,400 bundled questions into Room; give it time.
sleep 30
adb exec-out screencap -p > "$OUT/after-launch.png" || true

adb logcat -d > "$OUT/logcat.txt"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1 || true

status=0
if ! adb shell pidof "$PKG" | grep -q .; then
  echo "::error::App process is not running 30s after launch (crashed or killed)"
  status=1
fi
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "::error::Crash found in logcat"
  grep -A 25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
  status=1
fi
exit $status
