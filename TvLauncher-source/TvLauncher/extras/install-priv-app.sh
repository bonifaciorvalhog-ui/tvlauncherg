#!/bin/sh
# Instala como app privilegiado (aparelho com root). Uso: ./install-priv-app.sh caminho/do/app-debug.apk
set -e
APK="${1:?informe o APK}"
adb root
adb remount
adb shell mkdir -p /system/priv-app/TvLauncher
adb push "$APK" /system/priv-app/TvLauncher/TvLauncher.apk
adb push "$(dirname "$0")/privapp-permissions-tvlauncher.xml" /system/etc/permissions/
adb shell chmod 644 /system/priv-app/TvLauncher/TvLauncher.apk /system/etc/permissions/privapp-permissions-tvlauncher.xml
adb reboot
