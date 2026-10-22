#!/usr/bin/env bash
# 에뮬레이터에서 앱 화면을 캡처한다 (.github/workflows/app-screenshots.yml 에서 호출).
set -u
PKG=com.jeiu.fashionproject
APK=fashion_app/app/build/outputs/apk/debug/app-debug.apk
TEST_APK=fashion_app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
OUT="$GITHUB_WORKSPACE/screenshots"
mkdir -p "$OUT"

adb wait-for-device
# 에뮬레이터가 느려 생기는 시스템 앱 '응답 없음' 창이 화면을 가리지 않도록
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put global anr_show_background 0 || true
for i in $(seq 1 30); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 2; done
sleep 45   # 부팅 직후 바쁜 시간 넘기기
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true
# 위치를 서울로 (날씨가 서울 기준으로 나오도록), 한국어 · 서울 시간대
adb emu geo fix 126.9780 37.5665 || true
adb shell settings put system time_12_24 24 || true
adb shell setprop persist.sys.timezone Asia/Seoul || true
adb shell cmd locale set-app-locales "$PKG" --locales ko-KR 2>/dev/null || true

adb install -r -g "$APK"
adb install -r -g "$TEST_APK"
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION CAMERA POST_NOTIFICATIONS READ_MEDIA_IMAGES; do
  adb shell pm grant "$PKG" "android.permission.$p" 2>/dev/null || true
done
adb shell cmd locale set-app-locales "$PKG" --locales ko-KR 2>/dev/null || true
adb emu geo fix 126.9780 37.5665 || true

echo "== Railway 서버 상태 =="
curl -sS -m 20 https://fashion-backend-production-aba0.up.railway.app/api/health/database | tee "$OUT/server_health.txt" || echo "(서버 응답 없음)" | tee "$OUT/server_health.txt"
echo

for cls in A_MainTabsTour B_SubScreens; do
  adb shell am instrument -w -r \
    -e class "$PKG.screenshots.$cls" \
    "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee -a "$OUT/instrument.log" || true
done

adb pull "/sdcard/Android/data/$PKG/files/shots/." "$OUT/" || true
ls -la "$OUT"
exit 0
