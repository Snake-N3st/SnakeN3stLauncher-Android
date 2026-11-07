#!/usr/bin/env bash
# Compatibility shim: Java runtimes are now installed on demand, never bundled in the APK.
set -euo pipefail
printf '%s\n' 'No bundled Java preparation is needed. The launcher downloads the required runtime when installing a modpack.'
