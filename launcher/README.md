# LegacyCraft Launcher

An offline launcher with an instance manager, in a flat black-and-white
style (square corners, grey section headers, plain underlined links, like
World of Mods). There's no account and no login. It manages instances, skins
and capes, and it starts the game.

Start it by double-clicking `run-launcher.bat` (it compiles the sources with
Java 17+ and opens the window). The Gradle project in this folder also works
in IDEs: `gradle run`.

## Window

The launcher draws its own title bar: the tab strip at the top is the title
bar (drag it to move the window, double-click it to maximise), with the window
buttons on the right. Dialogs and file choosers get the same flat title strip.
Windows 11 only rounds the corners of windows that have a system frame, so the
launcher's corners stay square there too. Drag any edge to resize.

## Tabs

- **News**: the news page. To write your own, copy
  `src/main/resources/net/legacycraft/launcher/news.html` to
  `%USERPROFILE%\.legacycraft-launcher\news.html` and edit the copy. Links
  starting with `launcher:` (`instances`, `newinstance`, `skins`, `console`,
  `gamedir`, `instancesdir`, `skinsdir`, `capesdir`, `datadir`) are handled by
  the launcher.
- **Instances**: the instance manager. Each instance is its own copy of the
  game with its own folder (worlds in `saves`, `resourcepacks`, `options.txt`,
  `servers.dat`) and its own settings. Click an instance to select it,
  double-click to play it. Each row has **Play**, **Edit**, **Copy**,
  **Folder** and **Delete**:
  - **New instance** makes an empty instance in
    `%USERPROFILE%\.legacycraft-launcher\instances\<name>`.
  - **Copy** makes a new instance with the same settings and copies the
    original's `saves`, `resourcepacks`, `options.txt` and `servers.dat`.
  - **Delete** removes the instance; for folders under `instances\` it offers
    to delete the folder too (off by default).
  - **Edit** sets the name, folder, window size, what the launcher does while
    the game runs, memory (1-8 GB, or the game's default), the Java used to
    run Gradle, and extra JVM arguments.
- **Skins & Capes**: a skin list and a cape list next to a 3D preview.
  Whatever is selected in each list is what you wear.
  - Add 64x64 skins, or old 64x32 skins (converted the way 1.8 does it).
  - Choose Classic or Slim arms for each skin.
  - Add capes in the 64x32 cape layout (or 128x64 and other 2:1 sizes).
    Only the cape part is used; the elytra area of newer capes is ignored.
  - Drag the preview to turn the player; double-click it to stop spinning.
  - Drop PNG files onto a list to add them.
- **Launcher Log**: the launcher log, plus the game's output prefixed
  with `Client>`.

## Bottom bar

- **Instance**: picks the instance Play starts, with **Edit**, **New**,
  **Folder** and **All instances** links under it.
- **Play** runs `gradlew run` in the LegacyCraft folder. It passes
  `--gamedir` (the instance's folder), `--username`, `--skin` (the chosen
  skin, converted to 64x64 and copied to
  `<instance folder>\.legacycraft-launcher\skin.png`), `--skinModel`, `--cape`
  (copied to `cape.png` there), and `--width`/`--height` when a window size is
  set.
- **Change name** sets the offline player name; **Change skin** opens Skins &
  Capes.

## Where things are stored

`%USERPROFILE%\.legacycraft-launcher\`:

- `launcher_profiles.json`: the instances, stored as profiles in the 1.6
  launcher's format (plus `memoryMb` and `lastPlayed`), and a `legacycraft`
  section with the player name, the install folder and the skin library.
- `instances\`: the folders of instances made by the launcher.
- `skins\` and `capes\`: the imported skins and capes.

The launcher finds the LegacyCraft folder (the one with `gradlew.bat`) by
looking upwards from where it runs. If it can't find it, it asks when you
press Play. Settings from the previous launcher (`launcher.properties`) are
imported automatically. Profiles from before instances keep playing in the
LegacyCraft folder itself (that's where their worlds are); new instances get
their own folder.

## Notes

- There's no login. **Change name** only changes the offline name.
- The default launcher behaviour is "Hide launcher and re-open when game
  closes", so you can see what happened if the game fails to start.
- Memory and JVM arguments reach the game through the `LEGACYCRAFT_JVM_ARGS`
  environment variable, which the game's `build.gradle` reads. An `-Xmx` in
  the JVM arguments overrides the Memory setting.
- The Java setting only sets `JAVA_HOME` for Gradle. The game itself runs on
  the build's Java 17 toolchain.
- The game doesn't read `--width` and `--height` yet, so it ignores them.
