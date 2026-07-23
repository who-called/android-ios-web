#!/usr/bin/env sh
# Fetch the latest FR warm-up artifacts (published from prod by CI) into the app
# source trees, so a build embeds the real spam DB without a local Postgres.
# Run before building the apps:  ./scripts/fetch-warmup.sh
#
# Requires `gh` authenticated with repo read access (keyring works; a no-scope
# GITHUB_TOKEN env shadows it — unset it if `gh` 404s).
set -eu

REPO="${WARMUP_REPO:-xsmrzx/who-called}"
TAG="${WARMUP_TAG:-warmup-latest}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

ANDROID_ASSETS="$ROOT/android/app/src/main/assets"
IOS_RES="$ROOT/ios/WhoCalled/Resources"
mkdir -p "$ANDROID_ASSETS" "$IOS_RES"

tmp="$(mktemp -d)"
echo "[fetch-warmup] downloading $TAG from $REPO"
gh release download "$TAG" -R "$REPO" \
  -p warmup.sqlite -p warmup_ios.json -p manifest.json \
  -D "$tmp" --clobber

cp "$tmp/warmup.sqlite" "$ANDROID_ASSETS/warmup.sqlite"
cp "$tmp/manifest.json" "$ANDROID_ASSETS/manifest.json"
cp "$tmp/warmup_ios.json" "$IOS_RES/warmup_ios.json"
rm -rf "$tmp"

echo "[fetch-warmup] Android  → $ANDROID_ASSETS/warmup.sqlite"
echo "[fetch-warmup] iOS      → $IOS_RES/warmup_ios.json"
echo "[fetch-warmup] done"
