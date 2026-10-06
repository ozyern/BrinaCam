#!/bin/bash
# Installs the APK on the running emulator, opens the camera screen and saves
# the crash log plus screenshots to smoke-test/.
APK=$1
PKG=com.almalence.opencam
OUT=smoke-test
mkdir -p $OUT

adb install -r -g "$APK"
adb shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow || true
adb logcat -c
adb shell am start -W -n $PKG/.MainScreen
sleep 20
adb exec-out screencap -p > $OUT/screen-1.png
# Dismiss a first-run dialog if one is showing, then capture again.
adb shell input keyevent KEYCODE_BACK
sleep 3
adb shell am start -W -n $PKG/.MainScreen
sleep 10
adb exec-out screencap -p > $OUT/screen-2.png

adb logcat -d -b crash > $OUT/crash.txt
adb logcat -d > $OUT/logcat-full.txt
echo "===== crash buffer ====="
cat $OUT/crash.txt
echo "===== app log ====="
PID=$(adb shell pidof $PKG)
echo "pid: $PID"
grep -E "AndroidRuntime|FATAL|NativeLibs|almalence|Almalence|linker|UnsatisfiedLink" $OUT/logcat-full.txt | tail -150
exit 0
