#!/usr/bin/env bash
# Containerized gradle build for cs3-bridge.
# NEVER run gradle directly on the VPS host (earlyoom policy) — this runs it
# inside a memory-caged container, then stages dist/ for the runtime image.
set -euo pipefail
cd "$(dirname "$0")"

MEM="${CSBUILD_MEM:-3200m}"
SWAP="${CSBUILD_SWAP:-6g}"

echo "==> building in container (mem=$MEM swap=$SWAP)"
docker run --rm --memory="$MEM" --memory-swap="$SWAP" --cpus=3 \
  -v "$PWD":/src -w /src \
  -e GRADLE_USER_HOME=/src/.gradle-home \
  eclipse-temurin:21-jdk-noble \
  ./gradlew --no-daemon :bridge:installDist

echo "==> staging dist/"
rm -rf dist
cp -r bridge/build/install/bridge dist
echo "==> done:"
ls -la dist/ | head
ls dist/lib | wc -l
