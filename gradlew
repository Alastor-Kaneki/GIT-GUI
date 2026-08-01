#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_DIR="$APP_HOME/gradle/wrapper"
CLASSPATH="$WRAPPER_DIR/gradle-wrapper.jar"
JAR_URL="https://services.gradle.org/distributions/gradle-8.13-wrapper.jar"
SHA_URL="$JAR_URL.sha256"

if [ ! -f "$CLASSPATH" ]; then
    mkdir -p "$WRAPPER_DIR"
    if command -v curl >/dev/null 2>&1; then
        curl -fL "$JAR_URL" -o "$CLASSPATH"
        EXPECTED=$(curl -fsL "$SHA_URL" | tr -d '\r\n ')
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$CLASSPATH" "$JAR_URL"
        EXPECTED=$(wget -qO- "$SHA_URL" | tr -d '\r\n ')
    else
        echo "curl or wget is required to bootstrap the Gradle wrapper." >&2
        exit 1
    fi
    if command -v sha256sum >/dev/null 2>&1 && [ -n "$EXPECTED" ]; then
        ACTUAL=$(sha256sum "$CLASSPATH" | awk '{print $1}')
        if [ "$ACTUAL" != "$EXPECTED" ]; then
            rm -f "$CLASSPATH"
            echo "Gradle wrapper checksum verification failed." >&2
            exit 1
        fi
    fi
fi

JAVA_CMD=${JAVA_HOME:+$JAVA_HOME/bin/}java
exec "$JAVA_CMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
