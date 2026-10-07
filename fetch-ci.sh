#!/usr/bin/env bash
# Pull the latest CI-built dist (GitHub release "build") and stage dist/ for the
# runtime image, then build the local container image. The heavy gradle build
# always runs in GitHub Actions — never on the VPS.
set -euo pipefail
cd "$(dirname "$0")"

REPO="${CSBRIDGE_REPO:-vgupta1192/cs3-bridge}"

url=$(curl -s --max-time 30 "https://api.github.com/repos/$REPO/releases/latest" | python3 -c "
import json,sys
r=json.load(sys.stdin)
assets=[a for a in r.get('assets',[]) if a['name'].startswith('cs3-bridge-dist')]
if not assets: sys.exit('no dist asset on latest release')
import re
assets.sort(key=lambda a: [int(x) if x.isdigit() else x for x in re.split(r'(\\d+)', a['name'])])
print(assets[-1]['browser_download_url'])
")
echo "==> downloading $url"
curl -sL --max-time 300 "$url" -o /tmp/csbridge-dist.zip

rm -rf dist dist-extract && mkdir -p dist dist-extract
unzip -qo /tmp/csbridge-dist.zip -d dist-extract
mv dist-extract/bridge/* dist/
rm -rf dist-extract

echo "==> building image stremio-stack/csbridge:local"
docker build -q -t stremio-stack/csbridge:local . >/dev/null
echo "==> ready: $(ls dist/lib | wc -l) jars in dist/lib"
