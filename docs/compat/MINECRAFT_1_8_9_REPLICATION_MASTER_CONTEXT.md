# Minecraft: Java Edition 1.8.9 — 1:1 Replication Master Context

> **Purpose:** This document is the master source of truth for agents implementing a clean-room, behaviorally faithful reimplementation of **vanilla Minecraft: Java Edition 1.8.9**.
>
> **Target:** Java Edition **1.8.9**, not “classic Minecraft,” not “roughly 1.8,” not a modern Minecraft clone with old textures.
>
> **Primary rule:** If vanilla 1.8.9 and the replica disagree, vanilla 1.8.9 wins unless the project has an explicit, documented deviation.

---

# 0. MASTER DIRECTIVE FOR EVERY AGENT

You are contributing to a project whose goal is **observable equivalence with vanilla Minecraft: Java Edition 1.8.9**.

Do not optimize for familiarity, modern conventions, elegance, later-version behavior, or what seems logically correct. Optimize for **what Minecraft 1.8.9 actually does**.

A feature is not complete because it:

- looks similar,
- feels similar,
- has the same name,
- behaves correctly in the common case,
- passes a hand-written happy-path test,
- matches a modern Minecraft implementation,
- matches a Wiki summary,
- matches another clone,
- or matches an agent’s memory.

A feature is complete only when its externally observable behavior has been compared against a vanilla 1.8.9 reference and the relevant test suite passes.

If exact behavior is unknown, **do not invent it silently**. Mark the uncertainty, create a differential test, capture vanilla behavior, and then implement the result.

The replica should deliberately preserve old behavior that later versions changed, including quirks that modern developers may be tempted to “fix.”

---

# 1. PROJECT DEFINITION OF “1:1”

“1:1” means the following dimensions of compatibility are treated independently and must each be measured.

## 1.1 Simulation fidelity

Given equivalent initial state, seed, player input, packet sequence, tick timing, and configuration, the replica should produce the same gameplay state as vanilla 1.8.9.

This includes:

- player movement,
- block updates,
- scheduled ticks,
- entity movement,
- entity AI decisions,
- damage,
- knockback,
- hunger and exhaustion,
- potion effects,
- item durability,
- inventory transactions,
- redstone,
- crop growth,
- fluids,
- weather,
- daylight,
- world generation,
- structure placement,
- mob spawning,
- despawning,
- loot,
- XP,
- enchantment outcomes,
- command effects,
- and death/respawn behavior.

## 1.2 Network fidelity

A compatible implementation should correctly speak **Minecraft protocol version 47**, including the state machine and wire-level details expected by a 1.8.x client/server.

Network compatibility is not optional if the project claims 1.8.9 multiplayer compatibility.

## 1.3 Persistence fidelity

World data, player data, chunk data, entity data, inventories, tile entities, and metadata must preserve 1.8.9 semantics. Where the project supports direct interoperability with vanilla worlds, save/load behavior must be verified against actual vanilla 1.8.9 data.

## 1.4 Rendering fidelity

For the same world state, camera, resource pack, settings, and frame time, the replica should reproduce the visual result of vanilla 1.8.9 as closely as technically practical.

Rendering fidelity includes far more than textures. It includes:

- camera transform,
- FOV behavior,
- view bobbing,
- hand animation,
- item transforms,
- fog,
- sky,
- clouds,
- lighting,
- ambient occlusion,
- smooth-light interpolation,
- block model transforms,
- biome tinting,
- particles,
- damage tint,
- enchantment glint,
- breaking overlay,
- entity interpolation,
- shadow blobs,
- nametags,
- GUI composition,
- font rasterization,
- and old transparency behavior.

## 1.5 Input fidelity

Key presses, mouse movement, mouse sensitivity, sprint toggling, double-tap behavior, inventory clicks, drag behavior, key repeat, focus changes, and pause behavior should match 1.8.9.

## 1.6 UI fidelity

Screens, menus, HUD layout, button states, text, focus behavior, list scrolling, slot interaction, tooltip placement, debug overlays, chat, achievements, statistics, resource pack UI, multiplayer UI, Realms integration surfaces, and loading screens should match the target version.

## 1.7 Audio fidelity

Correct sound event, timing, attenuation, pitch randomization, volume, looping behavior, music cadence, ambient audio, positional audio, and GUI audio all matter.

## 1.8 Timing fidelity

Minecraft is highly timing-sensitive. Behavior must match not only “what happens” but **when it happens**.

Examples:

- tick ordering,
- redstone delays,
- piston delays,
- invulnerability windows,
- eating duration,
- bow charge,
- potion duration,
- entity interpolation,
- block breaking progress,
- scheduled fluid updates,
- torch burnout,
- item use,
- portal timing,
- death timing,
- chunk send cadence,
- keepalive timing,
- and animation timing.

## 1.9 Bug/quirk fidelity

If a bug or unusual behavior is observable and gameplay-relevant in vanilla 1.8.9, it is part of the compatibility surface unless the project explicitly excludes it.

Do not “correct” old behavior merely because it appears strange.

---

# 2. CLEAN-ROOM AND IP BOUNDARY

This project should reproduce **behavior**, not redistribute proprietary Mojang code or copyrighted game assets without permission.

Agents must follow these rules:

1. Do not paste, reconstruct, or redistribute Mojang source code.
2. Do not ship Mojang textures, sounds, music, fonts, language files, models, or other copyrighted assets unless the project has an explicit legal right to do so.
3. It is acceptable to use a legally obtained vanilla installation as an **oracle for behavioral comparison**.
4. It is acceptable to document observed behavior, file formats, network formats, algorithms inferred through testing, and compatibility requirements.
5. Implementation code should be independently written.
6. Community decompilations or mappings can be useful as research pointers, but agents should avoid copying implementation text verbatim.
7. If authentic asset compatibility is needed, support externally supplied or user-owned resource packs rather than bundling proprietary resources.
8. Project branding must not imply official Mojang/Microsoft affiliation.

The desired outcome is a clean-room compatibility implementation, not an unauthorized distribution of Minecraft itself.

---

# 3. VERIFIED TARGET ANCHORS

These are stable target facts and should be treated as hard anchors unless stronger evidence contradicts them.

- Target edition: **Minecraft: Java Edition**.
- Target version: **1.8.9**.
- Full release date: **9 December 2015**.
- Network protocol: **47**.
- 1.8.9 is network-compatible with the 1.8-era protocol family used by 1.8 through 1.8.8.
- 1.8.9 is fundamentally a late 1.8 release; its notable new user-facing change over 1.8.8 was improved Realms integration, including a Realms notifications option.
- Resource pack format: **pack_format 1**.
- The classic pre-1.9 combat model is still active.
- Sword blocking still exists.
- Shields do not exist.
- Attack cooldown does not exist.
- Off-hand gameplay does not exist.
- The game uses the pre-flattening numeric-ID/data-value era.
- World height is the old 256-block vertical range.
- Chunks are 16×16 columns subdivided into 16×16×16 sections.
- The networking-era chunk representation includes block state data plus block light, sky light, and biome data.
- Vanilla 1.8.9 is associated with the LWJGL 2/OpenGL-era client architecture rather than the modern rendering stack.

Anything more detailed than these anchors should still be backed by tests or version-specific evidence.

---

# 4. SOURCE-OF-TRUTH HIERARCHY

When sources disagree, use this order.

## Tier 0 — Vanilla 1.8.9 black-box observation

Highest authority.

Examples:

- run vanilla 1.8.9,
- provide controlled input,
- record state changes,
- capture packets,
- compare screenshots,
- inspect saved NBT,
- inspect logs,
- measure timing,
- replay deterministic experiments.

If an empirical result is repeatable, it wins over memory and summaries.

## Tier 1 — Official Mojang/Minecraft artifacts and metadata

Examples:

- official version metadata,
- launcher version manifest,
- server/client protocol behavior,
- official bug reports,
- official release notes,
- vanilla-produced world files,
- vanilla-produced resource metadata.

## Tier 2 — Version-specific protocol documentation

Use archived or version-aware protocol documentation for protocol 47.

Do not use a modern protocol page and assume fields are unchanged.

## Tier 3 — Minecraft Wiki version/history pages

Useful for feature presence, historical changes, item/block history, mechanics, and format history.

Always make sure the page is talking about the 1.8.x era, not current behavior.

## Tier 4 — Community reference implementations and technical projects

Useful for discovering edge cases, packet names, constants, and areas to test.

Never assume another project is perfectly faithful.

## Tier 5 — Human memory, forum anecdotes, videos

Useful for hypotheses only.

Convert anecdotes into tests before treating them as truth.

---

# 5. EVIDENCE TAGGING STANDARD

Every nontrivial compatibility claim should be tagged internally with one of:

- `VERIFIED_VANILLA` — directly measured against vanilla 1.8.9.
- `VERIFIED_FORMAT` — established by a version-specific documented file/network format.
- `VERIFIED_HISTORY` — established by reliable version history.
- `COMMUNITY_CORROBORATED` — multiple independent sources agree, but not yet black-box tested.
- `HYPOTHESIS` — plausible but unverified.
- `UNKNOWN` — explicitly unresolved.

Never silently promote `HYPOTHESIS` to fact.

---

# 6. NO-MODERNIZATION RULE

Agents must assume that later-version behavior is wrong for this project unless specifically proven present in 1.8.9.

The following are examples of forbidden accidental modernization:

- attack cooldown,
- attack indicator,
- sweeping attacks,
- shields,
- off-hand slot,
- elytra,
- modern swimming,
- crawling mechanics,
- recipe book,
- advancements,
- modern recipe unlocking,
- modern combat damage values,
- modern armor toughness,
- modern shield/axe interactions,
- modern hunger regeneration,
- modern villager professions/trading UI,
- modern waterlogging,
- modern block-state flattening,
- modern namespaced save representation where legacy numeric semantics are required,
- modern world height,
- modern cave generation,
- modern ore distribution,
- modern biome noise system,
- modern lighting engine assumptions,
- modern chunk palettes,
- modern networking,
- signed chat,
- modern server configuration defaults,
- modern entity dimensions,
- modern sneaking/crawling hitboxes,
- modern sound events,
- modern resource-pack format,
- modern block models where the 1.8 model system behaved differently,
- modern redstone update behavior,
- modern piston edge-case behavior,
- modern mob AI changes,
- modern pathfinding changes,
- modern loot tables where hardcoded 1.8 behavior is expected,
- modern commands or selector syntax,
- modern translations,
- modern menus,
- modern accessibility settings,
- modern Unicode/font behavior,
- or any quality-of-life feature introduced after 1.8.9.

If a modern engine is used underneath, it must emulate old outward behavior.

---

# 7. ARCHITECTURAL REQUIREMENTS

The implementation should be structured so fidelity can be measured and corrected without rewriting the entire project.

Recommended high-level separation:

```text
Core Simulation
├── Time / ticks
├── RNG services
├── World / chunks
├── Blocks
├── Lighting
├── Scheduled updates
├── Redstone
├── Fluids
├── Entities
├── Player systems
├── Inventory / items
├── Crafting / furnace / brewing / enchanting
├── AI / pathfinding
├── Weather / daylight
└── Dimensions / portals

Networking
├── Handshake
├── Status
├── Login
├── Encryption
├── Compression
├── Protocol 47 PLAY packets
├── Client state synchronization
└── Server authority

Persistence
├── NBT
├── level.dat
├── region files
├── chunks
├── entities
├── tile entities
├── playerdata
└── statistics / achievements where applicable

Client Presentation
├── window/input
├── renderer
├── chunk meshing
├── entities
├── particles
├── first-person item
├── GUI
├── HUD
├── font
├── audio
└── resource-pack loader

Compatibility Harness
├── vanilla oracle runner
├── deterministic replay
├── packet recorder
├── state logger
├── screenshot comparator
├── NBT comparator
└── test corpus
```

Do not let renderer decisions mutate authoritative world behavior.

Do not let GUI code become the source of truth for gameplay state.

Do not let networking code invent simulation state that belongs to the world model.

---

# 8. TIME AND TICK MODEL

Minecraft 1.8.9 gameplay is tick-based.

The core simulation target is **20 game ticks per second** under normal conditions.

The server simulation must use a fixed-timestep design. Rendering may be frame-rate-independent and interpolate between simulation states, but it must not change authoritative results.

## 8.1 Required concepts

Implement distinct notions of:

- game tick,
- render frame,
- partial tick/interpolation fraction,
- scheduled block tick,
- entity update tick,
- random block tick,
- world time,
- total world time,
- day/night time,
- client interpolation,
- network arrival time.

## 8.2 Ordering matters

Do not treat a game tick as an unordered “update everything” pass.

Exact ordering between systems can affect:

- redstone,
- pistons,
- fluids,
- collisions,
- damage,
- entity mounting,
- item pickup,
- portals,
- chunk loading,
- AI,
- and block scheduling.

When ordering is unknown, create a targeted vanilla experiment.

## 8.3 Lag behavior

Do not hide simulation differences using variable-delta physics.

If the server falls behind, it should remain a discrete-tick simulation. Any catch-up behavior should be tested against vanilla.

---

# 9. RANDOMNESS AND DETERMINISM

Minecraft’s apparent simplicity hides heavy dependence on exact pseudo-random sequencing.

A “similar” RNG is insufficient for 1:1 world generation.

Agents must document every RNG stream used by a subsystem.

Track:

- seed derivation,
- Java signed integer overflow behavior,
- 32-bit versus 64-bit arithmetic,
- shift semantics,
- multiplication overflow,
- coordinate hashing,
- per-chunk seeds,
- per-feature seeds,
- structure seeds,
- entity RNG state,
- enchantment RNG,
- loot RNG,
- random tick selection,
- particle/audio randomness if visual/audio fidelity requires it.

Do not substitute a modern PRNG.

If the original behavior depended on `java.util.Random` semantics, emulate those semantics exactly.

For every deterministic subsystem, store test vectors:

```text
input seed
input coordinates
input settings
expected random outputs
expected structure positions
expected block/state hash
```

---

# 10. COORDINATE SYSTEM AND NUMERIC SEMANTICS

Compatibility bugs often come from “equivalent” math that rounds differently.

Agents must preserve:

- integer floor behavior for negative coordinates,
- float versus double usage where observable,
- cast timing,
- truncation,
- clamping,
- angle wrapping,
- yaw conventions,
- pitch conventions,
- AABB epsilon behavior,
- block-coordinate conversion,
- chunk-coordinate conversion,
- local section indexing,
- packet quantization,
- entity fixed-point fields where protocol 47 uses them.

Negative-coordinate tests are mandatory.

Every math helper used by collision, movement, ray casting, worldgen, and networking should have a version-locked test suite.

---

# 11. WORLD REPRESENTATION

## 11.1 Dimensions

The core vanilla dimensions are:

- Overworld,
- Nether,
- End.

Each dimension has different:

- lighting rules,
- sky behavior,
- terrain generation,
- biome behavior,
- coordinate scaling for portals,
- spawn logic,
- weather behavior,
- ambient visuals,
- mob rules,
- bed behavior,
- and world provider semantics.

Do not generalize them into one dimension template unless all differences remain expressible.

## 11.2 World height

Use the old 256-block world height model.

Treat valid block coordinates, build limits, sky calculations, section indexing, and chunk serialization according to 1.8.9 behavior.

Do not import 1.18+ negative-Y assumptions.

## 11.3 Chunks

A chunk column is 16×16 horizontally and spans the old world height.

Internally, model vertical sections as 16×16×16 units.

Important compatibility surfaces include:

- section presence/absence,
- empty sections,
- block IDs,
- metadata/data values,
- block light,
- sky light,
- biome IDs,
- height map behavior,
- tile entities,
- entities,
- scheduled ticks,
- terrain-populated flags,
- timestamps,
- inhabited time where present,
- and save ordering where relevant.

## 11.4 Legacy IDs and metadata

1.8.9 exists before The Flattening.

Do not design the authoritative compatibility layer around modern block-state names alone.

Represent or losslessly map:

```text
legacy block numeric ID
legacy metadata/data value
runtime block/state object
network state encoding
save representation
```

Different metadata values can encode:

- orientation,
- variant,
- growth stage,
- power,
- slab half,
- door half/state,
- rail shape,
- fluid level,
- wool color,
- log axis,
- leaves variant/decay state,
- and many other properties.

Do not assume metadata is merely cosmetic.

## 11.5 NBT

Implement version-compatible Named Binary Tag support with exact primitive types and array handling.

Preserve unknown tags when practical for interoperability.

Do not coerce byte/short/int/long types just because values happen to fit.

---

# 12. ANVIL / REGION PERSISTENCE

World interoperability requires correct handling of classic Anvil-era region files.

Important concepts:

- `.mca` region files,
- chunk location table,
- chunk timestamps,
- compressed NBT chunk payloads,
- section list,
- section `Y`,
- `Blocks`,
- `Data`,
- `BlockLight`,
- `SkyLight`,
- optional high-ID extension data where required,
- chunk `Biomes`,
- entities,
- tile entities,
- tile ticks,
- level metadata.

A chunk section holds 4096 block positions.

The old Anvil structure uses nibble arrays for metadata/light data.

## 12.1 Persistence test suite

For every save-capable milestone:

1. Generate a world in vanilla.
2. Save and close.
3. Load in replica.
4. Save without intentional mutation.
5. Load resulting world in vanilla where supported.
6. Compare normalized NBT semantics.
7. Repeat in the opposite direction.

Normalize compression/container artifacts before semantic comparison.

---

# 13. WORLD GENERATION — ABSOLUTE FIDELITY ZONE

World generation is one of the hardest subsystems to replicate exactly.

“Terrain looks similar” is not acceptable.

For a fixed seed and settings, compatibility should target identical or testably equivalent:

- biome placement,
- height field,
- caves,
- ravines,
- water/lava bodies,
- beaches,
- surface blocks,
- bedrock,
- ores,
- dirt/gravel patches,
- trees,
- grass,
- flowers,
- mushrooms,
- pumpkins,
- reeds,
- cactus,
- lakes,
- dungeons,
- mineshafts,
- villages,
- strongholds,
- temples,
- witch huts,
- Nether fortresses,
- ocean monuments,
- End terrain,
- spawn point behavior,
- and feature population order.

## 13.1 Do not use modern generation

Specifically reject:

- modern multi-noise biome placement,
- aquifers,
- noise caves,
- modern mountain system,
- modern ore veins,
- modern biome blending,
- modern structure sets,
- data-driven modern placed/configured features,
- modern surface rules.

## 13.2 World types to reproduce

Version-specific world type support must be verified and implemented according to 1.8.9 menus and generation logic, including applicable classic types such as:

- Default,
- Superflat,
- Large Biomes,
- AMPLIFIED,
- Customized,
- Debug behavior where exposed by the version.

Do not use modern menu names/settings as a substitute.

## 13.3 Golden seed corpus

Maintain a permanent corpus such as:

```text
0
1
-1
2
42
1337
123456789
-123456789
2147483647
-2147483648
Long.MAX_VALUE-style edge seeds
text-derived seeds
seeds chosen for village near spawn
seeds chosen for ocean monument
seeds chosen for stronghold geometry
seeds chosen for extreme hills
seeds chosen for mesa
seeds chosen for mushroom island
seeds chosen for ravine/cave interaction
```

For each seed, store hashes at increasing scopes:

```text
biome map hash
height map hash
chunk block/state hash
populated chunk hash
structure bounding boxes
feature coordinate list
spawn coordinate
```

## 13.4 Population-order tests

Chunk generation can depend on neighboring population/order interactions.

Test generating the same region in different chunk request orders.

Do not assume pure chunk-local generation.

---

# 14. BIOMES

The biome system must use the 1.8.9-era biome set, numeric IDs, temperatures, rainfall behavior, colors, spawn lists, terrain parameters, and decoration rules.

Compatibility requirements include:

- exact biome IDs,
- temperature behavior,
- rainfall/snow thresholds,
- grass tint,
- foliage tint,
- water color where applicable in the target era,
- creature spawn lists,
- passive/hostile spawning,
- terrain shape parameters,
- decorators,
- mutated/variant biome handling,
- biome edge rules,
- river generation,
- ocean transitions,
- climate layer behavior.

Do not import biomes added after 1.8.9.

---

# 15. BLOCK SYSTEM

Every block must be defined by behavior, not just appearance.

For each block/state, the registry should be capable of storing or deriving:

```yaml
legacy_id:
metadata_values:
registry_name:
material:
hardness:
blast_resistance:
light_emission:
light_opacity:
is_opaque_cube:
is_full_cube:
render_type:
collision_boxes:
selection_box:
movement_modifier:
flammability:
fire_spread:
harvest_tool:
harvest_level:
drops:
fortune_behavior:
silk_touch_behavior:
random_tick:
scheduled_tick:
neighbor_update:
placement_rules:
interaction_rules:
redstone_properties:
tile_entity_type:
sound_type:
map_color:
biome_tint_mode:
replaceability:
liquid_behavior:
entity_collision_behavior:
```

## 15.1 Block state fidelity

Metadata ordering matters.

Examples requiring careful state mapping include:

- stairs,
- slabs,
- doors,
- trapdoors,
- fence gates,
- rails,
- pistons,
- repeaters,
- comparators,
- redstone dust,
- crops,
- cocoa,
- vines,
- signs,
- banners,
- beds,
- double plants,
- logs,
- leaves,
- fluids,
- furnaces,
- chests,
- trapped chests,
- heads,
- flower pots.

## 15.2 Collision fidelity

Never simplify all blocks to cubes.

Collision shapes and selection shapes can differ.

Test:

- fences,
- walls,
- stairs,
- slabs,
- doors,
- trapdoors,
- panes,
- bars,
- ladders,
- vines,
- snow layers,
- farmland,
- cactus,
- chests,
- enchanting tables,
- cauldrons,
- brewing stands,
- hoppers,
- anvils,
- carpets,
- skulls,
- buttons,
- pressure plates.

Small AABB differences affect movement, suffocation, ray casts, and PvP.

---

# 16. BLOCK BREAKING AND PLACEMENT

Breaking and placement need exact 1.8.9 timing and validation.

Implement:

- reach validation,
- targeted face,
- line-of-sight/raycast behavior,
- hardness,
- tool effectiveness,
- harvest level,
- enchantment modifiers,
- potion modifiers,
- underwater penalties,
- airborne penalties,
- creative instant break rules,
- block damage progress,
- break animation stages,
- drop rules,
- durability cost,
- adventure restrictions,
- placement collision tests,
- replaceable blocks,
- orientation from player yaw/pitch,
- stack decrement,
- sound/particle timing,
- neighbor notifications.

The order of “place block → mutate inventory → notify neighbors → play sound → send packet” can matter in multiplayer and redstone tests.

---

# 17. FLUIDS

Water and lava are old scheduled-tick fluid systems, not modern fluid states.

Reproduce:

- source/flowing distinction,
- metadata levels,
- downward preference,
- sideways spread,
- tick delay,
- source creation rules,
- lava speed differences by dimension,
- water/lava interactions,
- obsidian/cobblestone/stone outcomes,
- entity current acceleration,
- drowning interactions,
- item movement,
- extinguishing,
- fire interaction.

Fluid update ordering is a common source of divergence.

---

# 18. LIGHTING

The old lighting system uses separate sky-light and block-light values in the 0–15 range.

Implement version-correct behavior for:

- light emission,
- opacity,
- propagation,
- removal,
- chunk boundaries,
- skylight columns,
- dimension skylight differences,
- day/night visual brightness,
- block-light color treatment in the old renderer,
- smooth lighting,
- ambient occlusion,
- gamma option behavior.

Do not substitute a physically based lighting engine and call it equivalent.

The renderer may internally be modern, but output must mimic old visual rules.

---

# 19. REDSTONE — HIGH-RISK COMPATIBILITY AREA

Redstone must be treated like a digital simulation with historical implementation quirks, not an abstract ideal circuit system.

Required components include:

- redstone wire,
- torches,
- repeaters,
- comparators,
- levers,
- buttons,
- pressure plates,
- tripwire,
- daylight sensors,
- trapped chests,
- hoppers,
- droppers,
- dispensers,
- pistons,
- sticky pistons,
- slime blocks,
- powered rails,
- detector rails,
- activator rails,
- doors,
- trapdoors,
- fence gates,
- lamps,
- note blocks,
- command blocks where relevant.

## 19.1 Preserve historical quirks

The project must explicitly investigate and reproduce 1.8.9 behavior for:

- quasi-connectivity,
- BUD-like update behavior,
- piston update order,
- redstone dust directional power,
- repeater locking,
- comparator update order,
- torch burnout,
- block-update propagation order,
- piston block limits,
- slime block movement rules,
- entity movement by slime/pistons,
- tile-entity movability restrictions,
- falling blocks interacting with redstone,
- observer absence.

Do not port a current-version redstone simulator.

## 19.2 Redstone test worlds

Maintain vanilla-authored regression worlds for:

- quasi-connectivity rigs,
- zero/one/two-tick pulse devices,
- piston extenders,
- torch burnout clocks,
- comparator containers,
- hopper clocks,
- item sorters,
- slime launchers,
- rail switches,
- door logic,
- edge cases near chunk borders.

Each device should have a machine-readable expected timeline.

---

# 20. ITEM SYSTEM

The item system is still pre-flattening.

An item stack may require:

```text
numeric item ID / block-item mapping
metadata/damage value
stack count
NBT compound
display name
lore
enchantments
repair cost
attributes where supported
potion data
book data
firework data
skull owner data
banner data
map ID
```

Implement exact:

- stack limits,
- durability,
- damage values,
- tool classes,
- food values,
- saturation,
- use duration,
- right-click behavior,
- block-use behavior,
- entity-use behavior,
- creative handling,
- item entity merge rules,
- despawn timing,
- pickup delay,
- pickup priority.

---

# 21. INVENTORY AND CONTAINERS

Inventory mechanics are one of the easiest places to create subtle multiplayer desyncs.

Replicate:

- player inventory slot numbering,
- armor slots,
- hotbar,
- crafting grid,
- cursor stack,
- chest layouts,
- double chest layout,
- furnace,
- hopper,
- dispenser/dropper,
- brewing stand,
- enchanting table,
- anvil,
- beacon,
- villager trading,
- horse inventory where applicable,
- creative inventory.

Input behavior must cover:

- normal click,
- right click,
- shift-click,
- number-key hotbar swap,
- middle click in creative,
- dropping from slot,
- dropping outside GUI,
- double-click collect,
- drag distribution modes,
- slot validation,
- transaction acknowledgement,
- server rejection/resync.

Do not reuse modern inventory code without a full compatibility layer.

---

# 22. CRAFTING

Crafting compatibility requires the exact 1.8.9 recipe set and matching algorithm.

Support:

- shaped recipes,
- mirrored shaped recipes where applicable,
- shapeless recipes,
- metadata-sensitive ingredients,
- container-item results,
- remaining items,
- recipe ordering where ambiguity exists,
- 2×2 player crafting,
- 3×3 crafting table.

There is no modern recipe book UX.

Build a recipe corpus from version-specific data and validate every output.

---

# 23. FURNACES

Replicate:

- smelting recipes,
- fuel values,
- fuel consumption timing,
- cook timing,
- XP behavior,
- container items,
- lit/unlit furnace state,
- hopper interaction,
- comparator output,
- GUI progress scaling,
- save/load mid-smelt.

Tick boundaries matter.

---

# 24. ENCHANTING

1.8-era enchanting includes the lapis-based system introduced in the 1.8 generation.

Reproduce:

- enchanting table bookshelf detection,
- offered level calculation,
- lapis cost,
- XP level cost,
- clue enchantment display,
- enchantment seed behavior,
- item enchantability,
- weighted enchant selection,
- compatibility exclusions,
- treasure restrictions appropriate to the era,
- book handling,
- client/server synchronization.

Enchantment outputs must be tested with fixed player seed/state.

---

# 25. ANVILS

Reproduce old anvil mechanics exactly:

- rename behavior,
- repair using material,
- combining durability,
- combining enchantments,
- enchantment compatibility,
- prior-work/repair-cost handling,
- level cost,
- “too expensive” behavior,
- creative exceptions,
- anvil damage chance,
- falling-anvil behavior.

Avoid importing modern anvil rules.

---

# 26. BREWING AND POTIONS

Use the 1.8.9 potion model and recipes.

Reproduce:

- brewing stand slot layout,
- ingredient consumption,
- bottle types,
- potion metadata,
- splash conversion,
- duration/amplifier transformations,
- corrupting ingredients,
- potion colors,
- drinking duration,
- splash radius and falloff,
- effect stacking/replacement,
- instant effects,
- particles,
- effect HUD.

Do not use post-1.9 potion additions or tipped-arrow assumptions.

---

# 27. PLAYER ENTITY

The player is a special entity with several compatibility-critical systems.

Implement and test:

- position,
- previous position,
- yaw,
- pitch,
- eye height,
- bounding box,
- camera height,
- movement input,
- walking,
- sprinting,
- sneaking,
- jumping,
- falling,
- ladders,
- vines,
- water,
- lava,
- webs,
- ice,
- soul sand,
- knockback,
- step-up behavior,
- collision pushing,
- swimming-era water movement without modern swim pose,
- fall distance,
- health,
- absorption,
- armor,
- hunger,
- saturation,
- exhaustion,
- air,
- fire ticks,
- potion effects,
- XP,
- level,
- score,
- spawn point,
- sleeping,
- death,
- respawn,
- portals,
- riding,
- creative flight,
- spectator behavior.

## 27.1 Sneaking warning

Do not assume modern sneaking pose/hitbox rules.

Measure 1.8.9 eye height, camera offset, edge prevention, movement speed, and collision behavior directly.

## 27.2 Movement constants

Do not rely on remembered constants without tests.

Lock down by experiment:

- walk acceleration,
- sprint modifier,
- sneak modifier,
- jump impulse,
- gravity,
- air drag,
- ground friction,
- water acceleration,
- lava acceleration,
- ladder cap,
- step height,
- sprint-jump boost,
- knockback friction,
- ice slipperiness.

Store motion state each tick for comparison.

---

# 28. COLLISION ENGINE

Collision fidelity must use swept or stepwise behavior equivalent to vanilla’s axis-aligned collision resolution.

Test:

- X/Y/Z resolution ordering,
- stepping onto blocks,
- corners,
- ceilings,
- fences,
- slabs,
- stairs,
- doors,
- ladders,
- water edges,
- boats,
- minecarts,
- entity pushing,
- piston movement,
- suffocation,
- world border,
- negative coordinates.

Small differences accumulate into obvious PvP and parkour divergence.

---

# 29. RAY CASTING / TARGETING

Implement 1.8.9-style block and entity targeting.

Compatibility surfaces:

- block reach,
- entity reach,
- creative versus survival reach differences where applicable,
- eye position,
- partial ticks,
- block collision/selection shape,
- liquid targeting rules,
- entity AABB expansion used for hit detection,
- nearest-hit selection,
- tie behavior,
- spectator behavior.

Do not “improve” hit detection.

---

# 30. COMBAT — PRE-1.9 RULESET

Combat must feel and behave like real 1.8.9, especially for PvP.

Core requirements:

- **no attack cooldown**,
- **no sweeping attack**,
- **no shields**,
- **sword blocking exists**,
- old weapon damage values,
- old armor model,
- old Sharpness/Strength behavior,
- old sprint-knockback interaction,
- old critical-hit logic,
- old hurt-resistance/invulnerability handling,
- old projectile interactions,
- old fishing-rod combat behavior,
- old snowball/egg entity interaction,
- old golden-apple effects,
- no modern attack-speed attribute gameplay.

## 30.1 Sword blocking

Right-clicking with a sword enters the old blocking/use state.

Sword blocking reduces applicable incoming damage according to the 1.8-era rule set; the commonly documented baseline is a 50% reduction for blockable damage.

Exact applicability, animation, movement effect, item-use timing, block-hitting interactions, projectiles, fire effects, and cancellation cases must be validated against vanilla.

## 30.2 Hurt resistance

Repeated hits are not simply “every click deals damage.”

The target’s hurt-resistance/invulnerability behavior is critical to CPS, combo, projectile, and damage-over-time fidelity.

Record per-tick:

```text
health
last damage
hurt time
hurt resistant time
velocity
onGround
attacker sprint state
```

## 30.3 Sprint knockback

PvP fidelity depends on accurately reproducing the 1.8-era sprint-hit behavior and sprint reset interactions.

W-tapping, S-tapping, block-hitting, and combo behavior emerge from the underlying state machine. Do not implement them as named special moves.

## 30.4 Critical hits

Implement the exact vanilla condition predicate.

Test combinations of:

- falling,
- on ground,
- ladder,
- water,
- blindness,
- riding,
- sprinting,
- target type,
- jump state.

Do not use current-version critical conditions.

## 30.5 Armor

Use the pre-1.9 armor system.

Do not add armor toughness.

Test armor points, enchantments, durability loss, unblockable sources, projectiles, explosions, fire, fall damage, potion damage, and void damage separately.

---

# 31. PROJECTILES

Implement separate physics/impact behavior for:

- arrows,
- fishing hook,
- snowballs,
- eggs,
- ender pearls,
- splash potions,
- experience bottles,
- fireballs,
- small fireballs,
- wither skulls,
- thrown items used by entities where applicable.

For each projectile, test:

- spawn offset,
- launch velocity,
- inaccuracy,
- gravity,
- drag,
- water drag,
- collision raycast,
- entity hitbox expansion,
- shooter immunity window,
- bounce/stick behavior,
- despawn,
- fire,
- knockback,
- damage,
- critical flag,
- pickup state,
- serialization,
- network interpolation.

---

# 32. HUNGER, SATURATION, EXHAUSTION, REGENERATION

Do not use modern hunger logic.

Implement the target era’s:

- food level,
- saturation,
- exhaustion accumulation,
- sprint restriction,
- natural regeneration,
- starvation,
- difficulty dependence,
- food item values,
- potion interaction.

Create tick-by-tick tests for long-duration regeneration and starvation.

---

# 33. DAMAGE SYSTEM

Damage sources must remain distinguishable.

Examples:

- player melee,
- mob melee,
- projectile,
- fall,
- fire,
- lava,
- drowning,
- suffocation,
- cactus,
- explosion,
- magic,
- potion,
- starvation,
- anvil,
- falling block,
- void,
- lightning.

Each source may interact differently with:

- armor,
- blocking,
- enchantments,
- potions,
- knockback,
- hurt animation,
- death message,
- statistics,
- attribution.

---

# 34. STATUS EFFECTS

Implement only the effects present in the target version, with target-era semantics.

For each effect:

- numeric ID,
- amplifier,
- duration,
- ambient flag,
- particle behavior,
- color,
- stacking/replacement rules,
- tick interval,
- attribute changes,
- instant-effect handling,
- milk removal behavior,
- death persistence behavior.

Avoid importing later effects.

---

# 35. ENTITIES — GENERAL CONTRACT

Every entity type needs a compatibility definition containing:

```yaml
entity_type:
numeric_type_id:
spawn_packet_type:
width:
height:
eye_height:
tracking_range:
update_frequency:
velocity_tracking:
health:
movement_type:
gravity:
drag:
collision:
fire_rules:
water_rules:
fall_rules:
loot:
xp:
metadata_fields:
nbt_fields:
ai_tasks:
target_tasks:
sounds:
particles:
```

Do not assume dimensions or metadata indexes from modern versions.

---

# 36. MOB AI

The AI must reproduce the task-based 1.8-era behavior rather than a modern AI stack.

Test:

- idle wandering,
- look behavior,
- target acquisition,
- line of sight,
- pathfinding,
- door interaction,
- avoidance,
- panic,
- temptation,
- breeding,
- following parent,
- retaliation,
- nearest-player targeting,
- ranged attack cadence,
- melee attack cadence,
- sunlight behavior,
- water avoidance,
- village interactions,
- despawn logic.

AI fidelity should be compared statistically over deterministic replay where exact paths depend on RNG.

---

# 37. PATHFINDING

Do not substitute a generic A* and assume equivalence.

Version-specific path behavior depends on:

- node classification,
- step height,
- entity size,
- doors,
- water,
- lava,
- fences,
- rails,
- trapdoors,
- sunlight avoidance,
- path search budget,
- heuristic,
- node ordering,
- path smoothing,
- repath cadence.

Create maze fixtures with known vanilla routes.

---

# 38. ENTITY CATALOG CHECKLIST

At minimum, the implementation plan must account for all vanilla 1.8.9 categories, including:

- players,
- dropped items,
- XP orbs,
- paintings,
- item frames,
- armor stands,
- boats,
- minecarts and minecart variants,
- arrows,
- thrown projectiles,
- fishing hooks,
- falling blocks,
- TNT,
- lightning,
- hostile mobs,
- passive mobs,
- tameable mobs,
- villagers,
- iron golems,
- snow golems,
- bosses,
- Guardians/Elder Guardians,
- Rabbits,
- Endermites,
- and all entities inherited from earlier versions still present in 1.8.9.

Maintain a registry completeness test so no entity type is forgotten.

---

# 39. MOB SPAWNING

Reproduce target-era mob spawning, including:

- hostile/passive/water/ambient categories,
- eligible chunks around players,
- light checks,
- block checks,
- biome spawn lists,
- pack sizes,
- mob caps,
- distance from player,
- distance from world spawn,
- difficulty,
- dimension rules,
- despawn distances,
- persistence flags,
- name-tag persistence,
- structure-specific spawning.

Do not borrow modern mob-cap or simulation-distance logic.

---

# 40. WEATHER AND DAYLIGHT

Implement:

- day/night cycle,
- sun angle,
- moon phases,
- sky brightness,
- rain,
- thunder,
- lightning,
- biome precipitation rules,
- snow accumulation where target behavior allows,
- fire extinguishing,
- mob effects,
- sky/fog response,
- sound/particle effects,
- sleep weather reset behavior.

Weather timers and random selection must use target-era logic.

---

# 41. FIRE

Fire behavior must be version-specific.

Test:

- spread chance,
- encouragement/flammability,
- rain extinguishing,
- block destruction,
- Nether behavior,
- netherrack eternal fire,
- portal creation interaction,
- TNT ignition,
- entity ignition,
- lava ignition.

---

# 42. PORTALS

## Nether portals

Reproduce:

- frame validation,
- portal block placement,
- orientation,
- entity portal timer,
- coordinate scaling,
- target portal search radius,
- portal creation,
- safe placement,
- momentum/orientation handling,
- cooldown.

## End portals

Reproduce:

- frame activation,
- eye state,
- portal block creation,
- teleport behavior,
- End spawn platform,
- return portal behavior,
- dragon/fountain interactions appropriate to 1.8.9.

---

# 43. THE END AND ENDER DRAGON

Do not use modern dragon-fight behavior.

Reproduce the 1.8.9-era:

- End terrain,
- obsidian pillars,
- crystals,
- dragon AI/phase system as it existed then,
- damage zones,
- healing,
- perch/flight behavior,
- death sequence,
- XP,
- exit portal,
- dragon egg behavior,
- End credits trigger.

Later End Cities, gateways, chorus plants, and 1.9 End changes must not leak in.

---

# 44. NETHER

Reproduce target-era Nether generation and mechanics:

- terrain noise,
- bedrock roof/floor,
- lava sea,
- glowstone,
- quartz ore,
- soul sand/gravel patches,
- mushrooms,
- fire,
- Nether fortresses,
- fortress mobs,
- dimension lighting,
- portal scaling,
- water placement restrictions,
- bed explosion.

No modern Nether biomes or structures.

---

# 45. VILLAGES AND VILLAGERS

Use the pre-Village-&-Pillage system.

Reproduce:

- old village structures,
- village detection,
- doors as village anchors,
- population logic,
- golem spawning,
- zombie sieges where applicable,
- old villager professions,
- old trading mechanics,
- reputation/gossip only as existed in the target era,
- breeding conditions,
- farming behavior where present.

Do not import workstations, points of interest, or modern profession mechanics.

---

# 46. ACHIEVEMENTS AND STATISTICS

1.8.9 uses the old achievement/statistics system, not advancements.

Replicate applicable:

- achievement tree,
- unlock dependencies,
- toast/GUI behavior of the era,
- statistic counters,
- per-item/per-block stats,
- save format,
- multiplayer update behavior.

---

# 47. COMMANDS

Implement only target-era commands and target-era syntax.

Version-lock:

- command names,
- permissions,
- argument parsing,
- target selectors,
- selector arguments,
- coordinates,
- relative coordinates,
- command-block behavior,
- tab completion,
- error messages,
- output formatting,
- gamerules,
- command result statistics.

Do not use Brigadier or modern command syntax internally unless a compatibility parser faithfully reproduces 1.8.9.

---

# 48. GAMERULES AND DIFFICULTY

Maintain an exact catalog of 1.8.9 gamerules and their defaults.

Difficulty affects multiple systems and must be centralized:

- mob damage,
- starvation,
- hostile spawning,
- status/equipment probabilities,
- regional difficulty where applicable,
- peaceful despawning/healing behavior.

No gamerule introduced after 1.8.9 should appear by default.

---

# 49. MULTIPLAYER PROTOCOL 47

Protocol fidelity must be version-locked to 47.

Do not rely on a modern protocol enum.

The connection state machine includes:

```text
HANDSHAKE
  -> STATUS
  -> LOGIN
  -> PLAY
```

## 49.1 Handshake

The initial handshake contains the protocol version and chooses next state.

For 1.8.9, protocol version is 47.

## 49.2 Status / server list ping

Support the 1.7+ JSON status flow used by 1.8.x:

- handshake requesting status,
- status request,
- JSON status response,
- ping,
- pong.

Reproduce:

- version name/protocol,
- MOTD chat component behavior,
- online/max players,
- optional sample players,
- favicon encoding,
- latency measurement behavior.

## 49.3 Login

Implement target-era login state:

- login start,
- online/offline mode distinction,
- encryption request/response,
- shared secret,
- server authentication flow,
- optional compression negotiation,
- login success,
- transition to PLAY.

Encryption behavior in this era uses the classic Minecraft authenticated-login scheme with RSA key exchange and encrypted stream semantics.

## 49.4 Packet framing

Version-lock:

- VarInt encoding,
- packet length framing,
- compression threshold behavior,
- packet ID per state and direction,
- field ordering,
- string limits,
- position encoding,
- slot/NBT encoding,
- entity metadata encoding.

## 49.5 PLAY packet completeness

Create a generated registry covering **every protocol-47 PLAY packet in both directions**.

No “unknown packet ignored” behavior for packets vanilla actually uses during normal play.

Categories include:

### Client → server

- keepalive response,
- chat,
- use entity,
- player movement variants,
- digging,
- placement/use item,
- held-item change,
- animation,
- entity action,
- steer vehicle,
- window close,
- window click,
- transaction confirmation,
- creative inventory action,
- enchant item,
- update sign,
- player abilities,
- tab completion,
- client settings,
- client status,
- plugin messages,
- spectator interactions/resource-pack status where applicable to the protocol.

### Server → client

- keepalive,
- join game,
- chat,
- time update,
- entity equipment,
- spawn position,
- health/food,
- respawn,
- player position/look,
- held item,
- bed use,
- animation,
- player/entity spawning,
- collect item,
- velocity,
- destroy entities,
- movement/teleport,
- entity head look,
- entity status,
- attach/mount,
- entity metadata,
- entity effects,
- remove effect,
- XP,
- entity properties,
- chunk data,
- multi-block change,
- block change,
- block action,
- block break animation,
- map chunk bulk,
- explosion,
- effects,
- particles,
- game state change,
- global entity,
- windows/inventory,
- signs,
- maps,
- tile entity updates,
- open sign editor,
- statistics,
- player list/tab data,
- player abilities,
- tab completion,
- scoreboard/objective/team/display packets,
- plugin messages,
- disconnect,
- server difficulty,
- combat event,
- camera,
- world border,
- title,
- compression/resource-pack related packets where appropriate.

The exact packet list and IDs must be generated from a protocol-47 reference table and covered by serialization round-trip tests.

---

# 50. CHUNK NETWORK FORMAT

Protocol-47 chunk transfer is not the modern palette format.

For each sent section, the old network representation carries block state data for 4096 positions plus nibble-packed light arrays.

A full chunk transmission also includes biome bytes.

Version-specific documentation should be used to lock down:

- primary bit mask,
- section ordering,
- block-state packing,
- block-light ordering,
- sky-light presence,
- biome bytes,
- compression/framing,
- dimension-dependent skylight behavior.

Round-trip chunk packets against a vanilla client and server.

---

# 51. SERVER AUTHORITY AND PREDICTION

The vanilla client performs local movement prediction and local visual actions while the server remains authoritative for game state.

Replicate reconciliation behavior:

- server position corrections,
- teleport flags,
- inventory transaction rejection,
- block correction,
- entity correction,
- latency effects,
- interpolation,
- keepalive.

A replica that works only at 0 ms localhost is not multiplayer-complete.

Test at artificial latency/jitter/loss profiles.

---

# 52. DEDICATED SERVER

If implementing a dedicated server, reproduce target-era semantics for:

- `server.properties`,
- online mode,
- difficulty,
- gamemode,
- hardcore,
- PvP,
- spawn protection,
- allow-flight,
- view distance,
- whitelist,
- ops,
- player limit,
- MOTD,
- command blocks,
- RCON/query if in project scope,
- autosave,
- console commands,
- graceful shutdown,
- watchdog behavior where relevant.

Use a target-version properties catalog, not a current one.

---

# 53. RESOURCE PACKS

Target resource-pack format is **1**.

Implement 1.8.9-era resource lookup and overrides.

Compatibility surfaces include:

- `pack.mcmeta`,
- pack description,
- pack icon,
- asset namespace,
- textures,
- sounds,
- language files,
- font/glyph resources where used,
- block models,
- item models,
- blockstates,
- texture animations,
- colormaps,
- resource reload,
- pack ordering,
- default assets vs overrides.

Do not silently accept modern pack formats as if they were 1.8.9 unless an explicit compatibility extension is enabled.

---

# 54. 1.8 MODEL SYSTEM

1.8 introduced the JSON model/blockstate era that predates later refinements.

Implement target-version behavior for:

- model parent inheritance,
- texture variables,
- elements/cuboids,
- face UVs,
- cullface,
- tint index,
- rotations,
- blockstate variants,
- weighted variants,
- item display transforms,
- built-in/generated item models,
- missing-model behavior.

Do not assume 1.9+ multipart/model semantics are available.

---

# 55. TEXTURE ATLAS AND SAMPLING

For visual fidelity, lock down:

- atlas stitching,
- sprite UV padding,
- animated texture frame timing,
- mipmap settings,
- texture filtering,
- alpha behavior,
- clamping/repeat behavior,
- texture bleeding,
- block breaking overlay,
- enchantment glint.

The goal is to reproduce the old visual result, even if the backend is Vulkan/Direct3D/modern OpenGL.

---

# 56. BLOCK RENDERING

Separate render layers and behaviors as 1.8.9 expects.

Account for:

- opaque/cutout/translucent geometry,
- culling against neighboring blocks,
- connected-looking geometry created by state rather than texture magic,
- fluid faces,
- biome tint,
- smooth lighting,
- AO,
- random model variants,
- offset models where used,
- tile-entity special renderers.

Do not use modern render-layer assumptions unverified.

---

# 57. ENTITY RENDERING

For every entity, validate:

- model dimensions,
- pivot points,
- animation frequency,
- walking limb swing,
- head look,
- hurt animation,
- death rotation,
- child scaling,
- held-item transform,
- armor layers,
- name tag,
- shadow size,
- fire overlay,
- invisibility,
- potion glint/particles,
- leash,
- arrows stuck in entity.

---

# 58. FIRST-PERSON RENDERING

This is critical to “feels like 1.8.9.”

Reproduce:

- hand position,
- item transform,
- equip animation,
- swing animation,
- block animation,
- bow animation,
- eating/drinking animation,
- map rendering,
- item use animation,
- fire overlay,
- water overlay,
- suffocation overlay,
- view bobbing,
- hurt camera effect,
- FOV modifiers from sprinting/bow/speed/slowness.

Modern first-person transforms will immediately make the replica feel wrong.

---

# 59. CAMERA

Lock down:

- eye interpolation,
- yaw/pitch interpolation,
- third-person offset,
- third-person collision,
- front-facing third-person mode,
- spectator camera,
- sleeping camera,
- death camera,
- view bobbing,
- hurt tilt,
- portal distortion,
- nausea distortion,
- FOV setting curve,
- sprint FOV change.

---

# 60. SKY, FOG, CLOUDS, CELESTIALS

Reproduce target-era:

- sky gradient,
- horizon,
- sunrise/sunset colors,
- sun quad,
- moon phases,
- stars,
- cloud layer,
- cloud mode fast/fancy,
- fog distance,
- fog color,
- water fog,
- lava fog,
- blindness fog,
- void darkness,
- rain/snow rendering,
- dimension-specific sky.

Do not substitute a physically based atmospheric model.

---

# 61. PARTICLES

Maintain a version-specific particle registry.

For each particle:

- spawn conditions,
- position jitter,
- velocity,
- gravity,
- lifetime,
- texture index,
- color,
- scale,
- collision,
- brightness,
- blend mode,
- count/density setting.

Particles can be gameplay-significant for potion/critical/hit feedback.

---

# 62. AUDIO

Audio fidelity requires an event-driven system matching 1.8.9 behavior.

For each sound event record:

- sound resource,
- category if applicable in the era,
- volume,
- pitch,
- random pitch variation,
- positional attenuation,
- maximum distance,
- looping,
- stop conditions,
- event timing.

Reproduce:

- block step/break/place,
- entity idle/hurt/death,
- combat,
- projectiles,
- explosions,
- weather,
- portals,
- UI clicks,
- XP,
- level-up,
- music cadence,
- ambient cave sounds.

Do not replace old sounds with modern equivalents.

---

# 63. GUI FOUNDATION

Minecraft 1.8.9’s interface is resolution-scaled, texture-driven, and pixel-oriented.

Reproduce:

- scaled resolution calculation,
- GUI scale options,
- mouse coordinate conversion,
- button dimensions,
- hover state,
- disabled state,
- focus,
- text fields,
- sliders,
- scroll lists,
- slot rendering,
- tooltip positioning,
- screen darkening/background,
- panorama/title background behavior,
- dirt-style backgrounds where used.

Every GUI should be screenshot-tested at multiple window resolutions and GUI scales.

---

# 64. HUD

HUD fidelity includes:

- crosshair,
- hotbar,
- selected slot,
- hearts,
- absorption hearts,
- armor icons,
- hunger icons,
- air bubbles,
- XP bar,
- XP level,
- held-item name,
- boss health,
- scoreboard sidebar,
- player list/tab overlay,
- chat,
- potion-effect indicators as implemented in 1.8.9,
- spectator HUD,
- vignette,
- portal overlay,
- pumpkin overlay.

Icon flashing and damage animation timing must be tested, not eyeballed.

---

# 65. FONT AND TEXT RENDERING

Text differences make a clone look wrong immediately.

Lock down:

- default glyph source,
- character widths,
- formatting codes,
- colors,
- bold width behavior,
- italic skew,
- underline,
- strikethrough,
- obfuscated text,
- shadow offset/color,
- Unicode mode,
- line wrapping,
- chat component formatting,
- tooltip wrapping.

Do not use a generic TrueType font as the default replacement if pixel fidelity is desired.

---

# 66. TITLE SCREEN AND MENUS

Reproduce the target-era screen graph, not the current game’s UI.

At minimum cover:

- title screen,
- splash text behavior,
- Singleplayer,
- world selection,
- create world,
- edit/delete/re-create world,
- Multiplayer,
- server list,
- direct connect,
- add/edit server,
- Realms entry points appropriate to 1.8.9,
- Options,
- video settings,
- controls,
- language,
- resource packs,
- sound/music settings,
- skin customization,
- snooper settings if present,
- accessibility only if actually present in the era,
- pause menu,
- disconnect screen,
- death screen,
- game-over/hardcore behavior.

1.8.9-specific Realms notification settings must not be accidentally omitted if the goal includes menu completeness.

---

# 67. DEBUG SCREEN / F3

The F3 debug screen is a valuable compatibility oracle and should be recreated if full client fidelity is desired.

Version-lock the fields shown in 1.8.9, their labels, ordering, rounding, and toggles.

Do not display modern biome/resource-location/debug fields that did not exist then.

Use vanilla screenshots as golden references.

---

# 68. INPUT SYSTEM

Replicate:

- keyboard state,
- mouse buttons,
- mouse delta,
- sensitivity curve,
- invert mouse,
- key binding conflicts,
- attack/use/pick block,
- sprint key,
- sneak key,
- jump,
- inventory,
- drop,
- chat,
- command,
- player list,
- screenshot,
- fullscreen,
- perspective toggle,
- smooth camera where available,
- debug shortcuts.

Input sampling timing matters for PvP and movement.

---

# 69. WINDOW / FOCUS / PAUSE BEHAVIOR

Singleplayer integrated-server behavior differs from multiplayer when paused.

Test:

- ESC pause,
- losing window focus,
- “pause on lost focus” behavior if configurable/present,
- opening inventory,
- opening chat,
- death screen,
- integrated server pausing,
- LAN opening.

Do not pause a multiplayer server because the client opened a menu.

---

# 70. INTEGRATED SERVER

Singleplayer in this era uses a client plus integrated server architecture.

If reproducing this architecture, preserve the consequences:

- server-authoritative simulation,
- LAN hosting,
- packet-style synchronization internally or an equivalent boundary,
- pause behavior,
- world save behavior,
- command permissions,
- difficulty lock/state.

A single-threaded “client owns everything” shortcut may diverge in subtle ways.

---

# 71. GAME MODES

Implement target-era behavior for:

- Survival,
- Creative,
- Adventure,
- Spectator,
- Hardcore as a world mode modifier.

Differences include:

- damage,
- hunger,
- flight,
- block breaking,
- block drops,
- interaction restrictions,
- inventory,
- reach,
- item use,
- spectator camera/entity possession,
- death handling.

---

# 72. SPECTATOR MODE

Spectator was present in the 1.8 era and must not be treated like modern spectator by assumption.

Validate:

- noclip,
- invisibility,
- interaction restrictions,
- flying speed,
- scroll speed control,
- teleport/player menu,
- entity camera possession,
- rendering while inside blocks,
- network behavior.

---

# 73. CREATIVE MODE

Reproduce:

- creative inventory tabs,
- search behavior,
- item ordering,
- pick block,
- middle-click clone behavior,
- instant block breaking,
- flight,
- double-tap flight toggle,
- creative inventory packet behavior,
- drop/delete behavior,
- command block acquisition restrictions appropriate to the era.

Exact item ordering is part of UI fidelity.

---

# 74. PLAYER SKINS AND MODELS

1.8-era skin support includes classic and slim player arm models.

Reproduce target-era:

- skin dimensions/layout,
- outer skin layers,
- cape rendering if supported by account data,
- slim/classic arm selection,
- skin-part toggles,
- first-person arm rendering,
- interpolation/model animation.

Authentication/profile texture fetching may be isolated behind a service abstraction so modern account infrastructure changes do not contaminate gameplay protocol fidelity.

---

# 75. CHAT

Replicate target-era chat components and UI behavior:

- incoming chat packet positions/types,
- formatting codes,
- click/hover events supported in the era,
- wrapping,
- opacity,
- scale,
- width,
- height,
- fade timing,
- history,
- sent-message history,
- command input,
- tab completion,
- links prompt behavior,
- multiplayer filtering only as actually present then.

Do not implement signed-chat requirements.

---

# 76. SCOREBOARDS AND TEAMS

Support protocol/game behavior for:

- objectives,
- scores,
- display slots,
- teams,
- prefixes/suffixes,
- friendly fire,
- visibility settings supported by the era,
- nametag coloring,
- sidebar/list/below-name display.

These are heavily used by 1.8-era multiplayer servers.

---

# 77. SERVER LIST / MULTIPLAYER UX

Version-lock:

- server entries,
- ping icon stages,
- MOTD rendering,
- player count hover,
- version mismatch text,
- server icon,
- reorder behavior,
- direct connect,
- refresh cadence,
- connection cancellation,
- disconnect reasons.

This is a major compatibility surface for a 1.8.9 client replacement.

---

# 78. LOADING AND WORLD TRANSITIONS

Reproduce the visible and state-machine behavior of:

- world generation screen,
- terrain download/loading screen,
- dimension change,
- respawn,
- server join,
- server disconnect,
- resource reload,
- resource pack download where applicable.

Avoid exposing partially initialized world state.

---

# 79. PERFORMANCE WITHOUT BEHAVIORAL DRIFT

Optimization is allowed only when it preserves externally observable results.

Examples of risky optimizations:

- parallelizing worldgen and changing RNG/order,
- multithreading redstone updates,
- sorting entity updates differently,
- collapsing scheduled ticks,
- approximating collision shapes,
- changing floating-point precision,
- batching packet sends in ways that change timing,
- using unordered hash iteration when vanilla order matters.

When optimizing, run the differential suite before and after.

---

# 80. THREADING

The original architecture has strong main-thread assumptions.

A replica may use modern concurrency internally, but authoritative mutation should be deterministic.

Define ownership for:

- world state,
- chunks,
- entities,
- networking queues,
- renderer snapshots,
- save I/O,
- resource loading.

Never allow race timing to alter gameplay outcomes.

---

# 81. DATA-DRIVEN REGISTRIES

Where practical, put version-specific facts in generated data rather than scattered code.

Recommended generated registries:

```text
blocks.json
items.json
biomes.json
entities.json
sounds.json
particles.json
enchantments.json
potions.json
recipes.json
smelting.json
fuels.json
commands.json
packets_protocol_47.json
gamerules.json
statistics.json
achievements.json
```

Each entry should include provenance and verification state.

---

# 82. DIFFERENTIAL VANILLA ORACLE

The most important tool in this project is a repeatable way to compare vanilla and replica.

Build a test harness that can run the same scenario on both implementations.

A scenario should describe:

```yaml
name:
world_seed:
world_type:
difficulty:
gamemode:
initial_player_state:
initial_world_edits:
input_timeline:
packet_injections:
duration_ticks:
capture_points:
expected_outputs:
```

Capture outputs such as:

```text
player position/velocity
player health/hunger
inventory
world block states
scheduled ticks
entities
entity metadata
RNG checkpoints where available
packet trace
chunk hashes
NBT snapshots
screenshots
audio-event log
particles
```

---

# 83. REPLAY FORMAT

Create a deterministic replay format early.

Example conceptual structure:

```json
{
  "target": "java-1.8.9",
  "seed": 12345,
  "settings": {},
  "events": [
    {"tick": 0, "type": "key", "key": "forward", "down": true},
    {"tick": 10, "type": "key", "key": "jump", "down": true},
    {"tick": 11, "type": "key", "key": "jump", "down": false},
    {"tick": 20, "type": "mouse_button", "button": 0, "down": true}
  ]
}
```

Replay must distinguish tick-time events from frame-time events where necessary.

---

# 84. STATE HASHING

Use normalized hashes to detect divergence quickly.

Suggested hierarchy:

```text
world hash
  dimension hash
    chunk hash
      section block hash
      light hash
      entity hash
      tile entity hash
      scheduled tick hash
player hash
inventory hash
scoreboard hash
```

A mismatch should be bisectable to the first divergent tick.

---

# 85. SCREENSHOT FIDELITY TESTING

Use screenshot testing for presentation systems.

Capture:

- exact resolution,
- exact GUI scale,
- exact FOV,
- fixed camera,
- fixed world time,
- fixed weather,
- same resource pack,
- same entity state,
- same partial tick where possible.

Compare:

1. raw pixel diff,
2. masked diff for nondeterministic regions,
3. structural/perceptual diff for GPU precision noise,
4. manually reviewed reference images for important screens.

Never use perceptual similarity alone to certify simulation correctness.

---

# 86. NETWORK GOLDEN TESTING

Build packet fixtures from legitimate captures of vanilla behavior.

For each packet:

- decode vanilla bytes,
- re-encode and compare,
- test min/max values,
- test malformed input handling if compatibility requires it,
- test compression on/off,
- test encrypted stream,
- test packet fragmentation across TCP reads,
- test multiple packets in one TCP read.

TCP packet boundaries are not Minecraft packet boundaries.

---

# 87. NBT GOLDEN TESTING

Maintain a corpus of real 1.8.9 NBT:

- level.dat,
- player data,
- chunk with no entities,
- chunk with tile entities,
- chest,
- furnace mid-smelt,
- brewing stand mid-brew,
- enchanting table state if saved,
- villager trades,
- horse inventory,
- armor stand,
- item with complex NBT,
- written book,
- firework,
- banner,
- skull,
- map data,
- scoreboard.

Test read → normalize → write → read.

---

# 88. BUG COMPATIBILITY REGISTRY

Create `compat/bugs_1_8_9.yml`.

Each entry:

```yaml
id: local-or-mojang-bug-id
name:
subsystem:
observed_behavior:
reproduction_steps:
vanilla_1_8_9: true
later_fixed: true/false/unknown
gameplay_relevant: true/false
replicate: true/false
reason:
tests:
```

This prevents one agent from “fixing” a behavior another agent intentionally reproduced.

---

# 89. UNCERTAINTY REGISTRY

Create `compat/unknowns.yml`.

Each unresolved detail should include:

```yaml
question:
why_it_matters:
current_hypothesis:
evidence:
vanilla_test_needed:
owner:
status:
```

No hidden assumptions.

---

# 90. AGENT WORK CONTRACT

Every coding agent must begin a task by answering internally:

1. Which exact 1.8.9 behavior am I reproducing?
2. What evidence establishes the behavior?
3. What modern behavior am I at risk of accidentally importing?
4. Which deterministic tests will prove compatibility?
5. Which neighboring subsystems can this change affect?

Every implementation task should end with:

```text
Implementation completed
Evidence used
Tests added
Vanilla comparison performed
Known mismatches
Unknowns created
No-modernization review
```

---

# 91. AGENT TASK TEMPLATE

Use this format when assigning work:

```markdown
## Task
Implement <subsystem> for Minecraft Java 1.8.9 fidelity.

## Target behavior
<observable behavior only>

## Evidence
- Vanilla experiment:
- Version-specific docs:
- Existing golden files:

## Compatibility constraints
- Must preserve:
- Must not import from later versions:

## Required tests
1.
2.
3.

## Acceptance criteria
- Vanilla and replica agree on ...
- Packet/state/screenshot hash matches ...
- No unresolved behavior hidden behind TODOs.

## Deliverables
- implementation
- tests
- evidence note
- updates to uncertainty/bug registries
```

---

# 92. DEFINITION OF DONE

A subsystem is **not done** when it merely compiles.

A subsystem is done when:

- API/architecture exists,
- version-specific behavior is implemented,
- automated tests exist,
- at least one vanilla differential test exists for important behavior,
- edge cases are covered,
- multiplayer synchronization is covered if relevant,
- save/load is covered if relevant,
- rendering is covered if relevant,
- no modern mechanics leaked in,
- remaining unknowns are explicitly registered,
- performance changes have not altered results.

---

# 93. FIDELITY SEVERITY LEVELS

Classify mismatches:

## P0 — identity-breaking

Examples:

- wrong protocol,
- wrong worldgen,
- wrong movement physics,
- wrong combat,
- wrong block IDs/states,
- wrong redstone ordering,
- vanilla worlds corrupt,
- client/server desync.

## P1 — major visible/gameplay mismatch

Examples:

- wrong mob AI,
- wrong inventory clicks,
- wrong lighting,
- wrong GUI layout,
- wrong projectile physics,
- wrong enchantment outcomes.

## P2 — noticeable fidelity issue

Examples:

- animation timing,
- particle count,
- sound pitch,
- text alignment,
- subtle camera mismatch.

## P3 — obscure edge mismatch

Still tracked; may be scheduled later.

Never close a P0/P1 issue as “close enough.”

---

# 94. DEVELOPMENT MILESTONES

## Milestone 0 — compatibility skeleton

Required:

- fixed tick loop,
- deterministic RNG abstraction,
- math compatibility helpers,
- NBT,
- registry framework,
- replay framework,
- golden test framework.

Exit gate: deterministic empty-world simulation reproduces itself bit-for-bit.

## Milestone 1 — world/block core

Required:

- chunks,
- sections,
- block registry,
- metadata,
- block placement/break,
- collision,
- basic lighting,
- save/load.

Exit gate: hand-built block test world round-trips with no semantic drift.

## Milestone 2 — player movement

Required:

- walk,
- sprint,
- sneak,
- jump,
- gravity,
- friction,
- water/lava,
- ladders,
- webs,
- fall damage.

Exit gate: motion traces match vanilla reference scenarios.

## Milestone 3 — inventory/items/crafting

Required:

- item stacks,
- player inventory,
- containers,
- clicks,
- recipes,
- furnace.

Exit gate: automated inventory transaction corpus matches vanilla.

## Milestone 4 — world generation

Required:

- biome pipeline,
- terrain,
- caves,
- decoration,
- structures.

Exit gate: golden seed region hashes match chosen vanilla corpus.

## Milestone 5 — entities and AI

Required:

- entity framework,
- mobs,
- projectiles,
- vehicles,
- AI,
- spawning.

Exit gate: deterministic entity scenarios stay synchronized for thousands of ticks.

## Milestone 6 — redstone/fluids/full block behavior

Exit gate: regression contraption suite matches vanilla timelines.

## Milestone 7 — combat

Required:

- old damage system,
- blocking,
- crits,
- knockback,
- bow,
- rod,
- armor,
- enchantments.

Exit gate: PvP motion/damage traces match vanilla under replay.

## Milestone 8 — protocol 47 multiplayer

Required:

- status,
- login,
- encryption,
- compression,
- all normal-play packets,
- chunk streaming,
- entity sync,
- inventories,
- scoreboards.

Exit gate: vanilla 1.8.9 client can play on replica server, and replica client can connect to controlled vanilla-compatible 1.8.x endpoint where legally/test-wise appropriate.

## Milestone 9 — client presentation

Required:

- rendering,
- UI,
- HUD,
- audio,
- particles,
- first person,
- menus,
- resource packs.

Exit gate: curated screenshot/audio-event suite meets project thresholds.

## Milestone 10 — certification

Required:

- long replay runs,
- multiplayer latency tests,
- world round-trip tests,
- seed corpus,
- redstone corpus,
- combat corpus,
- GUI matrix,
- performance verification.

Exit gate: no unresolved P0/P1 compatibility defects.

---

# 95. CERTIFICATION MATRIX

Every release candidate should publish a matrix like:

```text
Subsystem                  Status   Fidelity   Evidence
------------------------------------------------------
Protocol 47 handshake      PASS     exact      packet goldens
Login/encryption           PASS     exact      vanilla capture
Chunk serialization        PASS     exact      byte/semantic tests
Player movement            PASS     exact      120 replay cases
Combat                     WARN     98%        2 KB edge mismatches
World generation           PASS     exact      2000 chunks / 25 seeds
Redstone                    WARN     high       3 piston quirks open
Inventory                  PASS     exact      click corpus
Rendering                  WARN     high       translucent sort delta
GUI                        PASS     pixel-high screenshot suite
Audio                      PASS     event exact pitch tolerance
```

“1:1” claims should be backed by this matrix, not marketing language.

---

# 96. MUST-NOT-FORGET 1.8-ERA FEATURES

This checklist exists because agents often accidentally build a 1.7-like or modern hybrid.

Ensure the implementation accounts for 1.8-era additions/features such as:

- armor stands,
- banners,
- Guardians and Elder Guardians,
- Rabbits,
- Endermites,
- ocean monuments,
- slime blocks,
- spectator mode,
- world border,
- 1.8 model/blockstate resource system,
- skin customization layers/slim model support,
- lapis-based enchanting,
- additional stone variants,
- additional fences/gates/doors by wood type,
- wet sponge behavior,
- prismarine family,
- sea lanterns,
- iron trapdoor,
- inverted daylight sensor behavior where applicable,
- 1.8 command additions/changes,
- and other Bountiful Update-era content.

Maintain a version-content manifest rather than relying on this abbreviated reminder.

---

# 97. MUST-NOT-LEAK POST-1.8.9 FEATURES

Keep an automated forbidden-content list.

Examples:

```text
shield
offhand
attack cooldown
sweeping edge
elytra
end city
chorus fruit
shulker
lingering potion
tipped arrow
mending
frost walker
observer
totem of undying
concrete
glazed terracotta
recipe book
advancement
trident
swimming pose
waterlogging
phantom
crossbow
village workstations
pillager
bee
netherite
piglin
new Nether biomes
amethyst
copper
warden
sculk
camel
sniffer
trial chambers
modern world height
modern cave generation
```

The exact forbidden list should be generated from version history and expanded continuously.

---

# 98. REFERENCE EXPERIMENT LIBRARY

Create small vanilla test worlds/scenarios instead of repeatedly testing manually.

Recommended experiments:

## Movement

- walk 100 ticks on stone,
- sprint 100 ticks,
- sprint-jump sequence,
- sneak to edge,
- jump under 2-block ceiling,
- ice momentum,
- soul sand,
- cobweb,
- ladder ascent/descent,
- water current,
- lava movement,
- fall from every height 1–30.

## Combat

- sword hit standing,
- sword hit sprinting,
- sprint reset,
- crit from known fall distance,
- blocked sword hit,
- arrow at measured charge,
- rod hit,
- snowball/egg interaction,
- armor tiers,
- protection enchantments,
- Strength/Weakness,
- golden apple effects.

## Redstone

- one-tick pulse,
- torch clock,
- quasi-connected piston,
- repeater lock,
- comparator subtraction,
- hopper clock,
- slime piston chain.

## Worldgen

- 32×32 chunk seed regions,
- structure coordinates,
- biome maps,
- End entry,
- Nether entry.

## Inventory

- every click mode,
- shift-click every container,
- drag stacks,
- double click collect,
- number-key swap,
- invalid transaction correction.

## Rendering

- every graphics setting,
- day/night,
- rain/thunder,
- underwater/lava,
- fog distances,
- each GUI scale,
- first-person item-use states.

---

# 99. NUMERIC CONSTANT POLICY

Never add a magic number with a comment like “Minecraft value” unless provenance is known.

Use:

```cpp
// VERIFIED_VANILLA: scenario movement.jump.basic_001
constexpr float kPlayerJumpImpulse = ...;
```

or data equivalent.

For every compatibility constant, store:

- target version,
- subsystem,
- value,
- type (`float`, `double`, `int`),
- source/evidence,
- tests that depend on it.

The *type* matters because float rounding can be observable.

---

# 100. FLOATING-POINT POLICY

Do not “improve precision” automatically.

If vanilla used float at a step that the replica performs in double, trajectories may diverge over time.

For physics/worldgen/rendering compatibility:

- mirror precision where known,
- mirror cast points,
- mirror clamp order,
- mirror interpolation order,
- add ulp/tolerance tests only where exact bit equality is impossible across platforms.

Never use broad epsilon tolerances to hide systematic divergence.

---

# 101. JAVA SEMANTICS COMPATIBILITY

If implemented outside Java, explicitly emulate Java behavior where algorithms depend on it:

- signed integer overflow,
- signed long overflow,
- arithmetic right shift,
- unsigned right shift where used,
- float-to-int casts,
- double-to-int casts,
- negative modulo differences,
- `Math.floor` versus truncation,
- `java.util.Random`,
- collection ordering where relied upon,
- hash behavior only where demonstrably gameplay-relevant.

Write helper functions and unit-test them against Java-generated vectors.

---

# 102. SAVE COMPATIBILITY MODES

Define project modes explicitly:

## Exact-interchange mode

Replica can load and save 1.8.9 worlds intended to reopen in vanilla.

Requires strict preservation.

## Import-only mode

Replica can load 1.8.9 worlds but writes its own format.

Must be clearly labeled; do not claim full save compatibility.

## Behavioral-only mode

No direct save interchange; simulation targets 1.8.9 behavior.

The project must state which mode it supports.

---

# 103. CLIENT COMPATIBILITY MODES

Similarly distinguish:

## Vanilla-client compatible server

Real 1.8.9 client can connect via protocol 47.

## Replica-client compatible vanilla/proxy server

Replica can connect to 1.8-era servers.

## Standalone behavior clone

No promise of network interoperability.

Do not conflate these claims.

---

# 104. RENDERING COMPATIBILITY LEVELS

Define:

- `R0`: functionally readable, not visually faithful.
- `R1`: correct assets/models/layout family.
- `R2`: high visual similarity.
- `R3`: screenshot-diff target for deterministic scenes.

If marketing says “1:1,” target R3 for core scenes and menus.

---

# 105. AUDIO COMPATIBILITY LEVELS

Define:

- event correctness,
- timing correctness,
- parameter correctness,
- waveform/resource correctness if legal assets are supplied externally.

A project can be behaviorally exact while shipping replacement audio; document this distinction.

---

# 106. CONTENT MANIFEST GENERATION

Agents should generate a machine-readable target manifest from version-specific references.

Manifest categories:

```text
blocks
block metadata variants
items
entities
biomes
enchantments
potions
achievements
statistics
commands
gamerules
particles
sounds
GUI screens
container types
recipes
smelting recipes
structures
world types
packet IDs
```

CI should fail if an implementation registry is missing a manifest entry without an explicit exclusion.

---

# 107. TEST-NAME CONVENTION

Use names that encode version and behavior.

Examples:

```text
mc189.player.jump.basic
mc189.player.sprint.first_hit_knockback
mc189.combat.sword_block.melee
mc189.redstone.piston.quasi_connectivity.above_block
mc189.worldgen.seed_42.chunk_0_0
mc189.protocol47.chunk.full_overworld
mc189.inventory.chest.shift_click.hotbar_full
mc189.render.gui.inventory.scale_2_1280x720
```

This prevents generic tests from silently drifting to newer behavior.

---

# 108. COMMIT / PR COMPATIBILITY CHECKLIST

Any change to gameplay should answer:

```text
[ ] Is this explicitly 1.8.9 behavior?
[ ] Is the evidence linked?
[ ] Is there a differential test?
[ ] Could iteration/order have changed?
[ ] Could RNG consumption have changed?
[ ] Could floating-point order have changed?
[ ] Could network packet timing/order have changed?
[ ] Could save output have changed?
[ ] Could redstone update order have changed?
[ ] Did a modern Minecraft assumption leak in?
```

---

# 109. CODE REVIEW RED FLAGS

Reviewers should stop a change when they see phrases like:

- “Minecraft probably…”
- “Current Minecraft does…”
- “This is cleaner…”
- “Players won’t notice…”
- “Close enough…”
- “Same mathematically…”
- “I used the newest Wiki formula…”
- “We can fix this old bug…”
- “I replaced it with built-in physics…”
- “This library already has Minecraft movement…”

Each is a signal to demand a version-specific test.

---

# 110. RECOMMENDED REPOSITORY LAYOUT

```text
/docs
  /compat
    MINECRAFT_1_8_9_REPLICATION_MASTER_CONTEXT.md
    verified_facts.md
    unknowns.yml
    bugs_1_8_9.yml
    deviations.yml
    source_policy.md
  /protocol47
  /world_format
  /render_reference

/data/mc189
  blocks.json
  items.json
  biomes.json
  entities.json
  recipes.json
  packets.json
  sounds.json
  particles.json
  gamerules.json

/src
  /core
  /world
  /entity
  /player
  /redstone
  /network/protocol47
  /persistence
  /client/render
  /client/gui
  /client/audio

/tests
  /unit
  /golden
  /replay
  /worldgen
  /protocol47
  /redstone
  /combat
  /render
  /savecompat

/reference
  /captures        # do not commit proprietary assets
  /screenshots     # only if project rights/policy permit
  /packet_traces
  /state_traces
  /nbt_samples
```

---

# 111. DEVIATION POLICY

If the project intentionally differs from 1.8.9, the deviation must be explicit.

Create `deviations.yml`:

```yaml
- id: DEV-001
  subsystem: authentication
  vanilla_behavior: legacy account/session flow tied to historical infrastructure
  replica_behavior: pluggable modern account provider
  reason: external service evolution
  compatibility_effect: gameplay protocol preserved after login
  user_visible: true
```

No hidden deviations.

---

# 112. AUTHENTICATION VS GAME PROTOCOL

Historical account infrastructure may no longer be practical to reproduce exactly because external services have changed.

Separate:

- account authentication,
- profile/skin service,
- protocol login encryption,
- server authorization,
- gameplay protocol.

It is acceptable for the launcher/account layer to be modern or project-specific while the in-game protocol target remains 47, provided the deviation is explicit.

---

# 113. PLATFORM DIFFERENCES

Vanilla 1.8.9 ran on multiple desktop platforms and GPU/driver combinations.

Decide whether the target is:

- platform-independent intended behavior,
- Windows vanilla behavior,
- a specific Java/LWJGL setup,
- or visual output from a reference GPU profile.

Rendering exactness may require a defined reference configuration.

Gameplay state should not depend on GPU.

---

# 114. LWJGL / WINDOWING NOTE

The original client belongs to the LWJGL 2 era. A modern implementation does **not** need to use LWJGL 2.

What matters is reproducing observable behavior:

- input sampling,
- mouse capture,
- fullscreen/window switching,
- display sizing,
- vsync option,
- framerate limit behavior,
- OpenGL-era rendering result.

Backend substitution is allowed; behavior substitution is not.

---

# 115. FRAME RATE AND SIMULATION

Test gameplay at:

- 20 FPS,
- 30 FPS,
- 60 FPS,
- 120 FPS,
- high/unlimited FPS,
- intentional frame hitching.

Authoritative simulation should remain stable.

Client-only effects may depend on frame interpolation and should be compared separately.

---

# 116. NETWORK CONDITIONS TEST MATRIX

For multiplayer, test at minimum:

```text
0 ms / 0 jitter / 0 loss
25 ms / 5 jitter / 0 loss
50 ms / 10 jitter / 0 loss
100 ms / 20 jitter / 0 loss
200 ms / 50 jitter / 0 loss
100 ms / 20 jitter / 1% loss
```

Observe:

- movement correction,
- combat,
- inventory,
- block placement,
- entity interpolation,
- chunk loading,
- keepalive,
- disconnect behavior.

---

# 117. PvP CERTIFICATION SUITE

Because 1.8.9 is still known for its PvP feel, certify it separately.

Required scenarios:

- standing hit,
- sprint hit,
- W-tap sequence,
- S-tap sequence,
- jump crit,
- non-crit jump hit,
- block-hit sequence,
- rod → sword sequence,
- snowball → sword sequence,
- bow knockback,
- knockback enchantment,
- varying latency,
- edge-of-reach hits,
- target airborne,
- attacker airborne,
- hit during hurt-resistance window.

Compare per-tick position, velocity, health, sprint state, and packets.

“Feels good” is not a valid PvP acceptance criterion.

---

# 118. PARKOUR / MOVEMENT CERTIFICATION SUITE

Create benchmark courses for:

- 1-block jumps,
- 2/3/4-block horizontal jumps,
- head-hitters,
- ladder jumps,
- fence jumps,
- ice jumps,
- slime block launches,
- sprint cornering,
- sneak-edge behavior,
- water movement,
- cobweb exit,
- soul sand.

Input replays must succeed/fail on the same ticks as vanilla.

---

# 119. REDSTONE CERTIFICATION SUITE

A release claiming redstone fidelity should run hundreds of machine fixtures.

Each fixture records:

```text
input event ticks
block states per tick
power levels per tick
piston positions
container counts
entity positions if relevant
```

Hash the timeline.

---

# 120. WORLDGEN CERTIFICATION SUITE

A strong worldgen gate should compare thousands of chunks across multiple seeds.

Use layers:

1. biome-only,
2. terrain before population,
3. populated terrain,
4. structures,
5. spawn.

When a mismatch occurs, report first differing coordinate/state, not only a global hash.

---

# 121. UI CERTIFICATION MATRIX

Capture each major screen at:

```text
854x480
1280x720
1920x1080
```

and every available GUI scale relevant to the resolution.

Test:

- title,
- options,
- controls,
- resource packs,
- world select,
- world create,
- multiplayer,
- pause,
- inventory,
- chest,
- crafting,
- furnace,
- enchanting,
- anvil,
- brewing,
- beacon,
- villager trade,
- death,
- chat,
- F3.

---

# 122. RESOURCE-PACK CERTIFICATION

Create synthetic legal test packs that exercise:

- texture override,
- animated texture,
- block model inheritance,
- blockstate variants,
- weighted model variants,
- item models,
- custom sounds,
- language override,
- pack stacking,
- malformed metadata,
- missing texture/model.

Do not require redistribution of vanilla assets to test loader semantics.

---

# 123. ERROR BEHAVIOR

Compatibility also includes failures.

Test:

- malformed server packet,
- wrong protocol version,
- bad compression frame,
- invalid resource pack,
- corrupt NBT,
- missing chunk data,
- invalid inventory transaction,
- connection timeout,
- kicked player,
- full server,
- banned player,
- whitelist rejection,
- duplicate login.

Where exact messages are not critical, preserve semantics even if wording differs for legal/localization reasons.

---

# 124. LOCALIZATION

If supporting vanilla language packs externally, version-lock translation keys and formatting behavior.

Do not assume modern translation keys.

If shipping original replacement localization, keep compatibility aliases separate from original project text.

---

# 125. SECURITY WITHOUT GAMEPLAY DRIFT

Security fixes are allowed where necessary, especially for parsing untrusted network/file data.

However:

- validate lengths before allocation,
- bound decompression,
- avoid historical crashes/exploits,
- preserve normal valid-input behavior,
- document cases where malicious-input behavior intentionally differs from vanilla.

Security bugs do not need to be replicated merely for “1:1” fidelity.

---

# 126. WHAT “EXACT” DOES NOT REQUIRE

Unless the project explicitly targets binary preservation, 1:1 behavioral fidelity does **not** require:

- same class names,
- same source structure,
- same obfuscation,
- same Java version,
- same LWJGL version,
- same renderer API,
- same memory layout,
- same thread count,
- same launcher,
- same proprietary assets bundled in the project.

These may differ as long as the observable compatibility contract is preserved.

---

# 127. WHAT “EXACT” DOES REQUIRE

Where applicable, it **does** require:

- same gameplay outcome,
- same timing,
- same deterministic generation,
- same packet semantics,
- same save semantics,
- same collision result,
- same inventory result,
- same damage/knockback result,
- same redstone result,
- same UI interaction result,
- same visible result within the project’s rendering tolerance,
- same audio event selection/timing within the project’s audio tolerance.

---

# 128. AGENT STARTUP PROMPT

Copy this section into coding-agent system context when needed:

```text
You are working on a clean-room behavioral reimplementation of Minecraft: Java Edition 1.8.9.

The project target is exactly 1.8.9 behavior. Never substitute behavior from newer Minecraft versions. Never assume modern APIs, combat, world generation, redstone, networking, block states, entity sizes, commands, UI, or file formats are compatible.

For every behavior you implement:
1. identify the exact observable 1.8.9 rule,
2. identify evidence,
3. implement it without copying proprietary Mojang source/assets,
4. add deterministic tests,
5. compare against vanilla 1.8.9 where practical,
6. record unknowns instead of guessing,
7. preserve old quirks when gameplay-relevant,
8. do not claim completion if known mismatches remain.

Protocol target: 47.
Resource-pack target: format 1.
World model: pre-flattening numeric IDs + metadata, old 256-block height.
Combat target: pre-1.9; no attack cooldown, no shields/offhand, sword blocking present.

The master compatibility document overrides generic Minecraft knowledge.
```

---

# 129. AGENT HANDOFF FORMAT

When one agent hands work to another, use:

```markdown
## Subsystem

## What is verified

## What is implemented

## What still differs from vanilla 1.8.9

## Evidence / vanilla scenarios

## Tests

## Unknowns

## Dangerous modern-version traps

## Files changed

## Next recommended task
```

This prevents context loss.

---

# 130. RESEARCH BACKLOG — HIGH PRIORITY

Before claiming full 1:1 fidelity, explicitly lock down:

- exact player motion constants and update order,
- exact collision resolution order/epsilons,
- exact reach/raycast rules,
- exact hurt-resistance behavior,
- exact sprint knockback state transition,
- exact sword-blocking damage applicability,
- exact critical-hit predicate,
- exact armor/enchantment damage pipeline,
- exact hunger/exhaustion rates,
- exact random tick selection,
- exact scheduled tick ordering,
- exact redstone neighbor-notification ordering,
- exact piston behavior,
- exact fluid spread order,
- exact lighting propagation,
- exact mob cap/spawn algorithm,
- exact entity despawn rules,
- exact worldgen RNG ordering,
- exact structure placement,
- exact inventory transaction behavior,
- exact protocol-47 packet IDs and codecs,
- exact old chunk packet layout,
- exact resource model transformations,
- exact GUI pixel layout,
- exact font widths,
- exact first-person transforms,
- exact audio pitch randomization.

These should become separate evidence-backed documents or generated data tables over time.

---

# 131. RESEARCH BACKLOG — MEDIUM PRIORITY

- all achievement triggers,
- all statistics increments,
- obscure command edge cases,
- map rendering details,
- banner rendering edge cases,
- firework NBT rendering,
- written book formatting,
- jukebox behavior,
- note-block instruments/pitches,
- beacon beam/render quirks,
- Ender Dragon obscure edge cases,
- village siege details,
- fishing loot details,
- rare mob equipment probabilities,
- entity leash edge cases,
- painting placement ordering,
- item-frame comparator behavior,
- minecart collision edge cases,
- boat break/drop edge cases,
- portal search tie-breaking,
- weather visual edge cases.

---

# 132. VERSION LOCK RULE

Every data file, test, documentation page, packet table, and compatibility helper should include the target identifier:

```text
minecraft-java-1.8.9
protocol-47
```

Do not create unversioned generic “Minecraft” constants when they can differ by version.

---

# 133. CI RULES

Recommended continuous-integration gates:

1. unit tests,
2. deterministic replay tests,
3. protocol codec round-trip tests,
4. save/NBT tests,
5. registry completeness,
6. forbidden post-1.8 content scan,
7. worldgen goldens,
8. redstone goldens,
9. movement/combat goldens,
10. screenshot tests for selected reference scenes.

Long vanilla differential tests may run nightly if they cannot run on every commit.

---

# 134. COMPATIBILITY DASHBOARD METRICS

Track measurable progress:

```text
% packet codecs verified
% blocks implemented
% metadata states verified
% items implemented
% recipes verified
% entities implemented
% AI scenarios passing
% seed/chunk hashes matching
% redstone fixtures passing
% movement replays passing
% combat replays passing
% inventory tests passing
% UI screenshots within threshold
% save fixtures round-tripping
open P0/P1 mismatches
open UNKNOWN items
```

This is better than a subjective “90% done.”

---

# 135. FINAL NON-NEGOTIABLES

An agent must never do any of the following without an explicit project deviation:

1. Replace pre-1.9 combat with current combat.
2. Replace old world generation with a visually similar modern generator.
3. Replace legacy IDs/metadata with lossy modern states.
4. Replace protocol 47 with a translated newer protocol and call it exact.
5. Replace redstone with idealized circuit logic.
6. Replace old movement with a generic character controller.
7. Replace Minecraft collision with capsule physics.
8. Replace inventory transaction semantics with a generic UI inventory.
9. Replace scheduled block ticks with frame updates.
10. Change RNG ordering for performance.
11. Change float/double behavior casually.
12. Ignore negative-coordinate edge cases.
13. Add later-version content to the vanilla ruleset.
14. Remove gameplay-relevant bugs because they look wrong.
15. Declare a subsystem complete without evidence and tests.

---

# 136. COMPACT “IF YOU ONLY READ ONE PAGE” SUMMARY

The target is **Minecraft: Java Edition 1.8.9**.

Treat vanilla 1.8.9 as an oracle.

Protocol is **47**.

Resource packs use **format 1**.

The world uses the old pre-flattening ID/metadata model and old 256-block vertical range.

The game uses the classic pre-1.9 combat system: no attack cooldown, no shields, no offhand, sword blocking exists.

Do not import current Minecraft behavior.

Do not guess constants.

Do not “fix” old quirks without documenting a deviation.

Build deterministic replays, packet goldens, world/NBT goldens, screenshot goldens, redstone fixtures, worldgen seed corpora, movement traces, and PvP traces.

When vanilla and the replica disagree, find the **first divergent tick/state transition** and fix that.

A clean architecture is useful, but fidelity outranks elegance.

The final product should be able to answer this question for every major subsystem:

> “What evidence proves this behaves like Minecraft Java 1.8.9 rather than like our memory of Minecraft?”

If the answer is “none yet,” the subsystem is not finished.

---

# 137. REFERENCE NOTES FOR AGENTS

Useful external references to consult while validating the target include:

- Minecraft Wiki / historical page for **Java Edition 1.8.9**.
- Historical **Java Edition 1.8 / Bountiful Update** feature history.
- Version-specific **protocol 47** documentation / archived wiki.vg material.
- A protocol-47 packet reference generated for Minecraft 1.8.9.
- Minecraft Wiki history for **resource pack format 1**.
- Historical **Anvil file format** documentation.
- Mojang historical bug reports for 1.8.x behavior.
- Vanilla 1.8.9 itself, used as the primary behavioral oracle.

Do not use a current-version Wiki formula without checking its history.

---

# 138. DOCUMENT MAINTENANCE

This document is the root context, not the final encyclopaedia.

As exact values become verified, move them into focused versioned documents such as:

```text
mc189_player_physics.md
mc189_combat.md
mc189_redstone.md
mc189_worldgen.md
mc189_protocol47.md
mc189_chunk_format.md
mc189_entity_registry.md
mc189_block_registry.md
mc189_inventory.md
mc189_rendering.md
mc189_gui.md
```

Each focused document should link back to tests and evidence.

If a focused document conflicts with this one, the more specific **verified** document wins, and this master file should be updated.

---

# END OF MASTER CONTEXT

**Core rule:** Reproduce Minecraft Java Edition 1.8.9 as it was, not as later Minecraft became and not as an agent remembers it.
