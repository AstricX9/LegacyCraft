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

## Menus and saves

The client starts on the 1.8.9 title screen. Singleplayer, Create New World
(with More World Options, Superflat and Customized screens), Options (video,
controls, sounds, skin, language, chat, resource packs, snooper), Multiplayer,
pause and death screens follow the 1.8.9 layout; every caption comes from the
supplied `lang/*.lang` files. What still sits behind some of those buttons is
listed in `docs/compat/deviations.yml` (DEV-009 to DEV-014).

Files are kept next to where the game runs (change with `--gamedir=<path>`):
`saves/<world>/` (a vanilla-shaped `level.dat` plus LegacyCraft chunk files,
DEV-003), `options.txt` (vanilla option names and key codes), `servers.dat`,
`resourcepacks/` and `screenshots/`.

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
