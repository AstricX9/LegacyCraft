LegacyCraft for Windows (Windows 7 SP1 or later)
=================================================

This folder is the game and the launcher, already built. Nothing needs
compiling, and Gradle and the source code are not needed.

1. Java 17
----------
LegacyCraft needs Java 17. The easiest way is to put a copy of Java in
this folder, so nothing has to be installed:

  a. On https://adoptium.net/temurin/releases/?version=17 choose
       Operating system: Windows
       Architecture:     x64  (or x86 on 32-bit Windows 7)
       Package type:     JRE, the .zip (not the .msi)
     To see which Windows you have: Start, right-click Computer,
     Properties, "System type".
  b. Unzip it here and rename the folder it makes (like
     "jdk-17.0.x+y-jre") to "runtime", so that this file exists:
       runtime\bin\javaw.exe

If Java 17 or newer is already installed, that works too and runtime\
can be left out.

Windows 7 needs Service Pack 1 and its updates (Java 17 relies on them).
Java 17 isn't officially supported on Windows 7 by its makers, but it
generally runs there.

2. Play
-------
  LegacyCraft Launcher.bat   the launcher: instances, skins, worlds,
                             Additions. Press Play.
  Play LegacyCraft.bat       the game straight away, without the launcher
                             (saves go in the "game" folder here).

The launcher keeps its settings and instances in
C:\Users\<you>\.legacycraft-launcher, like on any other PC.

3. If the game won't start
--------------------------
  - "needs Java 17": see step 1.
  - The window doesn't open, or there is an OpenGL error: update the
    graphics driver from the maker (NVIDIA, AMD or Intel). The drivers
    Windows 7 installs by itself often have no proper OpenGL.
  - Out of memory on 32-bit Windows: the launcher and Play LegacyCraft.bat
    already use 1 GB there; in the launcher, set the instance's memory
    to 1024 MB or less if it was changed.
  - The launcher's "Launcher Log" tab shows what the game printed.
