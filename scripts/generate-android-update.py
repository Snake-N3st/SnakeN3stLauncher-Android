#!/usr/bin/env python3
"""Create schema-1 Android update metadata from actual APK manifests and bytes.

No version command-line options are accepted: application ID, versions and minSdk
come exclusively from aapt dump badging. The APK URL is confined to this project's
GitHub release assets. This script does not publish, install or run the APKs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.parse import quote

REPOSITORY = "Snake-N3st/SnakeN3stLauncher-Android"
RELEASE_PREFIX = f"https://github.com/{REPOSITORY}/releases/download/"
APPLICATION_IDS = {"mc.snakenest.launcher.android", "mc.snakenest.launcher.android.debug"}
MAX_VERSION_CODE = 2_100_000_000


def aapt_path(explicit: str | None) -> str:
    if explicit:
        binary = Path(explicit).expanduser().resolve()
        if not binary.is_file():
            raise ValueError("The specified aapt binary does not exist")
        return str(binary)
    installed = shutil.which("aapt")
    if installed:
        return installed
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        candidates = list((Path(sdk) / "build-tools").glob("*/aapt"))
        candidates = [candidate for candidate in candidates if candidate.is_file()]
        if candidates:
            def version(candidate: Path) -> tuple[int, ...]:
                return tuple(int(number) for number in re.findall(r"[0-9]+", candidate.parent.name))
            return str(max(candidates, key=version))
    raise ValueError("aapt is required: use --aapt or set ANDROID_HOME")


def variant(binary: str, apk: Path, tag: str) -> dict:
    apk = apk.resolve(strict=True)
    if not apk.is_file() or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*\.apk", apk.name):
        raise ValueError("Each APK must be a regular .apk file with a safe asset filename")
    before = apk.stat()
    result = subprocess.run([binary, "dump", "badging", str(apk)], check=True,
                            capture_output=True, text=True, encoding="utf-8", timeout=60)
    package_line = next((line for line in result.stdout.splitlines() if line.startswith("package:")), None)
    if package_line is None:
        raise ValueError(f"APK manifest could not be read: {apk.name}")
    fields = dict(re.findall(r"([A-Za-z][A-Za-z0-9]*)='([^']*)'", package_line))
    application = fields.get("name")
    if application not in APPLICATION_IDS:
        raise ValueError(f"Unexpected APK application ID: {apk.name}")
    code_text = fields.get("versionCode", "")
    if not re.fullmatch(r"[1-9][0-9]{0,9}", code_text):
        raise ValueError(f"Invalid APK versionCode: {apk.name}")
    code = int(code_text)
    if code > MAX_VERSION_CODE:
        raise ValueError(f"APK versionCode exceeds Android distribution limits: {apk.name}")
    name = fields.get("versionName", "")
    if not name or len(name) > 80:
        raise ValueError(f"Missing or excessively long APK versionName: {apk.name}")
    minimum = re.search(r"^sdkVersion:'([0-9]+)'$", result.stdout, re.MULTILINE)
    if minimum is None or not 26 <= int(minimum.group(1)) <= 100:
        raise ValueError(f"Invalid APK minimum SDK: {apk.name}")
    digest = hashlib.sha256()
    size = 0
    with apk.open("rb") as stream:
        while block := stream.read(1024 * 1024):
            digest.update(block)
            size += len(block)
    after = apk.stat()
    if not 0 < size <= 512 * 1024 * 1024 or size != before.st_size or before.st_size != after.st_size or before.st_mtime_ns != after.st_mtime_ns:
        raise ValueError(f"APK changed during metadata generation: {apk.name}")
    return {
        "applicationId": application,
        "versionCode": code,
        "versionName": name,
        "apkUrl": RELEASE_PREFIX + quote(tag, safe="") + "/" + quote(apk.name, safe=""),
        "sha256": digest.hexdigest(),
        "size": size,
        "minSdk": int(minimum.group(1)),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True, help="Existing GitHub release tag, e.g. v0.2.0")
    parser.add_argument("--aapt", help="Optional Android SDK aapt executable")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--notes-file", type=Path, help="Optional UTF-8 release notes, at most 16000 characters")
    parser.add_argument("apk", nargs="+", type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"v[0-9A-Za-z][0-9A-Za-z.+_-]{0,100}", args.tag):
        parser.error("--tag must be a safe version tag beginning with v")
    try:
        binary = aapt_path(args.aapt)
        variants = [variant(binary, apk, args.tag) for apk in args.apk]
        if len({item["applicationId"] for item in variants}) != len(variants):
            raise ValueError("Only one APK per application ID is allowed")
        metadata = {"schema": 1, "variants": sorted(variants, key=lambda item: item["applicationId"])}
        if args.notes_file:
            notes = args.notes_file.read_text(encoding="utf-8")
            if len(notes) > 16000:
                raise ValueError("Release notes exceed 16000 characters")
            if notes.strip():
                metadata["releaseNotes"] = notes
        output = args.output.resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=output.parent,
                                             prefix=output.name + ".", suffix=".tmp", delete=False) as stream:
                temporary = Path(stream.name)
                json.dump(metadata, stream, ensure_ascii=False, indent=2)
                stream.write("\n")
            temporary.replace(output)
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
