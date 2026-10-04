# LegacyCraft Launcher

An offline launcher laid out like the Minecraft Launcher 1.6.x that shipped
alongside 1.8.9: Update Notes / Launcher Log / Profile Editor tabs over a
plain bottom bar with the profile selector, a big **Play** button and the
player panel. There's no account and no login. It manages profiles, skins and
capes, and it starts the game.

Start it by double-clicking `run-launcher.bat` (it compiles the sources with
Java 17+ and opens the window). The Gradle project in this folder also works
in IDEs: `gradle run`.

## Tabs

- **Update Notes**: the news page. Its layout and colours follow the Tumblr
  theme ("Photo Minimal", tweaked by Mojang) that the real launcher showed. The
  launcher paints the theme's tiled dark background behind the page (made
  from the game's dirt texture in grey; `#e0d0d0` text, `#aaaaff` links). To write your own news, copy
  `src/main/resources/net/legacycraft/launcher/news.html` to
  `%USERPROFILE%\.legacycraft-launcher\news.html` and edit the copy. Links
  starting with `launcher:` (`skins`, `profiles`, `console`, `gamedir`,
  `skinsdir`, `capesdir`, `datadir`) are handled by the launcher.
- **Launcher Log**: the launcher log, plus the game's output prefixed
  with `Client>`.
- **Profile Editor**: the profile table. Double-click a profile to edit it;
  right-click to add, copy or delete one.
- **Skins & Capes** (new, not in the 1.6 launcher): a skin list and a cape
  list next to a 3D preview. Whatever is selected in each list is what you
  wear.
  - Add 64x64 skins, or old 64x32 skins (converted the way 1.8 does it).
  - Choose Classic or Slim arms for each skin.
  - Add capes in the 64x32 cape layout (or 128x64 and other 2:1 sizes).
    Only the cape part is used; the elytra area of newer capes is ignored.
  - Drag the preview to turn the player; double-click it to stop spinning.
  - Drop PNG files onto a list to add them.

## Bottom bar

- **Profile / New Profile / Edit Profile** open the Profile Editor dialog:
  - Profile Info: name, game directory, resolution, launcher visibility.
  - Version Selection: only 1.8.9 is offered.
  - Java Settings: Executable, JVM Arguments.
- **Play** runs `gradlew run` in the LegacyCraft folder. It passes
  `--gamedir`, `--username`, `--skin` (the chosen skin, converted to 64x64 and
  copied to `<game dir>\.legacycraft-launcher\skin.png`), `--skinModel`,
  `--cape` (copied to `cape.png` there), and `--width`/`--height` when a
  resolution is set.
- **Switch User** sets the offline player name.

## Where things are stored

`%USERPROFILE%\.legacycraft-launcher\`:

- `launcher_profiles.json`: the profiles, in the 1.6 launcher's format, plus a
  `legacycraft` section with the player name, the install folder and the skin
  library.
- `skins\` and `capes\`: the imported skins and capes.

The launcher finds the LegacyCraft folder (the one with `gradlew.bat`) by
looking upwards from where it runs. If it can't find it, it asks when you
press Play. Settings from the previous launcher (`launcher.properties`) are
imported automatically.

## Differences from the 1.6 launcher

- There's no login. **Switch User** only changes the offline name.
- The Skins & Capes tab is new. The game draws the cape and slim arms; the
  in-game Skin Customization screen can hide the cape and each outer layer.
- The default launcher visibility is "Hide launcher and re-open when game
  closes", so you can see what happened if the game fails to start.
- JVM Arguments reach the game through the `LEGACYCRAFT_JVM_ARGS` environment
  variable, which the game's `build.gradle` reads.
- Executable only sets `JAVA_HOME` for Gradle. The game itself runs on the
  build's Java 17 toolchain.
- "Ask Mojang for assistance with crashes" is shown but disabled.
- The game doesn't read `--width` and `--height` yet, so it ignores them.
