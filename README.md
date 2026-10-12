# LegacyCraft

A from-scratch Java/LWJGL recreation of the Minecraft 1.8.9 survival experience.

The current playable foundation discovers the supplied 1.8.9 blockstates and
textures at runtime, bakes inherited JSON block models, and stores its unbounded
horizontal world in lazily generated 16x16 chunks made from 16x16x16 sections.
Blocks and items are loaded through generated, deterministic registry indexes;
each registry entry has a self-contained JSON definition rather than sharing a
single monolithic properties file.

## Game-data generation

`processResources` runs `generateGameData` automatically. Block source data is
organised one file per block under `data/mc189/` (see
[data/mc189/README.md](data/mc189/README.md)): `block_data/<block>.json`,
`block_states/<block>.json` and `block_items/<block>.json` for every block in
the 1.8.9 ID table. Item definitions merge the compact item defaults with any
file in `assets/legacycraft/definitions/items`. The task emits:

- exactly the canonical block IDs 0-197 from `data/mc189/blocks.json`;
- per block, a **data** file (`definitions/blocks`) and, separately, a
  **states** file (`states/blocks`). The two are never combined, and the build
  rejects keys that belong to the other side. Species and colour files such as
  `oak_planks` or `red_wool` are metadata variants, not blocks;
- one definition per item, including the (block, metadata) a block item places; and
- validated `indexes/blocks.json` and `indexes/items.json` manifests.

Run `gradlew generateGameData` to inspect the catalog under
`build/generated/resources/gameData` without launching the client.

## Run

In IntelliJ, reload the Gradle project and select the **LegacyCraft** run
configuration. It runs Gradle's `run` task, which generates resources before
launching `net.minecraft.client.main.Main`.

Or run from a terminal:

```powershell
.\gradlew.bat run
```

### Builds for other PCs (Windows 7 or later, Linux, macOS)

Double-click `build.bat` (or run `./build.sh` on Linux/macOS) for the build console: pick
Windows (64- and 32-bit together, or one of x64, x86, ARM64), Linux (x64, ARM64) or
macOS (Intel, Apple silicon), or all of them, and change the LegacyCraft version first if
you like (`V`, which edits the `lc:` line of `version.txt`). Each package lands in
`build/distributions/LegacyCraft-<version>-<target>.zip`: the game already built (`lib/`),
the launcher as a jar, and scripts to start them. The other PC needs no Gradle or source,
only Java 17, which can go in a `runtime/` folder inside it (see `packaging/windows/README.txt`
and `packaging/unix/README.txt`). The same packages build without the menu with
`gradlew packageWindows`, `packageLinuxX64`, `packageAll` and so on (listed in `build.gradle`),
or `build.bat linuxX64 a1.0.2`. The launcher runs a packaged game with plain Java instead of
`gradlew run`.

## Menus and saves

The client starts on the 1.8.9 title screen. Singleplayer, Create New World
(with More World Options, Superflat and Customized screens), Options (video,
controls, sounds, skin, language, chat, resource packs, snooper), Multiplayer,
pause and death screens follow the 1.8.9 layout; every caption comes from the
supplied `lang/*.lang` files. What still sits behind some of those buttons is
listed in `docs/compat/deviations.yml` (DEV-009 to DEV-014).

Files are kept next to where the game runs (change with `--gamedir=<path>`):
`saves/<world>/` (a vanilla-shaped `level.dat` plus LegacyCraft chunk files,
DEV-003), `options.txt` (vanilla option names and key codes), `additions.txt`
and `shaders/` (see Additions below), `servers.dat`,
`resourcepacks/` and `screenshots/`.

## Additions

Additions are LegacyCraft's own mod system: optional changes to 1.8.9, so
each player chooses how faithful the game looks. They are grouped as
Gameplay, Graphics, Interface, Accessibility and Launcher. Each addition is
its own file in `src/main/resources/assets/legacycraft/additions/`.

Each instance turns Additions on or off as a whole in the launcher's
**Additions** tab. Off (the default) is plain 1.8.9: no addition is active
and the title screen has no Additions button. On, each addition is switched
on or off in that tab, or in game from the **Additions** button under
Minecraft Realms on the title screen or under Achievements in the pause
menu. Both edit `additions.txt` in the game folder, so each
instance has its own. The current additions are Toggle Sprint, Toggle Sneak,
Full Bright, Shaders, Coordinates, FPS Counter, No Damage Tilt, Steady Field
of View and Solid Crosshair in the game, and Remember Tab, What's New and
Additions on Instances in the launcher (DEV-041).

The launcher and in-game screen both expose the individual addition switches.

### Settings

An addition can have settings: switches, sliders, lists of choices, and
files picked from a folder in the instance. Its row then has a settings
button (`textures/gui/settings.png`, so a resource pack can change it; a
small gear is drawn while that image is missing) that opens them, in game
and in the launcher. They are saved per instance in `additions.txt` as
`<addition>.<setting>:<value>` lines.

### Shaders

The Shaders addition runs a fragment shader over the picture (not the HUD).
It has built-in looks (Vibrant, Warm, Cool, Sepia, Grayscale) and a Custom
File setting that picks your own shader from the instance's `shaders`
folder (`.fsh`, `.frag` or `.glsl`). A shader is GLSL 1.20 and gets:

```glsl
uniform sampler2D scene;   // the picture
uniform float strength;    // the Strength setting, 0-1
uniform float time;        // seconds since the game started
uniform vec2 resolution;   // the window size in pixels
varying vec2 uv;           // where on screen, 0-1
```

and writes `gl_FragColor`. `assets/legacycraft/shaders/grayscale.fsh` is a
small one to start from. A shader that doesn't compile is reported in the
chat, and the file is reloaded when it changes.

### Making an addition

Copy `additions/_template.json` to `additions/<id>.json` (files starting
with `_` are skipped) and fill it in: `id`, `category`, `scope` (`game`, or
`launcher` for the launcher's own), `default`, `name`,
`description`, and any `settings` (the template has one of each kind). Then
add the id to `net.minecraft.client.additions.Additions` (or the launcher's
`Additions`) and check `additions.isOn(id)` where it changes the game (it is
false while Additions are off); read settings with
`additions.setting(id, "setting")`, `settingOn` or `number`. Both Additions
screens and the launcher pick it up from the folder.

## World generation

New worlds use a clean-room reimplementation of the 1.8.9 overworld
generator: the vanilla biome map, density-noise terrain blended between
biomes, biome surfaces (sand, sandstone, mesa clay bands, podzol, gravel
sea floors), caves and ravines, and population once a chunk's neighbours
exist (lakes, dungeons, ores, the biome's trees and plants, ice and snow).
The spawn point is chosen the vanilla way (grass in a forest, plains, taiga
or jungle near the origin). Structures are not generated yet; the full list
of differences is DEV-001 and DEV-016 in `docs/compat/deviations.yml`.

Worlds saved before this generator keep their old chunks, so walking into
new land there shows a seam; create a new world to see the terrain as
intended.

## Dedicated server

`gradlew runServer` starts a headless server in `run-server/` that Minecraft
1.8.9 clients can join (offline mode, port 25565 by default). The first start
writes `run-server/server.properties` (vanilla key names: `server-port`,
`level-seed`, `gamemode`, `view-distance`, `max-players`, `motd`,
`level-name`); the world is saved every five minutes and when you type
`stop`. Survival works: the server keeps each player's inventory, times
digging, drops and picks up items, runs crafting (inventory grid and crafting
table), hunger, fall damage, death and respawning, and saves every player in
`<world>/playerdata/`. Zombies, skeletons, creepers and spiders spawn in
the dark around every player and fight them, and farm animals spawn on
grass (`spawn-monsters` / `spawn-animals` in `server.properties` turn them
off). In-game commands are `/list`, `/seed`, `/gamemode <mode>`, `/spawn`
and `/kill`. What the server does not do yet (furnaces, chests, PvP, the
LegacyCraft client joining) is DEV-040.

## Controls

Defaults are 1.8.9's and can be rebound in Options > Controls.

- `WASD` move, mouse look, `Space` jump, `Left Shift` sneak, `Left Control` sprint
- Left click break block (hold in Survival; hardness and tools matter), right click
  place block (hold to keep placing), middle click pick block
- `Q` drops one of the held item (`Ctrl+Q` the stack); walking over items picks them up
- Mouse wheel or `1`-`9` select a hotbar slot
- `E` inventory: the creative inventory in Creative (tabs, search, the Survival
  Inventory tab with its destroy slot), the 2x2 crafting inventory otherwise
- Creative: double-tap `Space` to fly, `Space` / `Left Shift` to rise / descend,
  land to stop flying; blocks break instantly and placing uses no items
- `Esc` pause menu, `F1` hide the HUD, `F2` screenshot, `F11` fullscreen

In game the HUD follows 1.8.9: hotbar with item icons and counts, the held
item's name after switching, and in Survival/Adventure the hearts, food,
armor and experience bar. Broken blocks drop items (stone needs a pickaxe,
ores need the right tier). Inventory and flight state are saved in
`level.dat`; dropped items are not. What is still missing (crafting results,
drag splitting, sounds) is listed as DEV-015.

## Developer tools

- `F3` debug screen with 1.8.9's fields; `Alt+F3` adds the frame-time graph.
- `F3+A` rebuild chunk meshes, `F3+F` / `F3+Shift+F` render distance up/down,
  `F3+G` chunk borders, `F3+H` advanced tooltips, `F3+P` pause on lost focus,
  `F3+T` reload GUI textures and language, `F3+B` hitboxes, `F3+N` creative/spectator.
- Launch arguments (not in vanilla):
  - `--world=<folder>` open or create `saves/<folder>` straight away
  - `--seed=<n>` / `--spawn=<x>,<z>` throwaway, unsaved Creative world at a
    seed/position, with a starter hotbar
  - `--capture=<file.png>` render one in-game frame to a PNG and quit
  - `--debug` start with F3 open, `--gamedir=<path>` move saves/options

```powershell
.\gradlew.bat run --args="--world=Test --debug"
```

The game resources under `src/main/resources/assets` come from the user-supplied
Minecraft 1.8.9 asset archive and are not redistributed by this project setup.

The subsystem layout and fidelity rules are tracked in [ARCHITECTURE.md](ARCHITECTURE.md).
