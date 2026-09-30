# Minecraft 1.8.9 game data

Hand-maintained source data for everything the game defines: one JSON file
per thing, named after it, in a folder named after what it is. `gradlew
generateGameData` (run by every build) checks these files and turns them into
the runtime files under `build/generated/resources/gameData`. Nothing in this
folder is generated.

```
data/minecraft/
  block/            one file per block (+ index.json: IDs and defaults)
  block_state/      one file per block's states (+ template/ for shared ones)
  item/             items that differ from the defaults (+ index.json: defaults)
  biome/            one file per biome
  recipe/           one file per crafting or smelting recipe
  drop/             what a block drops when broken
  tags/item/        named groups of items that recipes can use as "#minecraft:<tag>"
  enchantment/      one file per enchantment
```

Names inside files are namespaced (`minecraft:stick`); a tag is `#minecraft:planks`.

## Blocks

| Path | Holds | Tag |
|---|---|---|
| `block/index.json` | `entries`: the legacy block ID table (0-197) in registration order; `defaults`: the block values used when a block's file doesn't set them | IDs COMMUNITY_CORROBORATED; defaults are LegacyCraft's |
| `block/<block>.json` | What the block **is**: `class` (its block class, see below; plain `Block` when absent) with the class's arguments (`doubleSlab` for slabs, `fruit` for stems), `material`, `hardness`, `opaque` (vanilla opaque cube), `fullCube`, `solid` (has collision), `translucent`, `renderType` (`model`/`fluid`/`entity`/`invisible`), `cullSame` (`none`/`block`/`state`/`material`), `tintColor`, `lightLevel`, `model` (placeholder model override), `targetable`, static `bounds`/`collision` boxes in sixteenths, and `items`: which item models place this block and the metadata placed (leaves set the no-decay bit); no `items` means no block item | HYPOTHESIS |
| `block_state/<block>.json` | The block's **states**: `properties` (name → ordered values, first is the default unless `defaults` says otherwise; properties with no `meta` field are derived from neighbours by the block class), `meta` (ordered 4-bit fields `{property, shift, bits, map?, fallback?, when?}`) and `model` (`{file, ignore, mapper}` blockstate mapping). `{}` means no state properties. `{"template": "stairs"}` uses `block_state/template/stairs.json`; fields beside it override the template | HYPOTHESIS for metadata layouts; the model mapping is checked against the supplied 1.8.9 blockstates by `BlockStateModelMappingTest` |

Every block in the index has exactly one file in `block/` and one in
`block_state/`, like vanilla ships one `blockstates/<name>.json` per name.
The build fails if one is missing or a file names a block that isn't in 1.8.9.
**Block data and block states are never combined in one file**: the build
rejects a state key in a block file and a block key in a state file.

Behaviour lives in code, one class per kind of block in `net.minecraft.block`,
chosen by `class`: `stairs` → `BlockStairs`, `fence` → `BlockFence`, `door` →
`BlockDoor`, `bush` → `BlockBush`, and so on (the full list is in
`BlockRegistry#create`). A class owns its neighbour-derived properties
(`actualState`), selection and collision boxes, placement orientation and
support rules (`canStay`). Blocks without a `class` are plain static-shaped blocks.

## Items

Item IDs come from the item models (`assets/minecraft/models/item`), sorted by
name. `item/index.json` holds the `defaults` (`maxStackSize`, `maxDamage`,
`useAction`, `food`, `saturation`); `item/<item>.json` exists only for items that
differ from them. Block items are declared in their block's file. HYPOTHESIS.

## Biomes

`biome/<biome>.json`, one per 1.8.9 overworld biome: legacy `id`,
`temperature`/`rainfall` (which index the vanilla colour maps), `depth`/`scale`
(terrain shape), surface `topBlock`/`fillerBlock` (+ metadata), `snow`/`rain`,
colour overrides (`grassColor`, `foliageColor`, `grassMix`, `waterColor`) and
per-chunk decoration counts. HYPOTHESIS for the numbers; the colour-map lookup
is checked against 1.8's documented biome colours by `BiomeColorsTest`.

## Recipes

`recipe/<name>.json`, named after what it makes (`<result>_from_<ingredient>`
when several recipes make the same thing, `<result>_from_smelting` for the
furnace). Written from memory of 1.8.9's CraftingManager, the Recipes* helpers
and FurnaceRecipes (HYPOTHESIS, DEV-024 and DEV-028):

- `minecraft:crafting_shaped`: `pattern` rows, `key` letter → item or tag,
  `result` `{id, count}`. A shaped recipe fits anywhere in the grid and also
  matches mirrored left to right.
- `minecraft:crafting_shapeless`: `ingredients` (items or tags), `result`.
- `minecraft:smelting`: `ingredient`, `result`, `experience` per item smelted
  (looked up by the result, as FurnaceRecipes.getSmeltingExperience does).

Tool repair is built into the code; armour dyeing, fireworks, banners, maps and
book copying aren't recipes yet (DEV-024). Clownfish and pufferfish can't be
cooked. A tag in a recipe stands for any of its items (vanilla's wildcard
metadata, e.g. `#minecraft:planks`); tags live in `tags/item/<tag>.json` as
`{"values": [...]}`.

## Drops

`drop/<block>.json`: `{"block": ..., "drops": [rules]}` for a player breaking the
block with the right tool, before Fortune and Silk Touch (which are in
`BlockDrops`). Each rule gives `item` (or `block`: the item placing another
block with the same variant; or `self`/nothing: the block's own item), and
optionally `meta` (only for these metadata values), `count` `[min, max]`
(uniform) and `chance`. `"drops": []` drops nothing. A block with no file drops
the item that places it. Leaves, tall grass, double plants, dead bushes,
vines, snow layers, gravel, crops, stems, nether wart, cocoa and huge
mushrooms are computed in `BlockDrops`. Written from knowledge of vanilla
behaviour; not yet checked against a running game (docs/compat/unknowns.yml).
HYPOTHESIS.

## Enchantments

`enchantment/<name>.json`, named by the 1.8.9 command name (`sharpness`):

| Key | Meaning |
|---|---|
| `id` | The legacy ID items store (0-8 armour, 16-21 weapons, 32-35 tools, 48-51 bows, 61-62 fishing rods) |
| `translation_key` | The lang key (`enchantment.damage.all`) |
| `weight` | How often the table picks it (10 common, 5 uncommon, 2 rare, 1 very rare); also sets anvil costs |
| `max_level` | Highest level |
| `supported_items` | What the table puts it on: `armor`, `armor_head`/`_torso`/`_legs`/`_feet`, `weapon`, `digger`, `bow`, `fishing_rod`, `breakable` |
| `min_cost`, `max_cost` | The enchantability range that rolls each level: `base + (level - 1) x per_level_above_first` |
| `incompatible` | Enchantments this can't be added next to (not always mutual: Looting refuses Silk Touch, not the other way round, as in vanilla) |
| `anvil_also_accepts` | Extra items an anvil allows: `axes`, `shears`, `damageable`, `armor` |
| `protection` | For the protection family: `against` (`any`, `fire`, `fall`, `explosion`, `projectile`) and the `multiplier` on `(6 + level²) / 3` |

1.8.9 has 25 enchantments (Unbreaking is one enchantment that goes on
armour, tools, weapons, bows and rods). There are no curses, Mending, Frost
Walker or Sweeping Edge. HYPOTHESIS: recalled from 1.8.9's Enchantment
subclasses; what each one does lives in code (DEV-029).
