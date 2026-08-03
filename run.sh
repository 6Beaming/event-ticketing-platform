#!/usr/bin/env sh
# Compile and run the complete MyTix Java application with one command
set -eu

BUILD_DIR=".build/classes"
SOURCE_LIST=".build/sources.list"
CONNECTOR_PATH="lib/mysql-connector-java-8.0.29.jar"

if [ ! -f "$CONNECTOR_PATH" ]; then
    echo "Missing MySQL driver: lib/mysql-connector-java-8.0.29.jar" >&2
    exit 1
fi

# OS check: if Windows shell environment, sets the Java classpath separator to ;
# for any other OS (Linux, macOS), sets it to :
case "$(uname -s 2>/dev/null || true)" in
    CYGWIN*|MINGW*|MSYS*) CLASSPATH_SEPARATOR=';' ;;
    *) CLASSPATH_SEPARATOR=':' ;;
esac

mkdir -p "$BUILD_DIR"
find "$BUILD_DIR" -type f -name '*.class' -delete
find src -type f -name '*.java' -print | sort > "$SOURCE_LIST"

if [ ! -s "$SOURCE_LIST" ]; then
    echo "No Java source files were found under src/." >&2
    exit 1
fi

javac -cp "$CONNECTOR_PATH" -d "$BUILD_DIR" @"$SOURCE_LIST"

case "${1:-}" in
    --generate-data)
        java -cp "$BUILD_DIR$CLASSPATH_SEPARATOR$CONNECTOR_PATH" data.DevelopmentDataGenerator
        exit 0
        ;;
    --self-test)
        java -cp "$BUILD_DIR$CLASSPATH_SEPARATOR$CONNECTOR_PATH" testing.FoundationSelfTest
        exit 0
        ;;
    --database-check)
        java -cp "$BUILD_DIR$CLASSPATH_SEPARATOR$CONNECTOR_PATH" testing.FoundationDatabaseCheck --reset-database
        exit 0
        ;;
esac

java -cp "$BUILD_DIR$CLASSPATH_SEPARATOR$CONNECTOR_PATH" Main
