#!/bin/sh

set -eu

SCRIPT_DIR=$(cd -P "$(dirname "$0")" >/dev/null 2>&1 && pwd)
APP_HOME=$(cd -P "$SCRIPT_DIR/.." >/dev/null 2>&1 && pwd)
SANDBOX_DIR="$APP_HOME/.sandbox-build"
MAIN_OUT="$SANDBOX_DIR/main"
MAIN_RES_OUT="$SANDBOX_DIR/resources-main"
MAIN_CP_PARTS="$SANDBOX_DIR/classpath-main.parts"
MAIN_CP_FILE="$SANDBOX_DIR/classpath-main.txt"
JAVA_VERSION=$(sed -n 's/^java_version=//p' "$APP_HOME/gradle.properties" | head -n 1)
MINECRAFT_VERSION=$(sed -n 's/^minecraft_version=//p' "$APP_HOME/gradle.properties" | head -n 1)
NAMED_MINECRAFT_JAR=$(find "$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged" -type f -name "*${MINECRAFT_VERSION}*.jar" | head -n 1)

if [ -z "$JAVA_VERSION" ] || [ -z "$MINECRAFT_VERSION" ]; then
    echo "gradle.properties에서 java_version 또는 minecraft_version을 찾지 못함" >&2
    exit 1
fi

if [ -z "$NAMED_MINECRAFT_JAR" ]; then
    echo "named minecraft jar를 찾지 못함: $MINECRAFT_VERSION" >&2
    exit 1
fi

if [ ! -d "$APP_HOME/.gradle/loom-cache/remapped_mods/remapped" ] || [ ! -d "$APP_HOME/.gradle/caches/modules-2/files-2.1" ]; then
    echo "샌드박스 빌드 캐시가 없음. 먼저 권한 있는 환경에서 Gradle 빌드 1회 필요" >&2
    exit 1
fi

mkdir -p "$SANDBOX_DIR"
rm -rf "$MAIN_OUT" "$MAIN_RES_OUT"
mkdir -p "$MAIN_OUT" "$MAIN_RES_OUT"

find "$APP_HOME/.gradle/loom-cache/remapped_mods/remapped" -type f -name '*.jar' ! -name '*-sources.jar' | sort > "$MAIN_CP_PARTS"
find "$APP_HOME/.gradle/caches/modules-2/files-2.1" -type f -name '*.jar' ! -name '*-sources.jar' | sort >> "$MAIN_CP_PARTS"
printf '%s\n' "$NAMED_MINECRAFT_JAR" >> "$MAIN_CP_PARTS"
paste -sd: "$MAIN_CP_PARTS" > "$MAIN_CP_FILE"

javac --release "$JAVA_VERSION" -proc:none \
    -cp "$(cat "$MAIN_CP_FILE")" \
    -d "$MAIN_OUT" \
    $(find "$APP_HOME/src/main/java" -type f -name '*.java' | sort | tr '\n' ' ')

if [ -d "$APP_HOME/src/main/resources" ]; then
    cp -R "$APP_HOME/src/main/resources"/. "$MAIN_RES_OUT"/
fi

echo "sandbox build ok"
