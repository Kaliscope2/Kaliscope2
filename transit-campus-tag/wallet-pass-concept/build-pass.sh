#!/usr/bin/env bash
# Packages pass-source/ into a signed .pkpass, installable in Apple Wallet.
#
# You need three things this script cannot supply, because they're tied to
# YOUR Apple Developer account (paid membership required):
#   1. A Pass Type ID registered at developer.apple.com -> Identifiers, and
#      its Pass Type ID certificate, exported from Keychain Access as a
#      .p12, then converted to PEM (see README.md in this folder).
#   2. Apple's WWDR (Worldwide Developer Relations) intermediate
#      certificate, in PEM form.
#   3. pass-source/pass.json's "teamIdentifier" changed from the
#      placeholder to your real 10-character Team ID, and
#      "passTypeIdentifier" matching the identifier you registered.
#
# Usage:
#   ./build-pass.sh <pass-cert.pem> <pass-key.pem> <wwdr.pem> [key-password]
#
# Output: build/TagDemoConcept.pkpass

set -euo pipefail

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <pass-cert.pem> <pass-key.pem> <wwdr.pem> [key-password]" >&2
  exit 1
fi

CERT="$1"
KEY="$2"
WWDR="$3"
KEY_PASS="${4:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$SCRIPT_DIR/pass-source"
BUILD_DIR="$SCRIPT_DIR/build"
STAGE_DIR="$BUILD_DIR/TagDemoConcept.pass"
OUTPUT="$BUILD_DIR/TagDemoConcept.pkpass"

if grep -q "REPLACE_WITH_YOUR_APPLE_TEAM_ID" "$SOURCE_DIR/pass.json"; then
  echo "warning: pass.json still has the placeholder teamIdentifier — Wallet will reject the pass until you set your real Team ID." >&2
fi

rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR"
cp "$SOURCE_DIR"/*.json "$SOURCE_DIR"/*.png "$STAGE_DIR/"

# --- manifest.json: sha1 of every file in the pass ---
python3 - "$STAGE_DIR" <<'PY'
import hashlib, json, os, sys

stage = sys.argv[1]
manifest = {}
for name in sorted(os.listdir(stage)):
    path = os.path.join(stage, name)
    if not os.path.isfile(path):
        continue
    with open(path, "rb") as f:
        manifest[name] = hashlib.sha1(f.read()).hexdigest()

with open(os.path.join(stage, "manifest.json"), "w") as f:
    json.dump(manifest, f)
PY

# --- signature: detached PKCS#7 over manifest.json ---
SIGN_ARGS=(-binary -sign -certfile "$WWDR" -signer "$CERT" -inkey "$KEY"
           -in "$STAGE_DIR/manifest.json" -out "$STAGE_DIR/signature" -outform DER)
if [ -n "$KEY_PASS" ]; then
  SIGN_ARGS+=(-passin "pass:$KEY_PASS")
fi
openssl smime "${SIGN_ARGS[@]}"

# --- zip (files at the archive root, not inside a folder) ---
rm -f "$OUTPUT"
(cd "$STAGE_DIR" && zip -X -q -r "$OUTPUT" .)

echo "Built $OUTPUT"
echo "AirDrop it to an iPhone, or serve it over HTTPS with content-type application/vnd.apple.pkpass, to install it in Wallet."
