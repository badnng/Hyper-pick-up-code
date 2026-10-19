#!/usr/bin/env bash
set -euo pipefail

: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
: "${TELEGRAM_BOT_API_COMMIT:?A pinned official source commit is required}"
[[ "$TELEGRAM_BOT_API_COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid Bot API source commit'; exit 1; }

source_dir="$RUNNER_TEMP/telegram-bot-api-source"
build_dir="$RUNNER_TEMP/telegram-bot-api-build"
binary_dir="$RUNNER_TEMP/telegram-bot-api-bin"

sudo apt-get update -qq
sudo apt-get install --no-install-recommends -y clang cmake ninja-build gperf libssl-dev zlib1g-dev
git init "$source_dir"
git -C "$source_dir" remote add origin https://github.com/tdlib/telegram-bot-api.git
git -C "$source_dir" fetch --depth=1 origin "$TELEGRAM_BOT_API_COMMIT"
git -C "$source_dir" checkout --detach FETCH_HEAD
test "$(git -C "$source_dir" rev-parse HEAD)" = "$TELEGRAM_BOT_API_COMMIT"
git -C "$source_dir" submodule update --init --recursive --depth=1

cmake -S "$source_dir" -B "$build_dir" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_COMPILER=clang++ -DCMAKE_C_COMPILER=clang \
    -DBUILD_SHARED_LIBS=OFF
cmake --build "$build_dir" --target telegram-bot-api --parallel 2
install -D -m 755 "$build_dir/telegram-bot-api" "$binary_dir/telegram-bot-api"
