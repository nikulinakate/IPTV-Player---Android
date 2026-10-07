#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 Scripts/check-localization.py
bash Scripts/test-core.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug
