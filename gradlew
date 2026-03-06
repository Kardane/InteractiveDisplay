#!/bin/sh

# Minimal Gradle wrapper launcher for POSIX environments.

# Resolve APP_HOME from script location, following symlinks.
app_path=$0
while APP_HOME=${app_path%"${app_path##*/}"} && [ -h "$app_path" ]; do
    ls_output=$(ls -ld -- "$app_path")
    link=${ls_output#*' -> '}
    case $link in
        /*) app_path=$link ;;
        *) app_path=$APP_HOME$link ;;
    esac
done

APP_BASE_NAME=${0##*/}
APP_HOME=$(cd -P "${APP_HOME:-./}" >/dev/null && printf '%s\n' "$PWD") || exit 1
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -z "$GRADLE_USER_HOME" ]; then
    GRADLE_USER_HOME="$APP_HOME/.gradle"
    export GRADLE_USER_HOME
fi

if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD=java
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: JAVA_HOME is not set and no java command could be found in your PATH." >&2
    exit 1
fi

DEFAULT_JVM_OPTS='-Xmx64m -Xms64m'

exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS \
    $JAVA_OPTS \
    $GRADLE_OPTS \
    "-Dorg.gradle.appName=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
