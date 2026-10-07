#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
temporary="$(mktemp -d)"
trap 'rm -rf "$temporary"' EXIT
find core/src/main/java core/src/test/java -name '*.java' > "$temporary/sources"
javac -encoding UTF-8 -d "$temporary/classes" @"$temporary/sources"
java -cp "$temporary/classes" com.smartiptv.core.CoreSelfTest
