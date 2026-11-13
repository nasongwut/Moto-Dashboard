#!/usr/bin/env bash
# =============================================================================
#  MotoDash: build (and optionally install) the APK
#  (วิธีเดียวกับ Mobile Remote)
#
#  Usage:
#    ./build.sh             build MotoDash.apk
#    ./build.sh install     build, then install on every phone connected over adb
#                           + ให้สิทธิ์ กล้อง/ตำแหน่ง/Bluetooth/แจ้งเตือน/ควบคุมเพลง แล้วเปิดแอป
#    ./build.sh clean       remove build output, then build
#
#  First time? Run ./setup-dev.sh to install JDK, Android SDK, and Gradle.
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

PKG="com.motodash.tracer"

# ใช้ env ของ MotoDash ถ้ามี ไม่งั้นใช้ของ Mobile Remote (เครื่องมือชุดเดียวกัน)
if [ -f "$HOME/.motodash-env" ]; then source "$HOME/.motodash-env"
elif [ -f "$HOME/.mobileremote-env" ]; then source "$HOME/.mobileremote-env"; fi

if [ -z "${ANDROID_HOME:-}" ] || [ -z "${JAVA_HOME:-}" ] || [ ! -x ./gradlew ]; then
  echo "Development environment not found. Running ./setup-dev.sh first…"
  ./setup-dev.sh --no-build
  source "$HOME/.motodash-env"
fi
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

if [ "${1:-}" = "clean" ]; then
  ./gradlew clean -q
fi

echo "==> Building"
./gradlew assembleDebug -q
cp app/build/outputs/apk/debug/app-debug.apk ./MotoDash.apk
echo "==> Done: $(pwd)/MotoDash.apk"

if [ "${1:-}" = "install" ]; then
  ADB="$ANDROID_HOME/platform-tools/adb"
  devices=$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1}')
  [ -n "$devices" ] || { echo "No phone connected. Enable USB debugging and run: adb devices"; exit 1; }
  for d in $devices; do
    echo "==> Installing on $d ($("$ADB" -s "$d" shell getprop ro.product.model | tr -d '\r'))"
    "$ADB" -s "$d" install --user 0 -r MotoDash.apk

    echo "==> Granting permissions"
    for p in CAMERA ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION BLUETOOTH_CONNECT POST_NOTIFICATIONS READ_MEDIA_AUDIO; do
      "$ADB" -s "$d" shell pm grant --user 0 "$PKG" "android.permission.$p" 2>/dev/null || true
    done
    # ทำงานเบื้องหลังได้ไม่จำกัด (กันระบบปิดการบันทึก) + ปลุกจอตอนไฟเข้า
    "$ADB" -s "$d" shell dumpsys deviceidle whitelist +"$PKG" >/dev/null 2>&1 || true
    "$ADB" -s "$d" shell cmd appops set --user 0 "$PKG" RUN_ANY_IN_BACKGROUND allow 2>/dev/null || true
    "$ADB" -s "$d" shell cmd appops set --user 0 "$PKG" USE_FULL_SCREEN_INTENT allow 2>/dev/null || true
    # แท็บสื่อ: เห็น/ควบคุมเพลงจาก YouTube Music, Spotify โดยไม่ต้องเด้งออกไปแอปอื่น
    "$ADB" -s "$d" shell cmd notification allow_listener "$PKG/$PKG.media.MediaListenerService" 2>/dev/null || true
    # ดึง MotoDash กลับมาหน้าจอหลังสั่งเล่นเพลงจากแอปอื่น
    "$ADB" -s "$d" shell cmd appops set --user 0 "$PKG" SYSTEM_ALERT_WINDOW allow 2>/dev/null || true

    "$ADB" -s "$d" shell am start --user 0 -n "$PKG/.MainActivity" >/dev/null
    echo "==> Launched MotoDash on $d"
  done
fi
