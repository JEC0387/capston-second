#!/usr/bin/env bash
# Runs inside the emulator step (working dir: fashion_app).
set -x
OUT="$GITHUB_WORKSPACE/shots"; mkdir -p "$OUT"
adb wait-for-device
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Clean status bar (demo mode): 09:41, full battery, full wifi, no notifications
adb shell settings put global sysui_demo_allowed 1
for cmd in "enter" "clock -e hhmm 0941" "battery -e level 100 -e plugged false" \
           "network -e wifi show -e level 4 -e mobile hide" "notifications -e visible false"; do
  adb shell am broadcast -a com.android.systemui.demo -e command $cmd >/dev/null
done
adb logcat -c
adb shell am instrument -w -r -e class com.jeiu.fashionproject.PresentationScreenshotTest \
  -e presentationShots true com.jeiu.fashionproject.test/androidx.test.runner.AndroidJUnitRunner | tee "$OUT/instrument.txt"
adb shell run-as com.jeiu.fashionproject ls files/presentation-shots | tee "$OUT/list.txt"
for f in $(adb shell run-as com.jeiu.fashionproject ls files/presentation-shots | tr -d '\r'); do
  adb exec-out run-as com.jeiu.fashionproject cat "files/presentation-shots/$f" > "$OUT/$f"
done
adb logcat -d | grep -E "SHOTS_|AndroidRuntime|TestRunner|FATAL" | tail -400 > "$OUT/logcat.txt"
ls -la "$OUT"
true
