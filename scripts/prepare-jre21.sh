#!/usr/bin/env bash
# Official Amethyst Java 21 release, repacked with its upstream binpack script.
set -euo pipefail
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
input="$work/input"
output="$work/output"
mkdir -p "$input"
release='https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre21'
upstream_commit='4527b5a73dcf3f890b45eab4c6a91651ea28a5ea'
for arch in arm arm64 x86 x86_64; do
    archive="jre21-android-$arch.tar.xz"
    if [[ -n ${SN3_JRE_DOWNLOAD_DIR:-} && -f "$SN3_JRE_DOWNLOAD_DIR/$archive" ]]; then
        cp "$SN3_JRE_DOWNLOAD_DIR/$archive" "$input/$archive"
    else
        curl --fail --location --retry 3 "$release/$archive" --output "$input/$archive"
    fi
done
(cd "$input" && sha256sum --check <<'HASHES'
96c297487def64666e379a9a363d9955c05b1a0b091b0cf24af88359a66f394a  jre21-android-arm.tar.xz
8d41ec401ee59f7722df60ed991f81ad146e130452804bfdd8a05d3436f7bbfe  jre21-android-arm64.tar.xz
9b8c7d10c5f751acb3b33506593da44ece52a0fd03e0b3c283ba08a7f285a40f  jre21-android-x86.tar.xz
cb88723961f5f9ad63afa1f212eb199816c27cabfd7dc66567bde1d8fb69713b  jre21-android-x86_64.tar.xz
HASHES
)
# The upstream repacker expects the build artifact's original release suffix.
for arch in arm arm64 x86 x86_64; do
    mv "$input/jre21-android-$arch.tar.xz" "$input/jre21-android-$arch-release.tar.xz"
done
curl --fail --location --retry 3 \
    "https://raw.githubusercontent.com/AngelAuraMC/angelauramc-openjdk-build/$upstream_commit/9_repackjre.sh" \
    --output "$work/repack.sh"
printf '%s  %s\n' '602831cac02b76c801309bcc63a672c036c8b236c09f83b69f82c466f87ce036' "$work/repack.sh" | sha256sum --check
# Recompression is intentionally run at minimum CPU priority, like builds.
TARGET_VERSION=21 GITHUB_SHA="release-jre21-$upstream_commit" nice -n 19 bash "$work/repack.sh" "$input" "$output"
for arch in arm arm64 x86 x86_64; do
    tar -xOf "$output/bin-$arch.tar.xz" ./release | grep -q '^JAVA_VERSION="21[."]'
done
components="$repo_root/app_pojavlauncher/src/main/assets/components"
# Publish only after all archives are verified. Never touch installed user runtimes.
rm -rf "$components/jre-21"
mv "$output" "$components/jre-21"
rm -rf "$components/jre" "$components/jre-new" "$components/jre-25"
printf 'Bundled Java 21 ready: %s\n' "$components/jre-21"
