# LegacyCraft parity architecture

LegacyCraft is a clean-room implementation targeting the observable behavior of
Minecraft Java Edition 1.8.9. The supplied 1.8.9 resource archive is the asset
source of truth. The older `Minecraft_OLd` project is used only as an inventory
of previously attempted subsystems; its implementation and mixed-version assets
are not copied wholesale.

## Package ownership

| Package | Responsibility | Current milestone |
|---|---|---|
| `net.minecraft.client` | main loop, timing, input orchestration | playable |
| `net.minecraft.client.gui` | scaled GUI, bitmap text, screens, HUD | font + HUD |
| `net.minecraft.client.renderer` | camera, world, entities, particles, weather | streamed chunk meshes + typed entity dispatch |
| `net.minecraft.client.renderer.block.model` | blockstate/model inheritance and baked quads | default variants + parent models |
| `net.minecraft.client.renderer.texture` | atlases, animation, resource textures | discovered block/GUI atlases |
| `net.minecraft.client.audio` | sound events, music, positioned playback | sounds.json event/variant registry |
| `net.minecraft.block` | blocks, materials, properties, block states | indexed per-definition registry |
| `net.minecraft.item` | items, stacks, tools, food, armor | indexed definitions + stacks/durability |
| `net.minecraft.entity` | entities, AI, attributes, combat | lifecycle + runtime ID manager |
| `net.minecraft.inventory` | slots, containers, crafting, furnaces | stack inventory + shaped recipes |
| `net.minecraft.world` | simulation and block access | biome-aware unbounded block access |
| `net.minecraft.world.chunk` | chunk lifecycle, sections and streaming | lazy indexed chunks + 16x16x16 sections |
| `net.minecraft.world.biome` | climate, terrain and decoration | deterministic climate/terrain selection |
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

1. Represent full block-state properties and select matching/multipart model variants.
2. Add asynchronous chunk scheduling around persisted, server-owned generation.
3. Main menu, pause menu, options, GUI scaling and language lookup.
4. Containers, survival mining/drop rules and recipe-data loading around the inventory core.
5. Skylight/block light with cross-chunk propagation and emissive blocks.
6. NBT/Anvil compatibility, combat/AI, sound playback, particles, weather, redstone and packet handlers.
