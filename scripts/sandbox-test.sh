#!/bin/sh

set -eu

SCRIPT_DIR=$(cd -P "$(dirname "$0")" >/dev/null 2>&1 && pwd)
APP_HOME=$(cd -P "$SCRIPT_DIR/.." >/dev/null 2>&1 && pwd)
SANDBOX_DIR="$APP_HOME/.sandbox-build"
MAIN_OUT="$SANDBOX_DIR/main"
MAIN_RES_OUT="$SANDBOX_DIR/resources-main"
TEST_OUT="$SANDBOX_DIR/test"
TEST_RES_OUT="$SANDBOX_DIR/resources-test"
RUNNER_OUT="$SANDBOX_DIR/runner"
JAVA_VERSION=$(sed -n 's/^java_version=//p' "$APP_HOME/gradle.properties" | head -n 1)

if [ -n "${JAVA_TOOL_OPTIONS:-}" ]; then
    export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djava.awt.headless=true"
else
    export JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"
fi

export SANDBOX_TEST_EXCLUDES="com.interactivedisplay.schema.RemoteImageCacheTest"

"$SCRIPT_DIR/sandbox-build.sh" >/dev/null

rm -rf "$TEST_OUT" "$TEST_RES_OUT" "$RUNNER_OUT"
mkdir -p "$TEST_OUT" "$TEST_RES_OUT" "$RUNNER_OUT"

TEST_CP=$(cat "$SANDBOX_DIR/classpath-main.txt"):"$MAIN_OUT":"$MAIN_RES_OUT"

javac --release "$JAVA_VERSION" -proc:none \
    -cp "$TEST_CP" \
    -d "$TEST_OUT" \
    $(find "$APP_HOME/src/test/java" -type f -name '*.java' | sort | tr '\n' ' ')

if [ -d "$APP_HOME/src/test/resources" ]; then
    cp -R "$APP_HOME/src/test/resources"/. "$TEST_RES_OUT"/
    TEST_CP="$TEST_CP:$TEST_RES_OUT"
fi

javac --release "$JAVA_VERSION" -proc:none \
    -cp "$TEST_CP:$TEST_OUT" \
    -d "$RUNNER_OUT" \
    "$APP_HOME/tools/sandbox-test-runner/src/com/interactivedisplay/tools/SandboxTestRunner.java"

java -cp "$TEST_CP:$TEST_OUT:$RUNNER_OUT" com.interactivedisplay.tools.SandboxTestRunner "$TEST_OUT"
