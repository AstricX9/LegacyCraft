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
| Blockstate variant keys | `property=value` pairs sorted by property name, comma-joined; `normal` when no property remains; no multipart | VERIFIED_FORMAT | every variant key in the supplied 1.8.9 `blockstates/*.json`; BlockStateModelMappingTest |
| Variant rotation direction | `y: 90` turns a north face east; `x: 90` turns a north face down; x applies before y | VERIFIED_FORMAT | supplied `furnace.json` / `piston.json` / log `axis=x` variants; ModelBakerTest |
| Upside-down stair corners | For `half=top`, the `inner/outer_left/right` names are mirrored relative to `half=bottom` | VERIFIED_FORMAT | supplied stairs blockstates baked and checked by geometry in StairShapeGeometryTest |
| Sky light rule | Falls straight down at full strength while still 15; once dimmed, every further block costs at least 1 even if transparent; otherwise spreads losing max(1, opacity) | VERIFIED_VANILLA | our recomputed sky light equals the light a real 1.8.9 server stored, 262144/262144 blocks over four chunks (VanillaWorldComparisonTest) |
| Block light rule | Sources emit their light level and it spreads losing max(1, opacity) per block | VERIFIED_VANILLA | same comparison: 262142/262144 blocks; the two differences are stale light vanilla left behind, not rule differences |
| Light opacity | opaque cubes 15 (vanilla 255), leaves and cobweb 1, water and ice 3, slabs/stairs/farmland 15 with neighbour brightness | VERIFIED_VANILLA | implied by the exact light match above over terrain containing water, leaves and overhangs |
| Anvil save format | Region files: 8 KiB header of 1024 (offset, sectors) + timestamps, chunks as length-prefixed gzip/zlib NBT; chunk sections hold Blocks + Data/Add nibbles and SkyLight/BlockLight nibbles, plus Biomes and the TerrainPopulated/LightPopulated flags | VERIFIED_FORMAT | read back from a real 1.8.9 server world (RegionFile/AnvilChunkReader, 40,960,000 blocks scanned) |
| Legacy block ID range | Every block ID a real 1.8.9 world contains falls inside the 0-197 table | VERIFIED_VANILLA | full scan of a 1.8.9 server world |
| Biome IDs | The mutated variants exist alongside the base biomes (e.g. 129 sunflower plains) and are stored per column as a byte | VERIFIED_VANILLA | biome IDs 1, 2, 4, 6, 7, 17, 18, 27, 29, 35, 36 and 129 observed in a 1.8.9 world |
| Block support rules | Every block a real 1.8.9 world contains satisfies our "can it stay" rules | VERIFIED_VANILLA | 7,529,149 placed blocks checked (VanillaStateRulesTest), including torches, rails, fences, vines, cacti, sugar cane, lily pads, dead bushes, mushrooms and chests. Since those rules read the decoded facing/half/variant, this also exercises those metadata layouts |
| Vine support | A vine keeps its stored sides even after a side loses its wall; it drops only when no side is backed and there is no vine or full cube above | VERIFIED_VANILLA | vines under jungle leaves with an unbacked side exist in a 1.8.9 world; our earlier side-trimming rule (1.9+ behaviour) rejected them |
| Stale generation leftovers | Vanilla does not re-validate blocks when later generation changes the ground under them: plants remain on sand or floating where lakes and caves cut in | VERIFIED_VANILLA | 93 of 7.5M blocks, all plants, in the sampled world |
| Biome placement | The GenLayer chain: island(1), fuzzy zoom(2000), add island(1), zoom(2001), add island(2/50/70), remove-too-much-ocean(2), add snow(2), add island(3), cool-warm edge(2), heat-ice edge(2), special edge(3), zoom(2002), zoom(2003), add island(4), mushroom island(5), deep ocean(4); then river init(100) off that sketch, biome(200), two zooms(1000+), biome edge(1000), hills(1000) fed by the zoomed river-init field, rare biome(1001), four zooms(1000..1003) with add island(3) after the first and shore(1000) after the second, smooth(1000), river mix(100) against a river branch (two zooms, four zooms, river(1), smooth(1000)), and finally Voronoi zoom(10) | VERIFIED_VANILLA | our chain reproduces the biome byte a real 1.8.9 server stored for 160000/160000 columns over 625 chunks (VanillaBiomeMapTest, seed -412575085214022153) |
| Layer cell randomness | Each layer mixes its base seed three times, then the world seed three times; per cell the seed takes x, z, x, z through the same mix; `nextInt(n)` uses `(chunkSeed >> 24) % n` (corrected upward when negative) and then mixes the world seed back in | VERIFIED_VANILLA | implied by the exact biome match above, which depends on every random draw landing identically |
| Voronoi zoom offsets | Each of the four corners draws its x nudge first and its z nudge second, as `(nextInt(1024) / 1024 - 0.5) * 3.6`, with 4 added to x for the east corners and to z for the south ones; a block takes the strictly nearest corner, ties going to the south-east | VERIFIED_VANILLA | swapping the x and z draws costs ~3% of columns (97.04% agreement); the documented order gives 100% |
| Menu text source | All menu captions exist as keys in the supplied 1.8.9 `lang/en_US.lang` (e.g. `menu.online=Minecraft Realms`, `selectWorld.mapFeatures=Generate Structures:`, `generator.amplified.info=Notice: Just for fun, requires beefy computer`) | VERIFIED_VANILLA | read directly from the supplied asset archive; LegacyCraft looks every caption up there instead of hard-coding it |
| 1.8.9 Options entries | The lang file carries `options.realmsNotifications` (the 1.8.9 addition), `options.stream=Broadcast Settings...` and `stream.unavailable.*`, `options.skinCustomisation`, `options.snooper.view` and `options.resourcepack`, and no attack-indicator/offhand/subtitles keys | VERIFIED_VANILLA | supplied `en_US.lang` |
| Customized world screen | Four pages: "Basic Settings", "Ore Settings", "Advanced Settings (Expert Users Only!)", "Extra Advanced Settings (Expert Users Only!)", with Randomize/Defaults (confirmed by a "Warning!" prompt), Presets, Previous/Next Page; seven named presets (Water World ... Good Luck) | VERIFIED_VANILLA | `createWorld.customize.custom.*` keys and `textures/gui/presets/*.png` in the supplied assets |
| Death screen | 1.8.9 has "Respawn", "Title screen", "Delete world" (hardcore), "Leave server" and a quit confirmation ("Are you sure you want to quit?"); there is no spectate option | VERIFIED_VANILLA | `deathScreen.*` keys in the supplied `en_US.lang` |
| Default pack | The built-in pack's description is "The default look of Minecraft", pack_format 1, and it declares 75 languages | VERIFIED_VANILLA | supplied `assets/pack.mcmeta` |
| Sunrise glow edge | Looking down toward a sunrise from high up, the orange glow stops in a hard horizontal line where the fogged far terrain begins; vanilla shows the same edge | VERIFIED_VANILLA | side-by-side screenshots, vanilla 1.8.8 client and LegacyCraft, time 0, same seed and position (2026-09-28) |

## Repo-local facts (about this codebase, not vanilla — tracked separately from vanilla facts)

- Block numeric IDs in `indexes/blocks.json` are now sourced from
  `data/mc189/blocks.json` (see `deviations.yml` `DEV-002`, resolved half),
  and the registry contains exactly IDs 0-197. Block state properties and
  metadata layouts come from `data/mc189/block_states/` (HYPOTHESIS; see
  `unknowns.yml`).
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
