#!/bin/sh
# Build the brook Go core + TUN-to-SOCKS5 bridge into an Android AAR
# (arm, arm64, x86, x86_64) consumed by app/.
#
# Requires: Go >= 1.24 (or GOTOOLCHAIN=auto), gomobile + gobind in PATH,
# Android NDK. On restricted networks Go downloads honor HTTPS_PROXY.
set -e

cd "$(dirname "$0")"

ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$HOME/Library/Android/sdk/ndk/27.2.12479018}"
export ANDROID_NDK_HOME

: "${GOTOOLCHAIN:=auto}"
export GOTOOLCHAIN
export HTTPS_PROXY="${HTTPS_PROXY:-socks5://127.0.0.1:1080}"
export HTTP_PROXY="${HTTP_PROXY:-socks5://127.0.0.1:1080}"

mkdir -p app/libs
(cd mobile && go mod tidy && gomobile bind \
	-target=android -androidapi 24 \
	-ldflags="-s -w" \
	-o ../app/libs/brook.aar \
	.)

ls -lh app/libs/brook.aar
