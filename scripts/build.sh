#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
rm -rf build/classes
mkdir -p build/classes
sources=()
while IFS= read -r -d '' source; do
    sources+=("$source")
done < <(find src/main/java -name '*.java' -print0)
javac --release 17 -g -encoding UTF-8 -Xlint:all -Werror -d build/classes "${sources[@]}"
jar --create --file build/pim.jar --main-class app.Main -C build/classes .
echo 'Built build/pim.jar'
