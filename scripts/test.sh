#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
./scripts/build.sh
junit_classpath='lib/test/junit-4.13.1.jar:lib/test/hamcrest-core-1.3.jar'
test_classpath="build/classes:build/test-classes:$junit_classpath"
rm -rf build/test-classes
mkdir -p build/test-classes
find src/test/java -name '*.java' -print0 |
    xargs -0 javac --release 17 -g -encoding UTF-8 -Xlint:all -Werror \
        -cp "$test_classpath" --source-path src/test/java -d build/test-classes

echo 'Model unit tests'
java "$@" -cp "$test_classpath" org.junit.runner.JUnitCore \
    tests.RecordTests tests.CriteriaTests tests.StorageTests

echo 'CLI tests'
java -cp "$test_classpath" org.junit.runner.JUnitCore tests.ConsoleTests
