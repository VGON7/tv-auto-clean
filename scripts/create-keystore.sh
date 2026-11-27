#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="$ROOT/keystore/release.keystore"
mkdir -p "$ROOT/keystore"

if [ -f "$KEYSTORE" ]; then
    echo "Keystore already exists: $KEYSTORE"
    echo "Delete it only if you intend to invalidate future app updates."
    exit 0
fi

keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias tvclean \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=TV Auto Clean, OU=Open Source, O=TV Auto Clean, C=CN"

echo
echo "Created: $KEYSTORE"
echo "Back it up privately. Never commit it to GitHub."
echo "For builds: export TV_CLEAN_STORE_PASS='your-password'"
