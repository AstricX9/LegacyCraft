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
| `net.minecraft.client.renderer` | camera, world, entities, particles, weather | chunk world |
| `net.minecraft.client.renderer.texture` | atlases, animation, resource textures | block/GUI atlases |
| `net.minecraft.client.audio` | sound events, music, positioned playback | planned |
| `net.minecraft.block` | blocks, materials, properties, block states | initial registry set |
| `net.minecraft.item` | items, stacks, tools, food, armor | planned |
| `net.minecraft.entity` | entities, AI, attributes, combat | planned |
| `net.minecraft.inventory` | slots, containers, crafting, furnaces | planned |
| `net.minecraft.world` | simulation and block access | finite milestone world |
| `net.minecraft.world.chunk` | chunk lifecycle, sections and streaming | renderer chunks only |
| `net.minecraft.world.biome` | climate, terrain and decoration | initial terrain noise |
| `net.minecraft.world.storage` | NBT, region files, saves | planned |
| `net.minecraft.network` | 1.8 protocol and packet handlers | planned |
| `net.minecraft.server` | integrated/dedicated authoritative simulation | planned |
| `net.minecraft.util` | identifiers, math, positions, ray tracing | identifiers started |

## Fidelity rules

1. Match 1.8.9 behavior, timing, coordinate conventions and GUI scale before adding improvements.
2. Read assets through namespaced resource locations; never hard-code development filesystem paths.
3. Keep simulation on a 20 TPS contract even when rendering runs faster.
4. Client presentation and authoritative world state remain separate so singleplayer can use an integrated server.
5. A subsystem is considered implemented only when it has observable behavior and focused verification; empty facade classes are avoided.
6. Later-version blocks, assets and mechanics are excluded from the parity target.

## Near-term vertical slices

1. Chunk sections and infinite chunk streaming, migrated conceptually from the old project.
2. Data-driven block states/models using the supplied 1.8.9 JSON files.
3. Main menu, pause menu, options, GUI scaling and language lookup.
4. Inventory, item stacks, survival mining, drops and crafting.
5. Skylight/block light with cross-chunk propagation and emissive blocks.
6. Save format, entities, combat, AI, sound, particles, weather, redstone and networking.
