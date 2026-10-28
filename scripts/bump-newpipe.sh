#!/usr/bin/env bash
# Try a newer NewPipeExtractor: updates gradle/libs.versions.toml (version and release date), runs the offline and live tests of
# :extractor-newpipe, and prints a verdict. On a failing verdict the previous versions are restored.
#   scripts/bump-newpipe.sh            # newest release
#   scripts/bump-newpipe.sh v0.26.6    # a specific tag
set -euo pipefail
cd "$(dirname "$0")/.."
CATALOG=gradle/libs.versions.toml

current=$(sed -n 's/^newpipe-extractor = "\(v[0-9.]*\)"/\1/p' "$CATALOG")
tag=${1:-$(curl -fsS https://api.github.com/repos/TeamNewPipe/NewPipeExtractor/releases/latest | python3 -c 'import sys,json; print(json.load(sys.stdin)["tag_name"])')}
released=$(curl -fsS "https://api.github.com/repos/TeamNewPipe/NewPipeExtractor/releases/tags/$tag" | python3 -c 'import sys,json; print(json.load(sys.stdin)["published_at"][:10])')
echo "current: $current   candidate: $tag (released $released)"
if [ "$tag" = "$current" ]; then echo "VERDICT: already on $tag, nothing to do"; exit 0; fi

cp "$CATALOG" "$CATALOG.bak"
restore() { mv "$CATALOG.bak" "$CATALOG"; }
sed -i "s/^newpipe-extractor = \"v[0-9.]*\"/newpipe-extractor = \"$tag\"/" "$CATALOG"
sed -i "s/^newpipe-released = \"[0-9-]*\"/newpipe-released = \"$released\"/" "$CATALOG" # the version and its date are generated into the app (:shared:generateVersions)

if ./gradlew :extractor-newpipe:test && ./gradlew :extractor-newpipe:liveTest; then
  rm "$CATALOG.bak"
  echo "VERDICT: $tag passes the offline and live tests. Review the diff, run ./gradlew allTests, then commit."
else
  restore
  echo "VERDICT: $tag FAILED; the catalog was restored to $current."
  exit 1
fi
