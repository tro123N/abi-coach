#!/usr/bin/env bash
# Läuft auf dem virtuellen Handy: installiert App und Test, klickt AbiCoach mit SmokeTest durch
# und legt Ergebnis, Logs und Bildschirmfotos auf den Branch "ci-smoke".
set -uo pipefail
cd "$(dirname "$0")/.."
source ci/publish-branch.sh

PKG="de.abicoach"
RUNNER="${PKG}.test/androidx.test.runner.AndroidJUnitRunner"
OUT=smoke-out
mkdir -p "$OUT/screens"
APP="app/build/outputs/apk/debug/app-debug.apk"
TEST="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
status=1

adb wait-for-device
adb shell settings put global hide_error_dialogs 1 || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
if adb install -r -g "$APP" && adb install -r -g "$TEST"; then
  adb shell cmd locale set-app-locales "$PKG" --locales de-DE || true
  adb logcat -c || true
  timeout -k 30 1500 adb shell am instrument -w -e class de.abicoach.SmokeTest "$RUNNER" 2>&1 | tee "$OUT/instrument.txt"
  grep -q "^OK (" "$OUT/instrument.txt" && status=0
  for f in $(timeout 30 adb shell run-as "$PKG" ls files/screens 2>/dev/null | tr -d '\r'); do
    timeout 30 adb exec-out run-as "$PKG" cat "files/screens/$f" > "$OUT/screens/$f" || true
  done
  timeout 60 adb logcat -d -v time '*:E' > "$OUT/logcat-errors.txt" 2>&1 || true
  timeout 60 adb logcat -d -v time -s AbiCoachSmoke:V AndroidRuntime:V TestRunner:V > "$OUT/logcat-test.txt" 2>&1 || true
else
  echo "Installation fehlgeschlagen" > "$OUT/instrument.txt"
fi
{
  echo "commit: ${GITHUB_SHA:-?}"
  echo "run: ${RUN_NUMBER:-?}"
  if [ "$status" -eq 0 ]; then echo "Ergebnis: OK"; else echo "Ergebnis: FEHLER"; fi
  echo "Bilder: $(ls "$OUT/screens" | wc -l)"
  echo
  tail -n 80 "$OUT/instrument.txt"
} > "$OUT/summary.txt"
publish_branch ci-smoke "$OUT" "Smoke-Test Lauf ${RUN_NUMBER:-?}"
exit 0
