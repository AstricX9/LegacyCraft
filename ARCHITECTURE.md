# LegacyCraft parity architecture

LegacyCraft is a clean-room implementation targeting the observable behavior of
Minecraft Java Edition 1.8.9. The supplied 1.8.9 resource archive is the asset
source of truth. The older `Minecraft_OLd` project is used only as an inventory
of previously attempted subsystems; its implementation and mixed-version assets
are not copied wholesale.

## Package ownership

| Package | Responsibility | Current milestone |
|---|---|---|
| `net.minecraft.client` | main loop, timing, input orchestration, integrated-server session | playable, menus + saves |
| `net.minecraft.client.additions` | optional, per-instance Additions (off = plain 1.8.9) | catalog + additions.txt + first examples per category (DEV-041) |
| `net.minecraft.client.gui` | scaled GUI, bitmap text, screens, HUD | full 1.8.9 menu graph + F3 (layout HYPOTHESIS) |
| `net.minecraft.client.renderer` | camera, world, entities, particles, weather | 16x16x16 section VBOs built on worker threads, frustum culling, opaque then sorted translucent pass, time-of-day lightmap, sky/fog/clouds (SkyRenderer) + typed entity dispatch |
| `net.minecraft.client.renderer.block.model` | blockstate/model inheritance and baked quads | state-mapped + weighted variants, element/variant rotation, uvlock |
| `net.minecraft.client.renderer.texture` | atlases, animation, resource textures | discovered block/GUI atlases |
| `net.minecraft.client.audio` | sound events, music, positioned playback | sounds.json event/variant registry |
| `net.minecraft.block` | blocks, materials, properties, block states | 1.8.9 ID table + metadata codec; one class per kind of block (actual state, shapes, placement, support) |
| `net.minecraft.item` | items, stacks, tools, food, armor | indexed definitions + stacks/durability + item tags (enchantments, names, prior work) |
| `net.minecraft.enchantment` | enchantments, table rolls, enchantment effects | 1.8.9 enchantment table + EnchantmentHelper rolls, protection, Unbreaking (DEV-029) |
| `net.minecraft.entity` | entities, AI, attributes, combat | lifecycle + runtime ID manager + damage sources |
| `net.minecraft.inventory` | slots, containers, crafting, furnaces | stack inventory, crafting grids, furnaces (smelting.json), enchanting table, anvil |
| `net.minecraft.world` | simulation and block access | biome-aware unbounded block access |
| `net.minecraft.world.chunk` | chunk lifecycle, sections and streaming | lazy indexed chunks + 16x16x16 sections |
| `net.minecraft.world.biome` | climate, terrain and decoration | 1.8.9 biome table + colours; vanilla GenLayer placement |
| `net.minecraft.world.gen` | terrain shape, surface, carvers, population | 1.8.9 overworld pipeline: density terrain, biome surfaces, caves/ravines, ores/trees/plants/lakes (DEV-001, DEV-016) |
| `net.minecraft.world.storage` | NBT, region files, saves | atomic metadata + compressed chunk saves |
| `net.minecraft.network` | 1.8 protocol and packet handlers | protocol states + bounds-checked packet codec |
| `net.minecraft.server` | integrated/dedicated authoritative simulation | 20 TPS integrated server + entity ticking |
| `net.minecraft.util` | identifiers, math, positions, ray tracing | identifiers started |

## Fidelity rules

1. Match 1.8.9 behavior, timing, coordinate conventions and GUI scale before adding improvements.
2. Read assets through namespaced resource locations; never hard-code development filesystem paths.
3. Keep simulation on a 20 TPS contract even when rendering runs faster.
4. Client presentation and authoritative world state remain separate so singleplayer can use an integrated server.
5. A subsystem is considered implemented only when it has observable behavior and focused verification; empty facade classes are avoided.
6. Later-version blocks, assets and mechanics are excluded from the parity target.

## Near-term vertical slices

1. ~~Represent full block-state properties and select matching model variants~~ (done; 1.8.9 has no multipart). ~~Per-state collision/selection boxes and support-based block updates~~ (done; see DEV-005, DEV-007 for what remains).
2. Add asynchronous chunk scheduling around persisted, server-owned generation.
3. ~~Main menu, pause menu, options, GUI scaling and language lookup~~ (done; screenshot comparison pending, see unknowns.yml).
4. Containers, survival mining/drop rules and recipe-data loading around the inventory core.
5. Skylight/block light with cross-chunk propagation and emissive blocks.
6. NBT/Anvil compatibility, combat/AI, sound playback, particles, weather, redstone and packet handlers.
