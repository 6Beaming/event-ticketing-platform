#!/usr/bin/env sh
# Compile and run the complete MyTix Java application with one command
set -eu

BUILD_DIR=".build/classes"
SOURCE_LIST=".build/sources.list"
CONNECTOR_PATH="lib/mysql-connector-java-8.0.29.jar"
LIB_PATH="lib/*"

if [ ! -f "lib/mysql-connector-java-8.0.29.jar" ]; then
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

javac -cp "$LIB_PATH" -d "$BUILD_DIR" @"$SOURCE_LIST"

# Optional development utilities. [We can remove them when delivery], except --generate-data
# --generate-data is useful, and the PDF explicitly recommends writing a sample-data generator.
case "${1:-}" in
    --generate-data)
        java -cp "$BUILD_DIR$CLASSPATH_SEPARATOR$LIB_PATH" data.DevelopmentDataGenerator
        exit 0
        ;;
    --self-test)
        # Runs quickly without MySQL and checks Java validation, transaction behavior, and calculations.
        java -cp "$BUILD_DIR$CLASSPATH_SEPARATOR$LIB_PATH" testing.FoundationSelfTest
        exit 0
        ;;
    --database-check)
        # It runs drop.sql -> schema.sql -> load.sql -> tests real JDBC operations
        java -cp "$BUILD_DIR$CLASSPATH_SEPARATOR$LIB_PATH" testing.FoundationDatabaseCheck --reset-database
        exit 0
        ;;
esac

java -cp "$BUILD_DIR$CLASSPATH_SEPARATOR$LIB_PATH" Main
