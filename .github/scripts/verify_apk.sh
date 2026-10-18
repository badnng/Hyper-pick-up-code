#!/usr/bin/env bash
set -euo pipefail

apk=${1:?APK path is required}
sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Android SDK is required}}
# 与工作流安装的版本一致，避免 runner 预装的新版本改变校验行为。
build_tools_version=${2:-36.1.0}
apksigner="$sdk_root/build-tools/$build_tools_version/apksigner"
test -x "$apksigner" || { echo "apksigner not found for Build Tools $build_tools_version"; exit 1; }
echo "Verify APK with Android Build Tools $build_tools_version"

# 36.x: Signer #1 certificate ...；37.x: V2 Signer: certificate ...。
# 同一证书可出现在多个签名方案中；去重后仍必须只有预期的分发证书。
signature=$("$apksigner" verify --print-certs "$apk" |
    sed -nE 's/^(Signer #[0-9]+|V[0-9.]+ Signer:) certificate SHA-256 digest: ([[:xdigit:]]{64})\r?$/\2/p' |
    tr '[:upper:]' '[:lower:]' | sort -u)
expected=9400b749bcd477daa317c4026d25ec97dd033be3b748fd89e42a989a093b97ca
test -n "$signature" || { echo 'Cannot read APK signing certificate SHA-256 digest'; exit 1; }
if [[ "$signature" != "$expected" ]]; then
    echo "APK signing certificate mismatch. Expected: $expected"
    printf 'Actual certificate digest(s):\n%s\n' "$signature"
    exit 1
fi
echo "APK signature verified: $signature"
