#!/bin/sh
# Plays LegacyCraft straight away, without the launcher. Saves and options go in the game folder next to this file.
# ./play.sh [player name]
. "$(cd "$(dirname "$0")" && pwd)/lib/find-java.sh"
exec "$JAVA" $JAVA_PLATFORM_ARGS -Xms512m -Xmx2g -cp "$ROOT/lib/*" net.minecraft.client.main.Main \
    "--gamedir=$ROOT/game" "--username=${1:-Player}"
