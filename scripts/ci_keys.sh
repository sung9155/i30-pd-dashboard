#!/usr/bin/env bash
# The public AOSP platform *test* key (android-4.2.2_r1), the one the i30 PD head unit accepts for platform apps
# (certificate sha256 c8a2e9bc...; release.py checks it after signing). Fetched with pinned SHA-256s into $1, for CI.
# Same source and hashes as upstream reikop/veloster-n-dashboard scripts/ci_tools.sh.
set -euo pipefail
OUT="${1:?usage: ci_keys.sh DIR}"; mkdir -p "$OUT"; cd "$OUT"
sha() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
get() {  # url file sha256
  [ -f "$2" ] && [ "$(sha "$2")" = "$3" ] && return 0
  curl -fsSL --retry 4 --retry-all-errors --retry-delay 5 "$1" | base64 -d > "$2"
  [ "$(sha "$2")" = "$3" ] || { echo "SHA-256 mismatch: $2" >&2; exit 1; }
}
AOSP=https://android.googlesource.com/platform/build/+/android-4.2.2_r1/target/product/security
get "$AOSP/platform.pk8?format=TEXT" platform.pk8 1ad8ef556870edb70f69a9d3c112544c07de5162ba440d84d33f8bb0c5962875
get "$AOSP/platform.x509.pem?format=TEXT" platform.x509.pem 9837de028f460c35cc8d3fa45f14eecce30f6fbfe4b93d399aef1acb80c20d14
echo "platform test key ready in $OUT"
