#!/bin/bash
# Installs the APK on the running emulator, opens the camera, takes a photo and
# opens the quick menu, saving screenshots, logcat and a short report to smoke-test/.
APK=$1
PKG=com.ozyern.brinacam
OUT=smoke-test
mkdir -p $OUT

# Tap the centre of the first UI element whose content-desc or text matches $1.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml $OUT/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
want, path = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
for node in root.iter('node'):
    if want in (node.get('content-desc', ''), node.get('text', '')):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -n "$xy" ]; then
    adb shell input tap $xy
    echo "tapped $1 at $xy" >> $OUT/report.txt
  else
    echo "could not find $1" >> $OUT/report.txt
  fi
}

adb install -r -g "$APK"
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
sleep 15
adb exec-out screencap -p > $OUT/1-launch.png

tap "Shutter"
sleep 6
adb exec-out screencap -p > $OUT/2-after-photo.png
echo "--- saved photos ---" >> $OUT/report.txt
adb shell content query --uri content://media/external/images/media --projection _display_name:relative_path \
  | grep BrinaCam >> $OUT/report.txt || echo "no photo found" >> $OUT/report.txt

tap "More controls"
sleep 3
adb exec-out screencap -p > $OUT/3-quick-menu.png
tap "More controls"
sleep 1

tap "VIDEO"
sleep 5
adb exec-out screencap -p > $OUT/4-video.png

adb logcat -d -b crash > $OUT/crash.txt
[ -s $OUT/crash.txt ] || echo "No crashes." > $OUT/crash.txt
adb logcat -d > $OUT/logcat-full.txt
echo "===== report ====="; cat $OUT/report.txt
echo "===== crash buffer ====="; cat $OUT/crash.txt
grep -E "AndroidRuntime|FATAL|BrinaCam" $OUT/logcat-full.txt | tail -80
exit 0
