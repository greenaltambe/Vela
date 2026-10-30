#!/usr/bin/env bash
# Creates the Android release keystore OUTSIDE the repository and prints how to give it to CI.
#   scripts/generate-keystore.sh [path]      default path: ~/vela-release.keystore
# Interactive: keytool asks for the passwords and your details. Nothing is written inside the repository.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
out="${1:-$HOME/vela-release.keystore}"
case "$(cd "$(dirname "$out")" && pwd)/$(basename "$out")" in
  "$here"/*) echo "Refusing to write the keystore inside the repository ($here). Pick a path outside it." >&2; exit 1 ;;
esac
if [ -e "$out" ]; then echo "$out already exists; refusing to overwrite it." >&2; exit 1; fi
alias_name="${ANDROID_KEY_ALIAS:-vela}"
keytool -genkeypair -v -keystore "$out" -alias "$alias_name" -keyalg RSA -keysize 4096 -validity 10000
chmod 600 "$out"
cat <<MSG

Created $out (alias: $alias_name).

BACK IT UP NOW, in two separate places (for example an encrypted drive and a password manager attachment).
If you lose this file or its passwords, users can never update the app: Android refuses updates signed with a different key.

Give it to GitHub Actions (repository Settings > Secrets and variables > Actions):
  ANDROID_KEYSTORE_BASE64    $(printf 'base64 -w0 %q' "$out")      # run that command and paste its output
  ANDROID_KEYSTORE_PASSWORD  the store password you just typed
  ANDROID_KEY_ALIAS          $alias_name
  ANDROID_KEY_PASSWORD       the key password you just typed

For a local signed build, create keystore.properties in the repository root (it is git-ignored):
  storeFile=$out
  storePassword=...
  keyAlias=$alias_name
  keyPassword=...
MSG
