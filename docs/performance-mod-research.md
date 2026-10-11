# Performance mod research

What the well-known Minecraft performance mods actually do, which of their
techniques fit LegacyCraft (a Java 17 / LWJGL 3 / fixed-function OpenGL
recreation of 1.8.9), and in what order to adopt them. Measurements live in
[performance-benchmarks.md](performance-benchmarks.md).

Researched 2026-10-11.

## How the evidence was gathered

Every repository below was shallow-cloned at its default branch on
2026-10-11 and read directly; the branch and last commit date are given
for each, because modern code differs a lot from the versions that once
targeted older Minecraft. Each finding carries one of these tags:

| Tag | Meaning |
|---|---|
| **SOURCE** | Read in the mod's code on the branch named. |
| **DOCS** | Stated in the mod's README, wiki or Modrinth page, not checked in code. |
| **UNVERIFIED** | Recalled or inferred; treat as a hypothesis until checked. |

Modrinth's API was not reachable from the research machine, so licences
come from each repository's LICENSE file (and Modrinth's web page where a
repository could not be cloned).

**Licence policy for LegacyCraft.** No code is copied from any of these
projects. Where an idea is used, it is a general algorithm (open-addressing
hashing, flood-fill visibility, primitive work queues, ray marching) written
independently. This matters most for Sodium (PolyForm Shield, which forbids
competing use of its code), Entity Culling (tr7zw Protective License),
MoreCulling (GPL-3.0) and Patcher (CC BY-NC-SA 4.0).

## LegacyCraft today (inspected before proposing anything)

| Subsystem | Where | What it does now |
|---|---|---|
| Terrain rendering | `client/renderer/WorldRenderer.java` | 16x16x16 sections, one VBO per section per layer (opaque, cutout, translucent), 32-byte vertices, `glDrawArrays(GL_QUADS)` with a `glPushMatrix/glTranslatef` per section, frustum culling per section, translucent layers re-sorted near the camera. No occlusion culling. |
| Meshing | `SectionMesher.java`, `SectionSnapshot.java`, `FluidMesher.java`, `BlockLighting.java` | Snapshot of a section plus a 2-block border copied on the main thread, meshed on up to 3 worker threads, uploaded under a 3 ms/frame budget. Model lookup is `synchronized` on the model manager. Render layer chosen by looking the block name up in a `Set<String>` per block. |
| Chunk storage | `world/World.java`, `world/chunk/Chunk.java` | Every chunk in one `HashMap<Long, Chunk>`; every `getState`/`get`/light read goes through it. Chunks are never unloaded. |
| World generation | `world/gen/*` | Vanilla 1.8.9 pipeline, run on the main thread by `WorldRenderer.generateAround` under a 6 ms/frame budget (and on the server tick thread for the dedicated server). |
| Lighting | `world/LightEngine.java` | Breadth-first flood fill over `ArrayDeque<Long>` queues; a new chunk is lit from scratch and then exchanges light with its neighbours. |
| Block states | `block/BlockState.java` | Immutable (block, metadata) pair; `BlockState.of` allocates a new object on every call, so every `World.getState` allocates. |
| Entities, particles, GUI, text | `client/renderer/entity/*`, `client/particle/*`, `client/gui/*` | Immediate mode (`glBegin`/`glEnd`, 64 call sites); fonts drawn quad by quad. |
| Server | `server/IntegratedServer.java`, `server/dedicated/*` | 20 TPS; the earlier "performance drop" thread already stopped mob spawning from generating chunks. |

### What the profiler says (cloud machine, headless)

A JFR recording of `gradlew benchmark -Pbench=worldgen` (289 chunks
generated, populated and lit) on `main` showed:

- **4.1 GB allocated per 289 chunks (about 14 MB per chunk).** 38% of it
  is `java.lang.Long` (boxed chunk keys and light-queue entries), 37% is
  `java.lang.reflect.Type[]` and 16% is `Direction[]`.
- The `Type[]` garbage comes from `HashMap.TreeNode.find` →
  `HashMap.comparableClassFor` → `Class.getGenericInterfaces()`. A packed
  chunk key `x << 32 ^ z` has `Long.hashCode` equal to `x ^ z`, so a square
  of N x N chunks produces only about N distinct hashes. Buckets overflow,
  Java turns them into red-black trees, and every lookup in a tree bin
  reflects on `Long`'s interfaces to compare keys.
- CPU: 50% in `LightEngine.lightChunk` (43% self time in
  `ArrayDeque.addLast`, which is where boxed positions are allocated), 28%
  in `HashMap.get` for chunk lookups, 13% in terrain generation proper.
- `Direction.values()` clones its array on every call; `LightEngine.spread`
  calls it once per dequeued position.

These two systems (chunk lookup and light propagation) are exactly the
targets of Lithium's `world.chunk_access` / FoamFix-style allocation work
and of Phosphor / Starlight respectively, which is why they come first in
the priority list below.

## Mod-by-mod findings

Columns: **Bottleneck** is the resource the mod saves; **Fits 1.8.9 Java/GL**
is whether the technique (not the mod) can be applied to LegacyCraft;
**Vanilla** is whether the mod claims, or the code shows, that results are
unchanged.

### Rendering and chunk optimisation

#### Sodium
- **Repo / licence:** github.com/CaffeineMC/sodium, branch `dev`, 2026-09-30. **PolyForm Shield 1.0.0** (source-available, no competing use). Supports 1.16.3 to current; requires OpenGL 4.5 drivers (DOCS).
- **Problem:** vanilla's terrain renderer spends CPU per section per frame and moves too many bytes per vertex.
- **What it changes (SOURCE):**
  - *Compact vertex format.* `CompactChunkVertex.STRIDE = 20` bytes: positions quantised to 20 bits per axis inside a section-relative range, texture coordinates to 15 bits, colour and light packed. LegacyCraft's vertex is 32 bytes.
  - *Region batching.* Sections are grouped into `RenderRegion`s of 8 x 4 x 8 sections that share buffers, and `DefaultChunkRenderer.fillCommandBuffer` issues one batch per region and pass instead of one draw (plus state changes) per section.
  - *Block-face culling.* Geometry is bucketed by facing (`ModelQuadFacing`: six axis directions plus "unassigned"); `DefaultChunkRenderer.getVisibleFaces` draws only the buckets whose faces can point at the camera given the camera's position relative to the section. Up to half of a section's axis-aligned faces are skipped without per-quad work.
  - *Occlusion culling.* `OcclusionCuller` walks the section graph breadth-first from the camera section, entering a neighbour only if the current section's precomputed `VisibilityEncoding` says light could pass between the two faces (the same idea as vanilla 1.8.9's `VisGraph`), with frustum checks along the way.
  - *Translucency sorting.* `translucent_sorting/` builds BSP trees or uses "GFNI" trigger planes so translucent geometry is re-sorted only when the camera crosses a plane that changes the order.
  - *Mesh building (SOURCE):* `AbstractBlockRenderContext.shouldDrawSide` caches neighbour-face culling decisions.
- **Bottleneck:** CPU (draw submission, culling), GPU vertex bandwidth, memory.
- **Fits 1.8.9 Java/GL:** face bucketing, compact vertices, occlusion graph and region batching all work with OpenGL 1.5 VBOs or OpenGL 2/3 attribute pointers; multi-draw needs GL 1.4 `glMultiDrawArrays`. No Vulkan needed.
- **Vanilla:** visually equivalent by design (DOCS: "fixing graphical issues" aside).
- **Trade-offs:** compact vertices need a shader or `GL_SHORT` positions with a scale; region buffers need sub-allocation (an arena) and careful freeing on the GL thread.

#### Oldium / 1.8.9-era Sodium ports
- Searches found forks of the 1.16 `sodium-fabric` code (e.g. github.com/mrmangohands/sodium-fabric) but **no maintained Sodium for 1.8.9** under that name (UNVERIFIED that "Oldium" exists as a public project). The 1.16 Sodium design (VBO per region, `glMultiDrawArrays`, compact vertices, chunk graph culling) is the relevant historical reference; its licence was LGPL-3.0 at the time (UNVERIFIED for every historical commit).

#### ImmediatelyFast
- **Repo / licence:** github.com/RaphiMC/ImmediatelyFast, branch `26.3`, 2026-10-08. **LGPL-3.0.**
- **Problem:** "immediate mode" rendering of entities, block entities, particles, text and HUD issues many small draws.
- **What it changes (DOCS + SOURCE file names):** a custom buffer implementation batches immediate-mode draws by render type (`enhanced_batching` mixins), caches per-character text data (`fast_text_lookup`), packs map textures into one atlas, avoids redundant framebuffer switches, optionally buffers sign text.
- **Bottleneck:** CPU / driver overhead (draw calls, state changes).
- **Fits LegacyCraft:** yes, strongly: LegacyCraft has 64 `glBegin` call sites, fonts drawn per quad, mobs drawn part by part in immediate mode. A small client-side vertex batcher (collect quads with the same texture/state, one `glDrawArrays` per batch) is the equivalent.
- **Vanilla:** visual output unchanged if draw order within a batch is preserved for blended geometry.

#### Entity Culling (and the OcclusionCulling library)
- **Repo / licence:** github.com/tr7zw/EntityCulling, branch `main`, 2026-10-08, **tr7zw Protective License**; it uses github.com/LogisticsCraft/OcclusionCulling (`master`, 2024-03-03), **MIT**.
- **Problem:** entities and block entities behind terrain are still drawn.
- **What it changes (SOURCE):** a dedicated `CullThread` (`EntityCullingModBase`) periodically ray-marches from the camera to points of each entity's bounding box through a grid of opaque blocks (`OcclusionCullingInstance.isAABBVisible`, Amanatides-Woo style grid walk, with a per-cell cache of known-visible / known-hidden cells near the camera). Hidden entities skip rendering; "tick culling" skips some client-only ticking.
- **Bottleneck:** CPU and GPU in entity-heavy scenes.
- **Fits LegacyCraft:** yes; LegacyCraft already has a voxel ray walker (`WorldRenderer.raycast`). 1.8.9 version is listed as outdated/unsupported upstream (DOCS).
- **Vanilla:** rendering-only; a one-frame lag of the async result can pop an entity in late. Beacon beams and similar oversized renderers must be exempt (DOCS).

#### MoreCulling
- **Repo / licence:** github.com/fxmorin/moreculling, `master`, 2026-10-06, **GPL-3.0** (author also forbids merging into clients without permission).
- **What it changes (DOCS):** aggressive block-state face culling (cull against neighbours with full faces even when vanilla does not), leaves culling (inner leaf faces), item-frame and painting back/side culling, sign text, beacon beam, cloud and rain culling. SOURCE confirms leaf-type mixins (`LeavesBlock_typesMixin`).
- **Bottleneck:** GPU (overdraw, vertex count) and mesh size.
- **Fits LegacyCraft:** the general ideas (skip faces that can never be seen) fit `SectionMesher.faceVisible`. Leaves culling **changes how fancy leaves look** and must be an Addition, not a default.

#### Enhanced Block Entities (EBE)
- **Repo / licence:** github.com/FoundationGames/EnhancedBlockEntities, `1.21.4`, 2025-05-26, **LGPL-3.0**.
- **What it changes (DOCS + SOURCE mixin names):** chests, ender chests, signs, beds, shulker boxes, bells are baked into chunk meshes as block models; the dynamic entity model is drawn only while animating. README benchmark: 1,700 chests, +155% FPS.
- **Fits LegacyCraft:** yes for chests and signs once they exist as tile-entity renderers. Visible side effect: smooth lighting on chests, which vanilla 1.8.9 does not have, so the static model must be lit like the entity model to stay faithful.

#### Nvidium (optional research)
- **Repo / licence:** github.com/MCRcortex/nvidium, `dev`, 2024-08-20, **LGPL-3.0**.
- **What it does (DOCS):** replaces Sodium's backend with NVIDIA mesh shaders (Turing or newer) and GPU-driven culling of huge terrain.
- **Fits LegacyCraft:** no as a dependency (vendor-specific, needs GL 4.6 + `GL_NV_mesh_shader`). The idea of GPU-side visibility is noted only for very large view distances.

### Game logic and world generation

#### Lithium
- **Repo / licence:** github.com/CaffeineMC/lithium, `develop`, 2026-10-05, **LGPL-3.0**. Options listed in `lithium-fabric-mixin-config.md` (DOCS, every option has a one-line description).
- **What it changes (SOURCE for the ones read):**
  - `world.chunk_access` (`ServerChunkCacheMixin`): the recent-chunk cache is scanned with one key comparison per slot, lambdas replaced with plain branches, no duplicate tickets.
  - `alloc.*`: removes allocations in hot paths (`Enum#values()` copies, lambdas, iterators).
  - `collections.*`: fastutil primitive maps instead of boxed `HashMap`s.
  - `shapes.*`, `entity.collisions.*`: specialised cuboid shapes and cheaper block access during movement.
  - `world.block_entity_ticking.sleeping`: idle furnaces, hoppers, brewing stands stop ticking until something changes.
  - `math.sine_lut`: smaller sine table (same values).
  - `world.tick_scheduler`: packed scheduled-tick collections.
- **Bottleneck:** CPU and GC on the server tick.
- **Vanilla:** the default set claims identical behaviour; deviations live under `minimal_nonvanilla` and are off-limits for LegacyCraft defaults.
- **Fits LegacyCraft:** very well, and the profile above shows LegacyCraft has the exact problems Lithium fixes (boxed chunk keys, `Direction.values()` per step).

#### C2ME
- **Repo / licence:** github.com/RelativityMC/C2ME-fabric, `dev/26.3.0`, 2026-10-07. **Mixed: MIT for most files, some modules all-rights-reserved** (`licenses/LICENSE-ARR.txt`).
- **What it changes (DOCS):** runs chunk generation, chunk I/O and lighting on several threads with its own scheduler; adds checks (`CheckedThreadLocalRandom`) to catch thread-unsafe code.
- **Fits LegacyCraft:** the idea (generate off the main thread, publish finished chunks to the world on the owning thread) is LegacyCraft's biggest remaining stutter source: `WorldRenderer.generateAround` spends up to 6 ms of every frame generating on the render thread. **Risk is high**: 1.8.9 population writes into neighbouring chunks, and the order of population affects results, so parallel generation must preserve the order in which populations run or worlds change. Terrain shaping (noise, surface, caves) is per-chunk and safe to parallelise; population is not.

#### Noisium
- **Repo / licence:** github.com/Steveplays28/noisium, `1.20-1.20.1`, 2025-06-24, archived, **LGPL-3.0**.
- **What it changes (DOCS):** writes noise-generated block states straight into chunk section storage instead of through the general `setBlockState` path; claims 1:1 worldgen parity.
- **Fits LegacyCraft:** check whether `ChunkPrimer` → `Chunk` copying goes through per-block setters; if so, write sections in bulk. Low risk, needs the worldgen checksum.

#### Very Many Players (VMP)
- **Repo / licence:** github.com/RelativityMC/VMP-fabric, `ver/26.1.1`, 2026-04-01, **MIT**.
- **What it changes (DOCS + SOURCE package names):** "area maps" for nearby-player and entity-tracking lookups (`playerwatching/optimize_nearby_*`), custom chunk sending with rate limiting, no per-packet flush (`networking/no_flush`), cached biome access.
- **Fits LegacyCraft:** the dedicated server (`server/dedicated`) checks `player.loadedChunks.contains(World.chunkKey(...))` per viewer per event; with many players an area map (players indexed by chunk) and batched flushing apply.

#### Phosphor / Starlight (lighting)
- **Phosphor:** github.com/CaffeineMC/phosphor-fabric, `1.19.x/dev`, 2024-01-28, archived, **LGPL-3.0**. SOURCE: mixins on the light providers, nibble-array storage and `LevelPropagator`; double-buffered primitive maps (`DoubleBufferedLong2ObjectHashMap`).
- **Starlight:** github.com/Tuinity/Starlight, `fabric`, 2024-03-08, **LGPL-3.0**. `TECHNICAL_DETAILS.md` (DOCS, written by the author): a single breadth-first propagator for block and sky light, increase and decrease queues with "re-check" semantics, positions and levels packed into primitive arrays, no hash maps on the hot path. Claims identical results to vanilla; vanilla 1.20 adopted most of it.
- **Fits LegacyCraft:** directly. `LightEngine` already uses the same breadth-first structure; the cost is boxing (`ArrayDeque<Long>`, `ArrayDeque<long[]>`), `Direction.values()` per step and a hash lookup per neighbour. Replacing the queues with a primitive ring buffer gives the same light field.

### Memory, loading and smaller optimisations

#### FerriteCore
- **Repo / licence:** github.com/malte0811/FerriteCore, `26.1`, 2026-03-24, **MIT**. `summary.md` (DOCS) lists each change with measured savings.
- **What it changes:** shares block-state neighbour tables and property maps (600 MB and 170 MB saved in a large pack), deduplicates model predicates, multipart models, collision shapes, quad vertex arrays and `ModelResourceLocation` strings.
- **Fits LegacyCraft:** the principle "one canonical immutable object per value" applies to `BlockState`: `BlockState.of` allocates on every call (every `World.getState`). A per-block array of the 16 metadata states removes that garbage with identical semantics (`equals` is already value-based).

#### FoamFix
- **Repo / licence:** github.com/asiekierka/FoamFix, `0.9.x-1.12.2`, 2025-02-02, **LGPL-3.0** (API MIT; one class MIT from SpongeCommon).
- **What it changes (SOURCE class names):** deduplicated property values and block states (`PropertyValueDeduplicator`, `FoamyBlockState`), fast air lookup (`FastAirLookupPatch`), cached hash codes, cheaper `BlockPos` (`BlockPosPatch`), a mob-spawner logic cache, faster hoppers, model loader cleanup after baking, deflater level for network compression, Trove-based deduplicating storage.
- **Fits LegacyCraft:** the closest-era reference (1.12.2 has 1.8.9's block-state design). Block-state interning, cached hashes and dropping model-loading intermediates after baking all apply.

#### ModernFix
- **Repo / licence:** github.com/embeddedt/ModernFix, `1.20`, 2026-10-10, **LGPL-3.0**.
- **What it changes (DOCS + SOURCE package names):** "dynamic resources" bakes block and item models on demand instead of all at start-up; faster texture stitching; deduplication; many targeted bug fixes.
- **Fits LegacyCraft:** start-up and F3+T reload time once the asset set grows; low priority today (the F3+T reload work is owned by another thread).

#### BetterFps
- **Repo / licence:** github.com/Guichaguri/BetterFps, `1.12`, 2018-10-23, **LGPL-2.1**. Supported 1.7.10 to 1.12 (DOCS, branch names).
- **What it changes (SOURCE):** replaces `MathHelper.sin/cos` with alternatives (Riven's 4096-entry table, Taylor series, libGDX, `java.lang.Math`); fast hopper, fast beacon, fog and creative-search patches.
- **Vanilla:** **not preserved.** Vanilla 1.8.9 uses a 65,536-entry table (LegacyCraft's `MathHelper` matches it); a 4096-entry table returns different values, which changes mob movement, projectile paths and anything seeded through trig. **Reject the math replacements.** The hopper and beacon caches are fine in principle.

#### BadOptimizations
- **Licence / repo:** **MIT** (Modrinth page), github.com/ItsThosea/BadOptimizations (could not be cloned; repository name differs between pages). Supports 1.19.1+ (DOCS).
- **What it changes (DOCS):** skips lightmap recomputation and upload when nothing affecting it changed, skips debug renderers when off, caches sky colour.
- **Fits LegacyCraft:** `WorldRenderer.bindLightmap` already re-uploads only when `LightMap.version()` changes; check that `LightMap` itself recomputes only on change. Sky colour caching applies to `SkyRenderer`.

#### Krypton
- **Repo / licence:** github.com/astei/krypton, `master`, 2026-09-30, **LGPL-3.0**.
- **What it changes (SOURCE mixin names):** optimised VarInt and UTF-8 encoding (`microopt/VarIntMixin`, `Utf8StringMixin`), a cheaper frame decoder and prepender, native (Velocity) compression and encryption, fewer flushes.
- **Fits LegacyCraft:** applies to `net.minecraft.network` and the dedicated server once player counts grow. Pure-Java parts (VarInt sizing, flush consolidation) are portable; native compression would add a dependency.

#### LazyDFU
- **Repo / licence:** github.com/astei/lazydfu, `master`, 2022-06-07, **MIT**.
- **What it changes (DOCS):** defers DataFixerUpper rule compilation until a migration is needed (58 s → 0.5 s start-up in the README's test).
- **Fits LegacyCraft:** **not applicable**: 1.8.9 has no DataFixerUpper. The general idea (build expensive tables on first use) applies to world conversion and registries.

#### LegacyEnhance
- **Could not be located** as a public repository or Modrinth project during this research (UNVERIFIED). Not used as a reference.

### Additional mods with relevant techniques

#### Patcher (1.8.9)
- **Repo / licence:** github.com/Sk1erLLC/Patcher, `master`, 2024-03-25, **CC BY-NC-SA 4.0** (ideas only).
- **What it changes (DOCS):** the most relevant 1.8.9-specific list: entity culling with a configurable interval, entity back-face culling, "optimized font renderer" and font data cache, HUD caching, static particle colour (no lighting lookup per particle per frame), chunk-update limit, reduced animation tick rate, batched model rendering, optimised world swapping. Also fixes vanilla's frustum culling false negatives (MC-63020, MC-70850).
- **Fits LegacyCraft:** confirms which costs dominated 1.8.9 clients in practice: text, particles, entities, and per-section state changes.

#### Dynamic FPS
- **Repo / licence:** github.com/juliand665/Dynamic-FPS, `main`, 2026-09-28, **MIT**.
- **What it changes (DOCS):** throttles rendering when the window is unfocused, hidden or idle (to as low as 1 FPS, while polling input at 15 Hz), and stops rendering the world under the loading overlay.
- **Fits LegacyCraft:** cheap, isolated, saves power; would be a Launcher/Accessibility Addition or an option, since vanilla 1.8.9 only has a menu frame-rate cap.

## Prioritised LegacyCraft optimisations

Impact is from the profile and benchmarks where available; cost and risk
are estimates. Each item names its correctness check.

| # | Optimisation | Reference | Files | Impact | Cost | Risk | Correctness check |
|---|---|---|---|---|---|---|---|
| 1 | **Primitive, well-hashed chunk map** (no boxing, no tree bins) | Lithium `world.chunk_access`, vanilla `LongHashMap` | `World.java`, new `util/LongHashMap.java` | High (all world access; 37% of worldgen garbage, ~28% of its CPU) | Low | Low | worldgen checksum identical; full test suite |
| 2 | **Primitive light queues**, cached `Direction` array | Starlight, Phosphor, Lithium `alloc.enum_values` | `LightEngine.java` | High (≈50% of worldgen CPU) | Low | Low | worldgen and relight checksums identical; `LightEngineTest` |
| 3 | **Canonical `BlockState` objects** | FerriteCore, FoamFix | `BlockState.java` | Medium (garbage on every block read) | Low | Low (identity comparisons become true more often) | full suite; blockaccess allocation |
| 4 | **Section occlusion graph** (vanilla 1.8.9 `VisGraph`) | Sodium `OcclusionCuller`, vanilla | `WorldRenderer`, `SectionMesher` | High in caves/underground and at long view distance | Medium | Medium (popping if wrong) | headless unit test on the visibility sets; screenshots on real hardware |
| 5 | **Face-direction buckets** per section | Sodium block-face culling | `SectionMesher`, `WorldRenderer` | Medium (up to ~half the opaque vertices skipped) | Medium | Low | identical pixels; draw-call and vertex counters |
| 6 | **Main-thread work in meshing**: per-block render-layer table instead of `Set<String>` lookups, no `synchronized` model lookup after warm-up, no `Integer[]` sort in translucency | Sodium, Lithium `alloc` | `SectionMesher` | Medium (mesh build time) | Low | Low | mesh byte-equality test |
| 7 | **Batch immediate-mode draws** (text first, then particles, mobs) | ImmediatelyFast, Patcher | `FontRenderer`, `ParticleManager`, `MobRenderer` | Medium in GUI-heavy and mob-heavy scenes | Medium | Low-medium (draw order for blended geometry) | screenshots |
| 8 | **Entity occlusion culling** off the main thread | Entity Culling, Patcher | entity rendering | Medium in entity-heavy scenes | Medium | Medium | entities visible through glass/leaves; pop-in |
| 9 | **Generate terrain off the main thread**, populate in the existing order on the main thread | C2ME, Noisium | `WorldRenderer.generateAround`, `World` | High for stutter while exploring | High | High (population order changes worlds) | worldgen golden tests, checksum across many seeds |
| 10 | **Compact vertex format / region buffers** | Sodium | `SectionMesher`, `WorldRenderer` | Medium (GPU bandwidth, VRAM) | High | Medium (UV precision, needs a shader) | identical pixels |
| 11 | **Block-entity baked models** (chests, signs) | EBE | tile-entity renderers | High with many chests | Medium | Medium (lighting must match the entity model) | screenshots |
| 12 | **Server: area maps, batched flush, VarInt micro-opts** | VMP, Krypton | `server/dedicated`, `network` | Medium with many players | Medium | Low | multiplayer tests |
| 13 | **Unfocused / idle frame cap** (as an Addition) | Dynamic FPS | `Minecraft.java` | Power and heat, not FPS | Low | Low | manual |

Rejected: BetterFps math replacements (change gameplay values), MoreCulling
leaves culling as a default (changes fancy leaves; acceptable only as an
Addition), Nvidium (vendor-specific), LazyDFU (no DFU in 1.8.9).

## Implementation log

| Order | Change | Status | Result (cloud, CPU time; see performance-benchmarks.md) |
|---|---|---|---|
| 1 | Primitive chunk map (`util/LongHashMap`) | Done | worldgen −26%, block access −34%, mob ticks −44%; worldgen garbage −59%; identical world |
| 2 | Primitive light queues (`LightEngine.LongQueue`) | Done | worldgen garbage a further −61% (−84% overall), worldgen CPU −8%; identical light |
| 3 | Canonical `BlockState` objects | Next | |
