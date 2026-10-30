#!/usr/bin/env bash
# Build the launcher APK with bare SDK tools and install it on TV (Android 9 / API 28)
set -euo pipefail
cd "$(dirname "$0")"

# Путь к SDK на Windows (если переменная не задана в системе)
DEFAULT_WIN_SDK="/c/Users/$USER/AppData/Local/Android/Sdk"
SDK="${ANDROID_HOME:-$DEFAULT_WIN_SDK}"

# Поиск последней версии build-tools и платформы android-28 или выше
BT_DIR="$SDK/build-tools"
BT="$(ls -d "$BT_DIR"/* 2>/dev/null | sort -V | tail -n 1)"

if [ -f "$SDK/platforms/android-28/android.jar" ]; then
  JAR="$SDK/platforms/android-28/android.jar"
else
  # Если android-28 не скачан, берем любую доступную платформу SDK для линковки
  JAR="$(ls -d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -n 1)"
fi

STOCK_LAUNCHER="${STOCK_LAUNCHER:-com.google.android.apps.tv.launcherx}"
B=build

rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$B/dex"

# Компиляция ресурсов
"$BT/aapt2" compile --dir res -o "$B/res.zip"

# Линковка с установкой минимального и целевого SDK 28
"$BT/aapt2" link -o "$B/base.apk" -I "$JAR" --manifest AndroidManifest.xml --java "$B/gen" \
  -A assets --min-sdk-version 28 --target-sdk-version 33 "$B/res.zip"

# Компиляция Java исходников
javac -nowarn -Xlint:-options --release 11 -classpath "$JAR" -d "$B/classes" \
  $(find src "$B/gen" -name '*.java')

# Сборка DEX под API 28
"$BT/d8" --min-api 28 --lib "$JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')

# Упаковка dex в APK
cp "$B/base.apk" "$B/unsigned.apk"
(cd "$B/dex" && zip -q -u ../unsigned.apk classes.dex)

# Выравнивание и подпись
"$BT/zipalign" -f 4 "$B/unsigned.apk" "$B/aligned.apk"
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias tvhome -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tvhome" >/dev/null 2>&1
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --out "$B/tvhome.apk" "$B/aligned.apk"

echo "built $B/tvhome.apk ($(wc -c < "$B/tvhome.apk") bytes)"
[ "${1:-}" = "--build" ] && exit 0

PY=../tools/venv/bin/python
"$PY" -I ../tools/tv.py --push-b64 "$B/tvhome.apk" /data/local/tmp/tvhome.apk
printf '%s\n' \
  "pm install -r /data/local/tmp/tvhome.apk" \
  "rm -f /data/local/tmp/tvhome.apk" \
  "pm grant com.mihir.tvhome android.permission.ACCESS_FINE_LOCATION" \
  "cmd package set-home-activity --user 0 com.mihir.tvhome/.MainActivity" \
  "pm disable-user --user 0 $STOCK_LAUNCHER" \
  "pm disable-user --user 0 com.google.android.tungsten.setupwraith" \
  "cmd package set-home-activity --user 0 com.mihir.tvhome/.MainActivity" \
  "am force-stop com.android.tv.settings" \
  "input keyevent KEYCODE_HOME" \
  | "$PY" -I ../tools/tv.py --batch
echo "Installed. Press Home on the remote."