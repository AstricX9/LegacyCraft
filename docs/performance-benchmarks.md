# Performance benchmarks

Baseline and before/after results for the optimisations chosen in
[performance-mod-research.md](performance-mod-research.md). Every change
is kept only if its benefit reproduces and the result checksums (the
generated world, the light field) are unchanged.

## How to run

```
gradlew benchmark -Pbench=worldgen        # one scenario
gradlew benchmark                         # all five, one JVM (less precise)
```

To compare two checkouts, run each scenario in a fresh JVM, alternating
between them, several times:

```
java -Xms2g -Xmx2g -cp "$(gradlew -q printBenchmarkClasspath)" net.minecraft.benchmark.EngineBenchmark worldgen
```

Each run prints, over its measured rounds: median, best and 90th-percentile
wall time; median CPU time of the benchmark thread; bytes allocated per
round; garbage collections and their pause time during the measured
rounds; and a checksum of the result.

## Scenarios (`src/benchmark/java/net/minecraft/benchmark/EngineBenchmark.java`)

All use seed 12345 and the default overworld. They are headless: no window
or OpenGL context, so terrain meshing, drawing, GPU time and draw calls are
**not** covered here (see "Not yet measured").

| Scenario | Rounds (warm-up + measured) | One round | Checksum |
|---|---|---|---|
| `worldgen` | 2 + 6 | Generate, populate and light every chunk within radius 8 (289 chunks) of a new world, nearest ring first like the renderer | Block IDs and light of every chunk |
| `snapshot` | 5 + 15 | Copy every non-empty section within radius 8 the way `WorldRenderer` does before meshing (`SectionSnapshot`) | Sample cells |
| `blockaccess` | 5 + 15 | 2,000,000 random `getState` calls and 200,000 `collisionBoxesAt` calls over loaded terrain (entity physics and ticking) | Sum of packed states and box counts |
| `relight` | 6 + 20 | Place 300 glowstone blocks at random spots, then restore what was there (two relights per spot) | Light of every chunk; must equal the start |
| `mobs` | 6 + 16 | Spawn up to 120 mobs around a player at night with the real spawner, then tick every entity 100 times | Entity count (varies: mobs use unseeded randomness) |

## Test conditions

**Cloud first pass (2026-10-11).** Intel Xeon @ 2.80 GHz, 4 vCPUs, 15 GB RAM,
shared virtual machine; OpenJDK 17.0.20, G1, `-Xms2g -Xmx2g`. Each variant
ran in 5 separate JVMs (4 for `mobs`), alternating with the others; the
tables give the median across JVMs of each JVM's median. Wall times on this
machine jumped by 2x or more between otherwise identical runs, so CPU time
and allocation are the figures to trust; differences under about 10% are
noise here. Real-hardware numbers (Andriy's PC) are still to come.

Variants:

- **main**: `origin/main` at 2616f4b, with only the benchmark added.
- **#1**: primitive chunk map (`util/LongHashMap`).
- **#1+#2**: plus primitive light queues in `LightEngine`.

## Baseline (main)

| Scenario | CPU / round | Allocated / round | GCs (pause) in measured rounds |
|---|---|---|---|
| worldgen | 7,643 ms (26 ms per chunk) | 4,121 MB (14 MB per chunk) | 21 (137 ms) |
| snapshot | 261 ms | 213 MB | 3 (31 ms) |
| blockaccess | 618 ms | 193 MB | 2 (29 ms) |
| relight | 102 ms | 67 MB | 1 (9 ms) |
| mobs | 162 ms; 1.19 ms per entity tick round | 144 MB | 2 (44 ms) |

Where the time goes (JFR, worldgen on main): 50% light propagation, 28%
chunk-map lookups (`HashMap.get`, mostly tree bins), 13% terrain shaping.
Allocation: 38% boxed `Long`, 37% `reflect.Type[]` from tree-bin key
comparison, 16% `Direction[]` copies.

## Results

### #1: primitive chunk map

| Scenario | CPU main → #1 | Allocated main → #1 | Checksum |
|---|---|---|---|
| worldgen | 7,643 → 5,676 ms (**−26%**) | 4,121 → 1,688 MB (**−59%**) | identical |
| snapshot | 261 → 209 ms (**−20%**) | 213 → 163 MB (−23%) | identical |
| blockaccess | 618 → 410 ms (**−34%**) | 193 → 81 MB (**−58%**) | identical |
| relight | 102 → 97 ms (noise) | 67 → 38 MB (−43%) | identical |
| mobs | 162 → 110 ms (**−32%**); ticks 1.19 → 0.67 ms | 144 → 79 MB (−45%) | n/a |

GCs during worldgen: 21 → 9. Tests: same results as main (see below).

### #2: primitive light queues (on top of #1)

| Scenario | CPU #1 → #1+#2 | Allocated #1 → #1+#2 | Checksum |
|---|---|---|---|
| worldgen | 5,676 → 5,242 ms (−8%) | 1,688 → 654 MB (**−61%**) | identical |
| relight | 97 → 105 ms (noise; a second 4-JVM run gave 77 → 81 ms) | 38 → 14 MB (**−63%**) | identical |
| mobs | 110 → 111 ms; ticks 0.67 → 0.67 ms | 79 → 78 MB | n/a |
| snapshot, blockaccess | not affected by this change (code path untouched) | unchanged | identical |

GCs during worldgen: 9 → 5. Overall, main → #1+#2: worldgen **−31% CPU and
−84% garbage**, with the same world and the same light.

### Correctness

- Result checksums identical across main, #1 and #1+#2 for worldgen
  (`a962aa59c14e62a7`), snapshot, blockaccess and relight.
- `gradlew test`: main 543 tests, 20 failures; with both changes 548 tests
  (5 new), the same 20 failures. `IronGolemTest.huntsAndHitsTheNearestHostileMob`
  is flaky on main as well (passed 2 of 3 reruns on both).
- New tests: `LongHashMapTest` (agrees with `HashMap` through growth,
  insertion order, spread of packed chunk keys) and
  `LightEngineTest.theLightQueueIsFirstInFirstOutThroughGrowthAndReuse`.

### Notes found while benchmarking

- An earlier version of the `mobs` scenario generated only radius 6; mobs that
  walked to the edge made `MovingEntity.collisionBoxes` and
  `BlockGrass.snowy` (through `World.getState`) generate new chunks on the
  tick thread, which made rounds 5–8x slower at random. Vanilla stops
  updating entities near unloaded chunks instead. In the game the renderer
  keeps the area around the player generated, so this mostly matters for the
  dedicated server; it is listed as a follow-up.

## Not yet measured

These need the real game window, so they will come from Andriy's PC:
average FPS and frame-time percentiles, CPU vs GPU frame time, mesh build
and upload time, draw calls and rendered geometry, start-up and resource
reload time, and long sessions (heap over time). Test worlds to use: static
terrain at spawn, rapid flight through unexplored terrain, a dense forest,
a cave system, an entity-heavy farm, torch placement for lighting updates.
