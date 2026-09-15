# LegacyCraft

A from-scratch Java/LWJGL recreation of the Minecraft 1.8.9 survival experience.

The current playable foundation discovers the supplied 1.8.9 blockstates and
textures at runtime, bakes inherited JSON block models, and stores its unbounded
horizontal world in lazily generated 16x16 chunks made from 16x16x16 sections.
Blocks and items are loaded through generated, deterministic registry indexes;
each registry entry has a self-contained JSON definition rather than sharing a
single monolithic properties file.

## Game-data generation

`processResources` runs `generateGameData` automatically. The task discovers the
bundled blockstates and item models, merges the compact defaults with any file in
`assets/legacycraft/definitions/blocks` or `definitions/items`, and emits:

- one JSON definition per block and item;
- contiguous numeric IDs with `minecraft:air` fixed at block ID 0; and
- validated `indexes/blocks.json` and `indexes/items.json` manifests.

Run `gradlew generateGameData` to inspect the catalog under
`build/generated/resources/gameData` without launching the client.

## Run

```powershell
.\gradlew.bat run
```

## Controls

- `WASD` move, mouse look
- `Space` jump, `Left Shift` sneak, `Left Control` sprint
- Left click break block, right click place block
- Mouse wheel or `1`-`9` select a hotbar slot
- `F` toggle fog distance, `F2` save a screenshot
- `F3` toggle the debug text overlay
- `Esc` release/capture the mouse; press it again while released to quit

The game resources under `src/main/resources/assets` come from the user-supplied
Minecraft 1.8.9 asset archive and are not redistributed by this project setup.

The subsystem layout and fidelity rules are tracked in [ARCHITECTURE.md](ARCHITECTURE.md).
