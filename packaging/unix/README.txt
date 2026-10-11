LegacyCraft for Linux and macOS
===============================

This folder is the game and the launcher, already built. Nothing needs
compiling, and Gradle and the source code are not needed.

1. Java 17
----------
LegacyCraft needs Java 17 or newer. Either install one (any Java 17+ on
PATH or in JAVA_HOME works), or put a copy in this folder:

  a. On https://adoptium.net/temurin/releases/?version=17 pick your
     system (Linux or macOS, x64 or aarch64 to match this package) and
     the JRE .tar.gz.
  b. Unpack it here and rename the folder it makes to "runtime", so
     that runtime/bin/java (Linux) or runtime/Contents/Home/bin/java
     (macOS) exists.

2. Play
-------
In a terminal, in this folder:

  ./launcher.sh        the launcher: instances, skins, worlds,
                       Additions. Press Play.
  ./play.sh [name]     the game straight away, without the launcher
                       (saves go in the "game" folder here).

On macOS, if the system refuses to run a downloaded file, run
  xattr -dr com.apple.quarantine .
in this folder once.

The launcher keeps its settings and instances in ~/.legacycraft-launcher.
