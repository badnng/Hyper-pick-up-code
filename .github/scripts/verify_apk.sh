#!/usr/bin/env bash
set -euo pipefail

apk=${1:?APK path is required}
sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Android SDK is required}}
apksigner=$(find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -name apksigner -type f | sort -V | tail -n 1)
test -n "$apksigner" || { echo 'apksigner not found'; exit 1; }
signature=$("$apksigner" verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
expected=9400b749bcd477daa317c4026d25ec97dd033be3b748fd89e42a989a093b97ca
test "$signature" = "$expected" || { echo 'APK signing certificate mismatch'; exit 1; }
echo "APK signature verified: $signature"
