#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash Scripts/test-core.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
