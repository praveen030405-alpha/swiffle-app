#!/usr/bin/env bash
# CI only. This script uses a fresh disposable emulator and a debug build.
set -euo pipefail
cd "$(dirname "$0")"
adb install ../../app/build/outputs/apk/debug/app-debug.apk
python check_connection.py --mode disabled
for access in 1 2; do
    adb shell am force-stop com.swiffle.app
    adb shell run-as com.swiffle.app mkdir -p shared_prefs
    echo "<map><int name=\"evidence\" value=\"$access\" /></map>" |
        adb shell 'run-as com.swiffle.app sh -c "cat > shared_prefs/swiffle_plugin_access.xml"'
    if [[ "$access" == 1 ]]; then
        python check_connection.py --mode read-only
    else
        python check_connection.py --mode create
    fi
done
