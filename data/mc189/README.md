# Minecraft 1.8.9 block data

Hand-maintained source data for the 1.8.9 blocks. `gradlew generateGameData`
turns it into the runtime files under `build/generated/resources/gameData`.
Nothing in this folder is generated.

Every block in `blocks.json` has exactly one file in each per-block folder,
named after the block (`oak_stairs.json`), much like vanilla ships one
`blockstates/<name>.json` per name. The build fails if a block is missing a
file or a file names a block that isn't in 1.8.9.

| Path | Holds | Tag |
|---|---|---|
| `blocks.json` | The legacy block ID table (0-197), in registration order | COMMUNITY_CORROBORATED |
| `block_defaults.json` | Block-data values used when a block's data file doesn't set them | LegacyCraft defaults |
| `block_data/<block>.json` | What the block **is**: `class` (its block class, see below; plain `Block` when absent) with the class's arguments (`doubleSlab` for slabs, `fruit` for stems), `material`, `hardness`, `opaque` (vanilla opaque cube), `fullCube`, `solid` (has collision), `translucent`, `renderType` (`model`/`fluid`/`entity`/`invisible`), `cullSame` (`none`/`block`/`state`/`material`), `tintColor`, `lightLevel`, `model` (placeholder model override), `targetable`, and static `bounds`/`collision` boxes in sixteenths | HYPOTHESIS |
| `block_states/<block>.json` | The block's **states**: `properties` (name → ordered values, first is the default unless `defaults` says otherwise; properties with no `meta` field are derived from neighbours by the block class), `meta` (ordered 4-bit fields `{property, shift, bits, map?, fallback?, when?}`) and `model` (`{file, ignore, mapper}` blockstate mapping). `{}` means the block has no state properties. `{"template": "stairs"}` uses a shared template; fields beside it override the template | HYPOTHESIS for metadata layouts; the model mapping is checked against the supplied 1.8.9 blockstates by `BlockStateModelMappingTest` |
| `state_templates/<name>.json` | Shared state definitions (stairs, door, fence, ...) | as above |
| `biomes/<biome>.json` | One file per 1.8.9 overworld biome: legacy `id`, `temperature`/`rainfall` (which index the vanilla colour maps), `depth`/`scale` (terrain shape), surface `topBlock`/`fillerBlock` (+ metadata), `snow`/`rain`, colour overrides (`grassColor`, `foliageColor`, `grassMix`, `waterColor`) and per-chunk decoration counts | HYPOTHESIS for the numbers; the colour-map lookup is checked against 1.8's documented biome colours by `BiomeColorsTest` |
| `block_items/<block>.json` | Which item models place this block, and the metadata placed (e.g. leaves set the no-decay bit). `{}` means no block item | HYPOTHESIS |

## Block classes

Behaviour lives in code, one class per kind of block in `net.minecraft.block`,
chosen by `class` in the block's data file: `stairs` → `BlockStairs`,
`fence` → `BlockFence`, `door` → `BlockDoor`, `bush` → `BlockBush`, and so on
(the full list is in `BlockRegistry#create`). A class owns its neighbour-derived
properties (`actualState`), selection and collision boxes, placement
orientation and support rules (`canStay`). Blocks without a `class` are plain
static-shaped blocks.

**Block data and block states are never combined in one file.** The build
rejects a state key in a data file and a data key in a states file.
