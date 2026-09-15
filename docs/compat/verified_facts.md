# Verified facts — Minecraft Java Edition 1.8.9

Each fact below is tagged per `source_policy.md`. Move an item here only once
it has real evidence; keep speculation in `unknowns.yml`.

| Fact | Value | Tag | Evidence |
|---|---|---|---|
| Target edition/version | Java Edition 1.8.9 | VERIFIED_HISTORY | [minecraft.wiki/w/Java_Edition_1.8.9](https://minecraft.wiki/w/Java_Edition_1.8.9) |
| Release date | 2015-12-09 | VERIFIED_HISTORY | same |
| Protocol version | 47 | VERIFIED_HISTORY | same |
| Resource-pack `pack_format` | 1 | VERIFIED_FORMAT | [minecraft.wiki/w/Pack_format](https://minecraft.wiki/w/Pack_format) (format 1 covers 1.6.1–1.8.9) |
| World height | 0–255 (256 blocks), pre-flattening IDs+metadata | VERIFIED_HISTORY | master context §3, §11.2 |
| 1.8.9 vs 1.8.8 | Minor: Realms notification toggles + 13 bug fixes (water physics, village spawning, arrow despawning, chat formatting, skin rendering); no new gameplay content | VERIFIED_HISTORY | minecraft.wiki/w/Java_Edition_1.8.9 |
| Trapped chest Christmas texture | Renders a seasonal texture when the client's system date is Dec 24–26 | VERIFIED_HISTORY | minecraft.wiki/w/Java_Edition_1.8.9 (candidate quirk — see `bugs_1_8_9.yml`) |
| Combat model | Pre-1.9: no attack cooldown, no shields/offhand, sword blocking exists | VERIFIED_HISTORY | master context §3, §30 |
| Chunk section shape | 16×16×16, YZX index ordering `(y<<8)\|(z<<4)\|x` | VERIFIED_FORMAT | matches the classic Anvil `Blocks` byte-array layout used in region files |

## Repo-local facts (about this codebase, not vanilla — tracked separately from vanilla facts)

- Block numeric IDs in `indexes/blocks.json` are now sourced from
  `data/mc189/blocks.json` (see `deviations.yml` `DEV-002`, resolved half).
  Item numeric IDs in `indexes/items.json` are still assigned by **sorted
  discovery order** of the supplied resource pack's item model files — see
  `unknowns.yml` for why that's not a simple fix.
- The supplied asset archive under `src/main/resources/assets` is
  VERIFIED_VANILLA as genuine 1.8.9 content: its own
  `src/main/resources/version.json` reports `"id": "1.8.9"`,
  `"releaseTime": "2015-12-03T09:24:39+00:00"`, and a
  `launchermeta.mojang.com` URL for the `1.8.9.json` version manifest. It was
  produced by the community **minecraft-assets** extraction project (per
  `src/main/resources/README.md`), which preserves original vanilla
  **blockstate** filenames exactly (confirmed: `blockstates/wooden_door.json`
  matches the legacy block name) but **renames item model files** to a
  normalized naming scheme decoupled from legacy item identity (confirmed:
  `models/item/oak_door.json` contains the genuine legacy texture path
  `items/door_wood`, but the real 1.8.9 item name for that item is
  `minecraft:wooden_door`). Any code that treats an item model filename as
  the item's registry name is therefore unsound — see `unknowns.yml`.
