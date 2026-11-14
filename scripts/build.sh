#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
rm -rf build/classes
mkdir -p build/classes
find src/main/java -name '*.java' -print0 |
    xargs -0 javac --release 17 -g -encoding UTF-8 -Xlint:all -Werror \
        -cp build/classes --source-path src/main/java -d build/classes
jar --create --file build/pim.jar --main-class app.Main -C build/classes .
echo 'Built build/pim.jar'
