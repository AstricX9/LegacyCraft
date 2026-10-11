#!/bin/sh
# Starts the LegacyCraft launcher. Uses the Java in runtime/ when there is one, else JAVA_HOME or the one on PATH.
. "$(cd "$(dirname "$0")" && pwd)/lib/find-java.sh"
exec "$JAVA" -jar "$ROOT/launcher/LegacyCraftLauncher.jar" "$@"
