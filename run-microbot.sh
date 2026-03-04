#!/bin/bash
# Microbot launcher script
# This script launches the latest built microbot jar

# Fix snap GTK library conflicts by unsetting snap-related environment variables
unset GTK_PATH
unset GTK_EXE_PREFIX
unset GTK_IM_MODULE_FILE
unset GIO_MODULE_DIR
unset LIBGL_DRIVERS_PATH

# Change to the build libs directory
cd "$(dirname "$0")/runelite-client/build/libs" || {
    echo "Error: build/libs directory not found. Have you built the project?"
    exit 1
}

# Find the microbot jar
JAR=$(ls -t microbot-*.jar 2>/dev/null | head -1)

if [ -z "$JAR" ]; then
    echo "Error: No microbot jar found in build/libs/"
    echo "Run './gradlew :client:shadowJar :client:microbotReleaseJar' to build it"
    exit 1
fi

echo "Launching $JAR..."
exec java -jar "$JAR" "$@"
