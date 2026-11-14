#!/usr/bin/env bash
# Builds the signed release APK for an Android tag and checks it.
#
#   scripts/release-android.sh android-vX.Y.Z
#
# Needs a clean checkout of the tag, the release keystore (OCA_KEYSTORE, path
# to the .jks; it never goes into Git) and the Android SDK (ANDROID_HOME,
# default ~/Android/Sdk). apksigner asks for the keystore password on the
# terminal. Output in dist/: the signed APK and its SHA-256 sum.
#
# The signer certificate must match android/release-signing-cert.sha256;
# otherwise the APK is deleted and the script fails. OCA_KS_PASS (password in
# the environment) and OCA_EXPECTED_CERT_SHA256 exist only for testing the
# script with a throwaway keystore.
set -euo pipefail

usage() { echo "usage: $0 android-vX.Y.Z" >&2; exit 2; }
fail() { echo "error: $*" >&2; exit 1; }

[[ $# -eq 1 ]] || usage
tag=$1
[[ $tag =~ ^android-v([0-9]+\.[0-9]+\.[0-9]+)$ ]] || fail "tag must look like android-vX.Y.Z"
version=${BASH_REMATCH[1]}

root=$(git rev-parse --show-toplevel)
cd "$root"

# The APK must come from exactly the tagged sources.
[[ -z $(git status --porcelain) ]] || fail "working tree is not clean"
tag_commit=$(git rev-parse -q --verify "refs/tags/$tag^{commit}") || fail "tag $tag does not exist locally (git fetch --tags)"
[[ $(git rev-parse HEAD) == "$tag_commit" ]] || fail "HEAD is not $tag (git checkout $tag)"

gradle_file=android/app/build.gradle.kts
version_name=$(sed -nE 's/^ *versionName = "([^"]+)".*/\1/p' "$gradle_file")
version_code=$(sed -nE 's/^ *versionCode = ([0-9]+).*/\1/p' "$gradle_file")
[[ $version_name == "$version" ]] || fail "versionName is $version_name, the tag says $version"
changelog=fastlane/metadata/android/en-US/changelogs/$version_code.txt
[[ -f $changelog ]] || fail "missing $changelog for versionCode $version_code"

[[ -n ${OCA_KEYSTORE:-} ]] || fail "set OCA_KEYSTORE to the path of the release keystore"
[[ -f $OCA_KEYSTORE ]] || fail "keystore not found: $OCA_KEYSTORE"
sdk=${ANDROID_HOME:-$HOME/Android/Sdk}
build_tools=$(ls -d "$sdk"/build-tools/* 2>/dev/null | sort -V | tail -1)
[[ -x $build_tools/apksigner && -x $build_tools/zipalign ]] || fail "apksigner/zipalign not found under $sdk/build-tools"

expected=${OCA_EXPECTED_CERT_SHA256:-$(cat android/release-signing-cert.sha256)}
expected=$(tr -d ':[:space:]' <<<"$expected" | tr 'A-F' 'a-f')

# Release build with the default (production) endpoints; assembleRelease also
# runs checkReleaseHasNoScanHook.
(cd android && ./gradlew --no-daemon clean assembleRelease)
forbidden=$(cd android && ./gradlew --no-daemon -q :app:dependencies --configuration releaseRuntimeClasspath |
  grep -E 'com\.google\.android\.gms|com\.google\.firebase|com\.google\.mlkit|com\.google\.android\.play' || true)
[[ -z $forbidden ]] || fail "forbidden dependencies in the release runtime: $forbidden"

unsigned=android/app/build/outputs/apk/release/app-release-unsigned.apk
mkdir -p dist
apk=dist/oneclickalias-$version.apk
rm -f "$apk" "$apk.sha256"
"$build_tools/zipalign" -f -P 16 4 "$unsigned" "$apk.aligned"
pass_args=()
[[ -n ${OCA_KS_PASS:-} ]] && pass_args=(--ks-pass env:OCA_KS_PASS)
"$build_tools/apksigner" sign --ks "$OCA_KEYSTORE" --ks-key-alias oneclickalias "${pass_args[@]}" \
  --out "$apk" "$apk.aligned"
rm -f "$apk.aligned" "$apk.idsig"

actual=$("$build_tools/apksigner" verify --print-certs "$apk" |
  sed -nE 's/^Signer #1 certificate SHA-256 digest: ([0-9a-f]+)$/\1/p')
if [[ $actual != "$expected" ]]; then
  rm -f "$apk"
  fail "APK is signed with $actual, expected $expected (wrong keystore?)"
fi
"$build_tools/apksigner" verify "$apk"
(cd dist && sha256sum "$(basename "$apk")" >"$(basename "$apk").sha256")
echo "signed: $apk (versionCode $version_code)"
cat "$apk.sha256"
