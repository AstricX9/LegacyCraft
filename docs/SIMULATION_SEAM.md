# Simulation seam (multiplayer groundwork)

Status: **step 1 of 3 — vocabulary only. Nothing in the game uses it yet.**
Code: `net.minecraft.sim` (`PlayerCommand`, `GameEvent`, `CommandSink`, `EventSink`, `MessageQueue`, `EventEntityHost`). Wire packets: `net.minecraft.network`.

## Why

`Minecraft.java` (~4,300 lines, ~200 methods) is client and simulation in one class. A rough name-based sort puts about a quarter of it squarely on the client side (rendering, input, GUI, sound), a quarter squarely on the simulation side (spawning, explosions, fluids, block and mob logic), and the rest on the boundary, where one method reads input, changes the world and plays a sound. Multiplayer needs those separated: the simulation decides and changes the world, the viewer shows the result.

## Rules

1. **Commands are requests, not facts.** The simulation checks reach, line of sight, held item, mining time and game mode, and may ignore or correct a command (it answers a rejected move with `PlayerPosition`).
2. **Events are snapshots.** An emitter must not change anything an event carries (`BlockState`, `ItemStack`) after emitting it, so a sink may keep, batch or send it later.
3. **One tick order.** Commands wait in a `MessageQueue` and are applied at the start of the next tick, whether they came from the local player or a socket. Events are drained by the viewer when it is ready.
4. **Events describe what a viewer needs, not simulation internals.** Which events a viewer receives is the interest manager's decision (nearby chunks and entities), not the emitter's.
5. **Hot paths stay cheap.** Records and a lock-free queue; the network layer batches block changes per chunk (`MultiBlockChangePacket`) and sends small entity moves as deltas (`EntityMovePacket`).

## Input to command

| Today (`Minecraft.interact`) | Command |
|---|---|
| attack click on an entity (`attackEntity`) | `UseEntity(id, ATTACK)` |
| right click on an entity (`interactWithEntity`) | `UseEntity(id, INTERACT)` |
| attack held on a block (`playerController.clickBlock/onPlayerDamageBlock`, then `breakBlock`) | `Dig(START / ABORT / FINISH)` |
| right click on a block (`activateBlock`, `placeHeldBlock`, `useBucket`, `useFlintAndSteel`, `useSpawnEgg`, `useSkull`) | `UseOnBlock` |
| right click in the air (`useHeldItem`) | `UseItem` |
| use key released (`releaseBow`) | `ReleaseUse(heldTicks)` |
| pick block | `PickBlock` |
| hotbar | `SelectSlot` |
| boat steering (`EntityBoat.setRiderInput`), dismount | `Steer`, `Dismount` |
| walking | `Move` |

## Effect to event

`EntityHost` already is most of the event vocabulary. `EventEntityHost` maps its effect-only calls: `playSound` to `Sound`, `particle` to `Particle`, `blockBreakEffects` to `BlockBreakEffects`, `waterSplash` to `EntityStatus(SPLASH)`, `achievement` to `AchievementAwarded`, `giveExperience` to `ExperienceGained`, and so on. World changes (`world.setState`) become `BlockChanged`. The calls that need the world itself (`player`, `entities`, `spawn`, `explode`, rules) stay with the simulation.

## Example: `breakBlock` today and after the split

Today one method decides (harvestable? creative sword rule? hardness), drops items, wears the tool, adds exhaustion, spawns particles, plays the sound and sets the block. After the split the simulation does the first group and the world change and emits `BlockChanged`, `BlockBreakEffects` and the item spawns; the client turns the events into particles and sound.

## Known hard parts (for steps 2 and 3)

- **The player's state has no server-side home.** `Player`, its inventory and exhaustion live in `net.minecraft.client.entity`, and many "simulation" methods reach into them. The player's data model has to move to a side-neutral package first.
- **Mining progress is client-side today** (`playerController`). It becomes client prediction with the server validating `Dig FINISH` against elapsed time and tool.
- **Targeting uses the client camera** (`target()`, `pointedEntity`). The client sends an entity id or block position; the server re-checks reach (3 blocks for entities in Survival, 6 in Creative, as now).
- **Prediction and correction** for movement and block placement, so the local player does not feel latency.
- **Local mode versus remote mode.** In single-player the renderer can keep reading the live world and entities with no copying. A remote client holds replicas built from `EntitySpawned` / `EntityMoved` / `EntityRemoved`. This split is the main decision for step 2.

## Next

Step 2 moves simulation behind the seam one area at a time (block break and place, then item use, mobs and combat, dimensions last), each with tests on the simulation side and a short in-game checklist. Step 3 puts `net.minecraft.network` between the two sides.
