#!/usr/bin/env bash
# Compila Quincho (release) y lo instala en la TV por adb.
#
# Uso:
#   scripts/instalar.sh [IP-de-la-TV] [--home]
#
#   IP-de-la-TV   por defecto 192.168.1.37
#   --home        además lo deja como launcher por defecto
set -euo pipefail

IP="192.168.1.37"
DEJAR_HOME=0
for arg in "$@"; do
    case "$arg" in
        --home) DEJAR_HOME=1 ;;
        *) IP="$arg" ;;
    esac
done

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
APK="$RAIZ/app/build/outputs/apk/release/app-release.apk"
PAQUETE="com.ombu.tv"

echo "==> Compilando (release)..."
(cd "$RAIZ" && ./gradlew assembleRelease --no-daemon -q)

echo "==> Conectando a $IP:5555..."
adb connect "$IP:5555" >/dev/null
adb -s "$IP:5555" get-state >/dev/null || {
    echo "No pude conectar. Revisá que la depuración por red esté activa en la TV." >&2
    exit 1
}

echo "==> Instalando $(du -h "$APK" | cut -f1)..."
adb -s "$IP:5555" install -r "$APK"

if [ "$DEJAR_HOME" -eq 1 ]; then
    echo "==> Dejándolo como launcher por defecto..."
    adb -s "$IP:5555" shell cmd package set-home-activity "$PAQUETE/.MainActivity"
fi

adb -s "$IP:5555" shell input keyevent KEYCODE_WAKEUP
adb -s "$IP:5555" shell am start -n "$PAQUETE/.MainActivity" >/dev/null
echo "Listo."
