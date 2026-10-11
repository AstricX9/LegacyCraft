# Sourced by launcher.sh and play.sh. Sets JAVA to a Java 17 or newer:
# runtime/ next to LegacyCraft first, then JAVA_HOME, then whatever is on PATH.
ROOT="$(cd "$(dirname "$0")" && pwd)"
JAVA=""
for candidate in "$ROOT/runtime/bin/java" "$ROOT/runtime/Contents/Home/bin/java" "${JAVA_HOME:+$JAVA_HOME/bin/java}"; do
    if [ -n "$candidate" ] && [ -x "$candidate" ]; then JAVA="$candidate"; break; fi
done
if [ -z "$JAVA" ] && command -v java >/dev/null 2>&1; then JAVA="$(command -v java)"; fi

java_help() {
    echo
    echo "Download the Java 17 JRE for your system (https://adoptium.net/temurin/releases/?version=17),"
    echo "unpack it and rename its folder to \"runtime\" inside the LegacyCraft folder."
    echo "See README.txt for the details."
    exit 1
}

if [ -z "$JAVA" ]; then
    echo "LegacyCraft needs Java 17, and none was found."
    java_help
fi
JAVA_MAJOR="$("$JAVA" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1)"
if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 17 ]; then
    echo "LegacyCraft needs Java 17 or newer, but the Java found is older:"
    "$JAVA" -version
    java_help
fi

# GLFW on macOS has to run on the first thread.
JAVA_PLATFORM_ARGS=""
if [ "$(uname -s)" = "Darwin" ]; then JAVA_PLATFORM_ARGS="-XstartOnFirstThread"; fi
