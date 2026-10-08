package net.minecraft.server.dedicated;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockDrops;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityArrow;
import net.minecraft.entity.EntityItem;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityManager;
import net.minecraft.inventory.CraftingGrid;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.network.Packet;
import net.minecraft.network.v47.Clientbound;
import net.minecraft.network.v47.Metadata;
import net.minecraft.network.v47.Slot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.WorldStorage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A headless LegacyCraft server that 1.8.9 clients (protocol 47, offline
 * mode) can join. One thread runs the 20 TPS tick: it admits logged-in
 * players, applies their packets ({@link PlayHandler}), ticks the world and
 * dropped items, runs each player's health and hunger ({@link PlayerLife}),
 * streams chunks, forwards block changes, keeps entities visible and keeps
 * each client's inventory in step. Sockets are read and written on their own
 * threads ({@link ServerConnection}).
 *
 * <p>Run from the game directory: {@code server.properties} and the world
 * folder (level-name) live there. The world and every player's data are
 * saved every five minutes and on shutdown.
 */
public final class DedicatedServer {
    public static final int TICKS_PER_SECOND = 20;
    private static final long TICK_NANOS = 1_000_000_000L / TICKS_PER_SECOND;
    private static final int CHUNKS_PER_TICK = 8;
    private static final int AUTOSAVE_TICKS = 5 * 60 * TICKS_PER_SECOND;
    private static final int KEEP_ALIVE_INTERVAL = 15 * TICKS_PER_SECOND;
    private static final int KEEP_ALIVE_TIMEOUT = 30 * TICKS_PER_SECOND;
    /** Past this many changed blocks in one column in one tick, the whole column is resent. */
    private static final int MULTI_CHANGE_LIMIT = 64;
    /** Dropped items and arrows are shown within this many blocks, mobs within 80 (vanilla's tracking ranges). */
    private static final double ITEM_RANGE = 64, MOB_RANGE = 80;

    private final ServerProperties properties;
    final BlockRegistry blocks;
    final ItemRegistry items;
    final ItemCodec codec;
    final CraftingManager crafting;
    final BlockDrops drops;
    final World world;
    private final Path worldDirectory;
    private final PlayerData playerData;
    final int spawnX, spawnY, spawnZ;
    final Random random = new Random();

    private final Queue<PendingJoin> joins = new ConcurrentLinkedQueue<>();
    /** Work handed to the tick thread from elsewhere, run at the start of the next tick. */
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final Map<UUID, ServerPlayer> players = new LinkedHashMap<>();
    private volatile int playerCount;
    private final PlayHandler handler = new PlayHandler(this);
    final PlayerLife life = new PlayerLife(this);
    final EntityManager entities = new EntityManager();
    final ServerMobs mobs = new ServerMobs(this);
    private long tick;
    long timeOfDay;
    private volatile boolean running = true;
    private ServerSocket listener;

    /** Changed blocks per column since the last flush (null value: resend the whole column). */
    private final Map<Long, Set<Integer>> changedBlocks = new HashMap<>();

    private record PendingJoin(ServerConnection connection, String name, UUID uuid) {}

    public DedicatedServer(Path gameDirectory) throws IOException {
        Files.createDirectories(gameDirectory);
        properties = ServerProperties.load(gameDirectory.resolve("server.properties"));
        var resources = new ClasspathResourceManager();
        blocks = BlockRegistry.load(resources);
        items = ItemRegistry.load(resources, blocks);
        codec = new ItemCodec(blocks, items);
        crafting = CraftingManager.load(resources, items);
        drops = BlockDrops.load(resources, BlockDrops.itemsOf(items.values()));
        BiomeRegistry biomes = BiomeRegistry.load(resources, blocks);
        worldDirectory = gameDirectory.resolve(properties.levelName);
        if (WorldStorage.exists(worldDirectory)) {
            world = WorldStorage.load(worldDirectory, blocks, biomes);
            log("Loaded world \"" + properties.levelName + "\" (seed " + world.seed() + ")");
        } else {
            world = new World(blocks, biomes, parseSeed(properties.levelSeed));
            log("Created world \"" + properties.levelName + "\" (seed " + world.seed() + ")");
        }
        world.setSmelting(net.minecraft.item.crafting.FurnaceRecipes.load(resources, items), this::itemByName);
        world.addListener(new World.Listener() {
            @Override public void blockChanged(int x, int y, int z) { blockChangedAt(x, y, z); }
            @Override public void chunkChanged(int chunkX, int chunkZ) { changedBlocks.put(World.chunkKey(chunkX, chunkZ), null); }
        });
        world.setEvents(new World.Events() {
            @Override public void washedAway(int x, int y, int z, BlockState previous) { dropBlock(x, y, z, previous); }
            @Override public void popped(int x, int y, int z, BlockState previous) { dropBlock(x, y, z, previous); }
            @Override public void containerBroken(int x, int y, int z, List<ItemStack> contents) {
                for (ItemStack stack : contents) spawnItem(EntityItem.fromBlock(world, x, y, z, stack, random));
            }
        });
        playerData = new PlayerData(worldDirectory.resolve("playerdata"), this);
        timeOfDay = playerData.loadTime();
        int[] spawn = world.findSpawn();
        spawnX = spawn[0];
        spawnZ = spawn[1];
        spawnY = surfaceY(spawnX, spawnZ);
        log("Spawn is " + spawnX + ", " + spawnY + ", " + spawnZ);
    }

    public static void main(String[] args) throws IOException {
        Path directory = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        DedicatedServer server = new DedicatedServer(directory);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "Server shutdown"));
        Thread console = new Thread(() -> server.console(System.in), "Server console");
        console.setDaemon(true);
        console.start();
        server.run();
    }

    public ServerProperties properties() { return properties; }
    public int playerCount() { return playerCount; }
    public Collection<ServerPlayer> players() { return players.values(); }
    long tick() { return tick; }
    Item itemByName(String name) { return items.get(new ResourceLocation("minecraft", name)); }

    // ---- main loop -------------------------------------------------------------------------

    public void run() throws IOException {
        listener = new ServerSocket();
        listener.bind(properties.serverIp.isEmpty() ? new InetSocketAddress(properties.port)
            : new InetSocketAddress(properties.serverIp, properties.port));
        log("Listening on port " + listener.getLocalPort() + " (Minecraft 1.8.9, offline mode)");
        Thread acceptor = new Thread(this::acceptLoop, "Server listener");
        acceptor.setDaemon(true);
        acceptor.start();

        long next = System.nanoTime();
        while (running) {
            try {
                tickOnce();
            } catch (RuntimeException e) {
                System.err.println("[Server] Tick " + tick + " failed:");
                e.printStackTrace();
            }
            next += TICK_NANOS;
            long wait = next - System.nanoTime();
            if (wait > 0) {
                try { Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000)); } catch (InterruptedException e) { break; }
            } else if (wait < -2_000_000_000L) {
                log("Can't keep up! Skipping " + (-wait / TICK_NANOS) + " ticks");
                next = System.nanoTime();
            }
        }
        shutdown();
    }

    /** Port the server is listening on (useful when it was configured as 0). */
    public int boundPort() { return listener == null ? -1 : listener.getLocalPort(); }

    public void stop() { running = false; }

    /** Typing "stop" in the terminal saves the world and shuts down. */
    private void console(java.io.InputStream input) {
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(input))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.trim().equalsIgnoreCase("stop")) { stop(); return; }
                if (!line.isBlank()) log("Unknown console command. The only one so far is: stop");
            }
        } catch (IOException ignored) {}
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = listener.accept();
                new ServerConnection(this, socket).start();
            } catch (IOException e) {
                if (running) System.err.println("[Server] Accept failed: " + e.getMessage());
            }
        }
    }

    void queueJoin(ServerConnection connection, String name, UUID uuid) { joins.add(new PendingJoin(connection, name, uuid)); }

    /** Runs this on the tick thread at the start of the next tick (the world and entities are only touched there). */
    void execute(Runnable task) { tasks.add(task); }

    private void tickOnce() {
        tick++;
        timeOfDay++;
        for (Runnable task; (task = tasks.poll()) != null; ) task.run();
        for (PendingJoin join; (join = joins.poll()) != null; ) admit(join);
        for (ServerPlayer player : List.copyOf(players.values())) {
            if (player.connection.isClosed()) { remove(player); continue; }
            for (Packet packet; (packet = player.connection.poll()) != null; ) handler.handle(player, packet);
            life.tick(player);
            keepAlive(player);
        }
        world.tick();
        mobs.beforeTick();
        entities.tick();
        mobs.afterTick();
        pickUpItems();
        for (ServerPlayer player : players.values()) streamChunks(player, CHUNKS_PER_TICK);
        flushBlockChanges();
        trackPlayers();
        trackEntities();
        for (ServerPlayer player : players.values()) {
            syncInventory(player, false);
            syncEquipment(player);
            life.syncHealth(player);
        }
        if (tick % TICKS_PER_SECOND == 0) broadcast(new Clientbound.TimeUpdate(tick, timeOfDay));
        if (tick % AUTOSAVE_TICKS == 0) save();
    }

    private void shutdown() {
        for (ServerPlayer player : players.values()) player.connection.kick("Server closed");
        try { listener.close(); } catch (IOException ignored) {}
        save();
        log("Stopped");
    }

    private synchronized void save() {
        try {
            WorldStorage.save(worldDirectory, world);
        } catch (IOException e) {
            System.err.println("[Server] Saving the world failed: " + e.getMessage());
        }
        for (ServerPlayer player : players.values()) playerData.save(player);
        playerData.saveTime(timeOfDay);
    }

    // ---- joining and leaving ---------------------------------------------------------------

    private void admit(PendingJoin join) {
        ServerConnection connection = join.connection();
        if (connection.isClosed()) return;
        ServerPlayer existing = players.get(join.uuid());
        if (existing != null) {
            existing.connection.kick("You logged in from another location");
            remove(existing);
        }
        if (players.size() >= properties.maxPlayers) { connection.kick("The server is full!"); return; }

        ServerPlayer player = new ServerPlayer(connection, join.name(), join.uuid(), entities.reserveId(),
            new CraftingGrid(2, 2, crafting, this::itemByName));
        player.gameMode = properties.gameMode;
        player.x = spawnX + 0.5;
        player.y = spawnY;
        player.z = spawnZ + 0.5;
        playerData.load(player);
        player.lastKeepAliveReplyTick = tick;
        players.put(player.uuid, player);
        playerCount = players.size();
        log(player.name + " joined from " + connection.address() + " (entity " + player.entityId + ")");

        connection.send(new Clientbound.JoinGame(player.entityId, player.gameMode, 0, properties.difficulty,
            Math.min(255, properties.maxPlayers), world.isFlat() ? "flat" : "default", false));
        connection.send(new Clientbound.SpawnPosition(spawnX, spawnY, spawnZ));
        connection.send(new Clientbound.ServerDifficulty(properties.difficulty));
        connection.send(abilities(player));
        connection.send(new Clientbound.TimeUpdate(tick, timeOfDay));
        connection.send(new Clientbound.HeldItemChange(player.inventory.currentItem));
        // Everyone already here goes on the newcomer's tab list, and the newcomer on everyone's.
        List<Clientbound.PlayerListItem.Entry> everyone = new ArrayList<>();
        for (ServerPlayer other : players.values()) everyone.add(listEntry(other));
        connection.send(new Clientbound.PlayerListItem(Clientbound.PlayerListItem.ADD, everyone));
        broadcastExcept(player, new Clientbound.PlayerListItem(Clientbound.PlayerListItem.ADD, List.of(listEntry(player))));
        // The chunks under the player before the position, so the client leaves "Downloading terrain" on solid ground.
        streamChunks(player, 25);
        teleport(player, player.x, player.y, player.z, player.yaw, player.pitch);
        syncInventory(player, true);
        life.syncHealth(player);
        broadcast(chat(translate("multiplayer.player.joined", "yellow", player.name), 1));
    }

    private void remove(ServerPlayer player) {
        if (players.remove(player.uuid) != player) return;
        playerCount = players.size();
        player.connection.close("Removed");
        // What was on the cursor or in a crafting grid goes back into the inventory before saving.
        player.openWindow.close(stack -> player.inventory.addItemStackToInventory(stack));
        if (player.openWindow != player.playerWindow) player.playerWindow.close(stack -> player.inventory.addItemStackToInventory(stack));
        playerData.save(player);
        mobs.removed(player);
        log(player.name + " left (" + player.connection.closeReason() + ")");
        hideFromOthers(player);
        broadcast(new Clientbound.PlayerListItem(Clientbound.PlayerListItem.REMOVE,
            List.of(new Clientbound.PlayerListItem.Entry(player.uuid, null, 0, 0))));
        broadcast(chat(translate("multiplayer.player.left", "yellow", player.name), 1));
    }

    /** Takes the player's entity off everyone else's screen (leaving or dying). */
    void hideFromOthers(ServerPlayer player) {
        for (ServerPlayer other : players.values()) {
            if (other.tracked.remove(player.entityId) != null)
                other.connection.send(new Clientbound.DestroyEntities(new int[]{player.entityId}));
        }
    }

    private void keepAlive(ServerPlayer player) {
        if (tick - player.lastKeepAliveReplyTick > KEEP_ALIVE_TIMEOUT) {
            player.connection.kick("Timed out");
            remove(player);
            return;
        }
        if (player.keepAliveSentTick < 0 && tick % KEEP_ALIVE_INTERVAL == 0) {
            player.keepAliveId = (int) tick;
            player.keepAliveSentTick = tick;
            player.connection.send(new Clientbound.KeepAlive(player.keepAliveId));
        }
    }

    void keepAliveAnswered(ServerPlayer player, int id) {
        if (player.keepAliveSentTick >= 0 && id == player.keepAliveId) {
            player.keepAliveSentTick = -1;
            player.lastKeepAliveReplyTick = tick;
        }
    }

    // ---- movement --------------------------------------------------------------------------

    /** Moves the client's player; its own moves are ignored until it reports having arrived. */
    void teleport(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        player.x = x;
        player.y = y;
        player.z = z;
        player.yaw = yaw;
        player.pitch = pitch;
        player.fallDistance = 0;
        player.awaitingTeleport = true;
        player.teleportX = x;
        player.teleportY = y;
        player.teleportZ = z;
        player.connection.send(new Clientbound.PlayerPositionLook(x, y, z, yaw, pitch, 0));
    }

    /** The y a player stands at on top of the column (the first air above the highest solid block). */
    int surfaceY(int x, int z) {
        for (int y = World.HEIGHT - 1; y > 0; y--) if (world.get(x, y, z).isSolid()) return y + 1;
        return World.SEA_LEVEL + 1;
    }

    // ---- chunks ----------------------------------------------------------------------------

    private int viewDistance(ServerPlayer player) { return Math.min(properties.viewDistance, Math.max(2, player.clientViewDistance)); }

    /**
     * Sends up to {@code budget} missing columns nearest the player first and
     * unloads the ones now out of range. A column is only sent once its
     * neighbours on the positive sides exist, so it has been populated.
     */
    void streamChunks(ServerPlayer player, int budget) {
        int radius = viewDistance(player), cx = player.chunkX(), cz = player.chunkZ();
        for (Iterator<Long> it = player.loadedChunks.iterator(); it.hasNext(); ) {
            long key = it.next();
            int x = (int) (key >> 32), z = (int) key;
            if (Math.abs(x - cx) > radius + 1 || Math.abs(z - cz) > radius + 1) {
                it.remove();
                player.connection.send(Clientbound.ChunkData.unload(x, z));
            }
        }
        List<long[]> wanted = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                long key = World.chunkKey(cx + dx, cz + dz);
                if (!player.loadedChunks.contains(key)) wanted.add(new long[]{dx * dx + dz * dz, cx + dx, cz + dz});
            }
        wanted.sort((a, b) -> Long.compare(a[0], b[0]));
        for (int i = 0; i < wanted.size() && i < budget; i++) {
            int x = (int) wanted.get(i)[1], z = (int) wanted.get(i)[2];
            world.getChunk(x + 1, z);
            world.getChunk(x, z + 1);
            world.getChunk(x + 1, z + 1);
            Chunk chunk = world.getChunk(x, z);
            player.loadedChunks.add(World.chunkKey(x, z));
            player.connection.send(ChunkEncoder.encode(world, chunk, !world.isNether()));
        }
    }

    private void blockChangedAt(int x, int y, int z) {
        long key = World.chunkKey(x >> 4, z >> 4);
        if (changedBlocks.containsKey(key) && changedBlocks.get(key) == null) return;
        Set<Integer> set = changedBlocks.computeIfAbsent(key, k -> new HashSet<>());
        set.add((x & 15) << 12 | (z & 15) << 8 | y);
        if (set.size() > MULTI_CHANGE_LIMIT) changedBlocks.put(key, null);
    }

    private void flushBlockChanges() {
        if (changedBlocks.isEmpty()) return;
        for (Map.Entry<Long, Set<Integer>> entry : changedBlocks.entrySet()) {
            long key = entry.getKey();
            int chunkX = (int) (key >> 32), chunkZ = (int) key;
            Packet packet = null;
            for (ServerPlayer player : players.values()) {
                if (!player.loadedChunks.contains(key)) continue;
                if (packet == null) packet = blockChangePacket(chunkX, chunkZ, entry.getValue());
                player.connection.send(packet);
            }
        }
        changedBlocks.clear();
    }

    private Packet blockChangePacket(int chunkX, int chunkZ, Set<Integer> positions) {
        if (positions == null) return ChunkEncoder.encode(world, world.getChunk(chunkX, chunkZ), !world.isNether());
        short[] packed = new short[positions.size()];
        int[] states = new int[positions.size()];
        int i = 0;
        for (int position : positions) {
            int x = chunkX * 16 + (position >> 12 & 15), z = chunkZ * 16 + (position >> 8 & 15), y = position & 255;
            packed[i] = (short) position;
            states[i++] = world.getState(x, y, z).packed();
        }
        if (packed.length == 1) {
            int p = packed[0];
            return new Clientbound.BlockChange(chunkX * 16 + (p >> 12 & 15), p & 255, chunkZ * 16 + (p >> 8 & 15), states[0]);
        }
        return new Clientbound.MultiBlockChange(chunkX, chunkZ, packed, states);
    }

    /** Puts the client's view of one block back to what the world has (after a refused edit). */
    void resendBlock(ServerPlayer player, int x, int y, int z) {
        if (y < 0 || y >= World.HEIGHT) return;
        player.connection.send(new Clientbound.BlockChange(x, y, z, world.getState(x, y, z).packed()));
    }

    // ---- dropped items ---------------------------------------------------------------------

    /** Set while a Creative player breaks a block, so the half of a door or bed it takes along drops nothing. */
    boolean suppressDrops;

    void spawnItem(EntityItem item) {
        if (item.stack() == null || item.stack().isEmpty()) return;
        entities.add(item);
    }

    /** A block's drops when the world removed it (washed away, lost its support). */
    void dropBlock(int x, int y, int z, BlockState previous) {
        if (suppressDrops) return;
        var block = previous.block();
        for (ItemStack stack : drops.drops(block.name().path(), block.material(), previous.metadata(), null, random))
            spawnItem(EntityItem.fromBlock(world, x, y, z, stack, random));
    }

    /** A stack thrown from the player's hand (the drop key, or dragged out of a window). */
    void throwItem(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        spawnItem(EntityItem.thrown(world, player.x, player.eyeY(), player.z, player.lookX(), player.lookY(), player.lookZ(),
            stack, false, random));
    }

    /** Living players walking over items pick up what fits, the item flying to them. */
    private void pickUpItems() {
        for (Entity entity : entities.values()) {
            if (!(entity instanceof EntityItem item) || item.isRemoved() || item.pickupDelay > 0) continue;
            for (ServerPlayer player : players.values()) {
                if (player.dead || player.gameMode == 3) continue;
                if (Math.abs(item.x() - player.x) > 1.425 || Math.abs(item.z() - player.z) > 1.425
                    || item.y() < player.y - 0.75 || item.y() > player.y + 2.3) continue;
                ItemStack stack = item.stack();
                int before = stack.count();
                player.inventory.addItemStackToInventory(stack);
                if (stack.count() == before) continue;
                int id = entities.idOf(item.uuid());
                if (stack.isEmpty()) {
                    Packet collect = new Clientbound.CollectItem(id, player.entityId);
                    player.connection.send(collect);
                    for (ServerPlayer viewer : players.values())
                        if (viewer != player && viewer.tracked.containsKey(id)) viewer.connection.send(collect);
                    item.remove();
                    break;
                }
                Packet update = new Clientbound.EntityMetadata(id, itemMetadata(stack));
                for (ServerPlayer viewer : players.values()) if (viewer.tracked.containsKey(id)) viewer.connection.send(update);
            }
        }
    }

    private byte[] itemMetadata(ItemStack stack) { return new Metadata().putSlot(10, codec.toSlot(stack)).build(); }

    /**
     * Shows each player the dropped items, mobs and arrows around them and
     * how they move, turn and change, and forgets the gone ones.
     */
    private void trackEntities() {
        Map<Integer, Entity> live = new HashMap<>();
        for (Entity entity : entities.values()) {
            if (entity.isRemoved()) continue;
            if (entity instanceof EntityItem || entity instanceof EntityArrow
                || entity instanceof EntityLiving mob && ServerMobs.mobType(mob) >= 0)
                live.put(entities.idOf(entity.uuid()), entity);
        }
        Set<Integer> playerIds = new HashSet<>();
        for (ServerPlayer player : players.values()) playerIds.add(player.entityId);
        for (ServerPlayer viewer : players.values()) {
            List<Integer> gone = new ArrayList<>();
            for (Map.Entry<Integer, ServerPlayer.Tracked> entry : viewer.tracked.entrySet()) {
                int id = entry.getKey();
                if (playerIds.contains(id)) continue;
                Entity entity = live.get(id);
                if (entity == null || !inRange(viewer, entity.x(), entity.z(), range(entity))) gone.add(id);
                else update(viewer, id, entity, entry.getValue());
            }
            if (!gone.isEmpty()) {
                int[] ids = new int[gone.size()];
                for (int i = 0; i < ids.length; i++) { ids[i] = gone.get(i); viewer.tracked.remove(ids[i]); }
                viewer.connection.send(new Clientbound.DestroyEntities(ids));
            }
            for (Map.Entry<Integer, Entity> entry : live.entrySet()) {
                int id = entry.getKey();
                Entity entity = entry.getValue();
                if (viewer.tracked.containsKey(id) || !inRange(viewer, entity.x(), entity.z(), range(entity))) continue;
                show(viewer, id, entity);
            }
        }
    }

    private static double range(Entity entity) { return entity instanceof EntityLiving ? MOB_RANGE : ITEM_RANGE; }

    /** The spawn packet (and what goes with it) for an item, arrow or mob coming into view. */
    private void show(ServerPlayer viewer, int id, Entity entity) {
        int x = Clientbound.fixed(entity.x()), y = Clientbound.fixed(entity.y()), z = Clientbound.fixed(entity.z());
        if (entity instanceof EntityItem item) {
            viewer.tracked.put(id, new ServerPlayer.Tracked(x, y, z, 0, 0));
            viewer.connection.send(new Clientbound.SpawnObject(id, Clientbound.SpawnObject.ITEM, x, y, z, 0, 0, 1,
                Clientbound.SpawnObject.velocity(item.motionX), Clientbound.SpawnObject.velocity(item.motionY),
                Clientbound.SpawnObject.velocity(item.motionZ)));
            viewer.connection.send(new Clientbound.EntityMetadata(id, itemMetadata(item.stack())));
        } else if (entity instanceof EntityArrow arrow) {
            int yaw = Clientbound.angle(arrow.rotationYaw), pitch = Clientbound.angle(arrow.rotationPitch);
            viewer.tracked.put(id, new ServerPlayer.Tracked(x, y, z, yaw, pitch));
            int shooter = arrow.shooter == null ? -1 : entities.idOf(arrow.shooter.uuid());
            viewer.connection.send(new Clientbound.SpawnObject(id, Clientbound.SpawnObject.ARROW, x, y, z, pitch, yaw,
                shooter < 0 ? 1 : shooter + 1, Clientbound.SpawnObject.velocity(arrow.motionX),
                Clientbound.SpawnObject.velocity(arrow.motionY), Clientbound.SpawnObject.velocity(arrow.motionZ)));
        } else if (entity instanceof EntityLiving mob) {
            int yaw = Clientbound.angle(mob.rotationYaw), pitch = Clientbound.angle(mob.rotationPitch);
            ServerPlayer.Tracked shown = new ServerPlayer.Tracked(x, y, z, yaw, pitch);
            shown.headYaw = Clientbound.angle(mob.rotationYawHead);
            shown.metadata = ServerMobs.metadata(mob);
            viewer.tracked.put(id, shown);
            viewer.connection.send(new Clientbound.SpawnMob(id, ServerMobs.mobType(mob), x, y, z, yaw, pitch, shown.headYaw,
                Clientbound.SpawnObject.velocity(mob.motionX), Clientbound.SpawnObject.velocity(mob.motionY),
                Clientbound.SpawnObject.velocity(mob.motionZ), shown.metadata));
            if (mob.heldItem != null) viewer.connection.send(new Clientbound.EntityEquipment(id, 0, codec.toSlot(mob.heldItem)));
            if (mob.health <= 0) viewer.connection.send(new Clientbound.EntityStatus(id, Clientbound.EntityStatus.DEAD));
        }
    }

    /** What changed about a shown item, arrow or mob since last tick. */
    private void update(ServerPlayer viewer, int id, Entity entity, ServerPlayer.Tracked shown) {
        if (entity instanceof EntityItem item) {
            sendMove(viewer, id, item.x(), item.y(), item.z(), 0, 0, false, shown, item.onGround);
        } else if (entity instanceof EntityArrow arrow) {
            sendMove(viewer, id, arrow.x(), arrow.y(), arrow.z(), arrow.rotationYaw, arrow.rotationPitch, true, shown, arrow.onGround);
        } else if (entity instanceof EntityLiving mob) {
            sendMove(viewer, id, mob.x(), mob.y(), mob.z(), mob.rotationYaw, mob.rotationPitch, true, shown, mob.onGround);
            int headYaw = Clientbound.angle(mob.rotationYawHead);
            if (headYaw != shown.headYaw) {
                shown.headYaw = headYaw;
                viewer.connection.send(new Clientbound.EntityHeadLook(id, headYaw));
            }
            byte[] metadata = ServerMobs.metadata(mob);
            if (!java.util.Arrays.equals(metadata, shown.metadata)) {
                shown.metadata = metadata;
                viewer.connection.send(new Clientbound.EntityMetadata(id, metadata));
            }
        }
    }

    /** Sends a packet to everyone currently showing this entity. */
    void sendToViewers(int id, Packet packet) {
        for (ServerPlayer viewer : players.values()) if (viewer.tracked.containsKey(id)) viewer.connection.send(packet);
    }

    private boolean inRange(ServerPlayer viewer, double x, double z, double range) {
        return Math.abs(x - viewer.x) <= range && Math.abs(z - viewer.z) <= range
            && viewer.loadedChunks.contains(World.chunkKey((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4));
    }

    // ---- other players ---------------------------------------------------------------------

    /** Shows each player every other living player within their view distance, and sends what moved since last tick. */
    private void trackPlayers() {
        for (ServerPlayer viewer : players.values()) {
            double range = viewDistance(viewer) * 16.0;
            for (ServerPlayer other : players.values()) {
                if (other == viewer) continue;
                // The dead stay a second for their fall-over animation.
                boolean visible = !(other.dead && tick - other.deathTick > 20) && inRange(viewer, other.x, other.z, range);
                ServerPlayer.Tracked shown = viewer.tracked.get(other.entityId);
                if (visible && shown == null) spawnFor(viewer, other);
                else if (!visible && shown != null) {
                    viewer.tracked.remove(other.entityId);
                    viewer.connection.send(new Clientbound.DestroyEntities(new int[]{other.entityId}));
                } else if (shown != null) {
                    if (sendMove(viewer, other.entityId, other.x, other.y, other.z, other.yaw, other.pitch, true, shown, other.onGround))
                        viewer.connection.send(new Clientbound.EntityHeadLook(other.entityId, Clientbound.angle(other.yaw)));
                }
            }
        }
    }

    private void spawnFor(ServerPlayer viewer, ServerPlayer other) {
        int x = Clientbound.fixed(other.x), y = Clientbound.fixed(other.y), z = Clientbound.fixed(other.z);
        int yaw = Clientbound.angle(other.yaw), pitch = Clientbound.angle(other.pitch);
        viewer.tracked.put(other.entityId, new ServerPlayer.Tracked(x, y, z, yaw, pitch));
        Slot held = codec.toSlot(other.heldStack());
        viewer.connection.send(new Clientbound.SpawnPlayer(other.entityId, other.uuid, x, y, z, yaw, pitch,
            held.isEmpty() ? 0 : held.itemId(), metadata(other)));
        viewer.connection.send(new Clientbound.EntityHeadLook(other.entityId, yaw));
        for (int slot = 0; slot < 5; slot++) {
            Slot item = other.shownEquipment[slot];
            if (!item.isEmpty()) viewer.connection.send(new Clientbound.EntityEquipment(other.entityId, slot, item));
        }
    }

    /**
     * Sends an entity's move since it was last shown: relative when it fits a
     * byte per axis, a teleport otherwise. Returns whether the look changed.
     */
    private boolean sendMove(ServerPlayer viewer, int id, double ex, double ey, double ez, float eyaw, float epitch,
                             boolean hasLook, ServerPlayer.Tracked shown, boolean onGround) {
        int x = Clientbound.fixed(ex), y = Clientbound.fixed(ey), z = Clientbound.fixed(ez);
        int yaw = hasLook ? Clientbound.angle(eyaw) : shown.yaw, pitch = hasLook ? Clientbound.angle(epitch) : shown.pitch;
        int dx = x - shown.x, dy = y - shown.y, dz = z - shown.z;
        boolean moved = dx != 0 || dy != 0 || dz != 0, turned = yaw != shown.yaw || pitch != shown.pitch;
        if (!moved && !turned) return false;
        if (Math.abs(dx) > 127 || Math.abs(dy) > 127 || Math.abs(dz) > 127)
            viewer.connection.send(new Clientbound.EntityTeleport(id, x, y, z, yaw, pitch, onGround));
        else if (moved && turned)
            viewer.connection.send(new Clientbound.EntityLookAndRelativeMove(id, dx, dy, dz, yaw, pitch, onGround));
        else if (moved)
            viewer.connection.send(new Clientbound.EntityRelativeMove(id, dx, dy, dz, onGround));
        else
            viewer.connection.send(new Clientbound.EntityLook(id, yaw, pitch, onGround));
        shown.x = x;
        shown.y = y;
        shown.z = z;
        shown.yaw = yaw;
        shown.pitch = pitch;
        return turned;
    }

    /** Index 0 flags (0x02 sneaking, 0x08 sprinting, 0x10 eating), 6 health, 10 the skin layers shown. */
    byte[] metadata(ServerPlayer player) {
        int flags = (player.sneaking ? 0x02 : 0) | (player.sprinting ? 0x08 : 0) | (player.useStartTick >= 0 ? 0x10 : 0);
        return new Metadata().putByte(0, flags).putFloat(6, Math.max(player.health, 0)).putByte(10, player.skinParts).build();
    }

    /** Sends a packet to everyone currently showing this player. */
    void sendToTrackers(ServerPlayer player, Packet packet) {
        for (ServerPlayer viewer : players.values())
            if (viewer.tracked.containsKey(player.entityId)) viewer.connection.send(packet);
    }

    // ---- keeping clients in step -----------------------------------------------------------

    /**
     * Sends the slots of the open window that changed since the client was last
     * told, and the cursor; {@code full} resends the whole window (after a
     * click, whose prediction may have gone wrong without the server's copy changing).
     */
    void syncInventory(ServerPlayer player, boolean full) {
        ServerWindow window = player.openWindow;
        if (player.syncedWindow != window || player.syncedSlots.length != window.size()) {
            player.syncedWindow = window;
            player.syncedSlots = new Slot[window.size()];
            full = true;
        }
        Slot[] now = new Slot[window.size()];
        for (int i = 0; i < now.length; i++) now[i] = codec.toSlot(window.get(i));
        if (full) {
            player.connection.send(new Clientbound.WindowItems(window.id, now));
            player.syncedSlots = now;
        } else {
            for (int i = 0; i < now.length; i++) {
                if (same(now[i], player.syncedSlots[i])) continue;
                player.syncedSlots[i] = now[i];
                player.connection.send(new Clientbound.SetSlot(window.id, i, now[i]));
            }
        }
        Slot cursor = codec.toSlot(player.inventory.getItemStack());
        if (full || !same(cursor, player.syncedCursor)) {
            player.syncedCursor = cursor;
            player.connection.send(new Clientbound.SetSlot(-1, -1, cursor));
        }
    }

    /** Held item and armor as other players see them. */
    private void syncEquipment(ServerPlayer player) {
        InventoryPlayer inventory = player.inventory;
        for (int slot = 0; slot < 5; slot++) {
            Slot now = codec.toSlot(slot == 0 ? inventory.getCurrentItem() : inventory.armorInventory[slot - 1]);
            if (same(now, player.shownEquipment[slot])) continue;
            player.shownEquipment[slot] = now;
            sendToTrackers(player, new Clientbound.EntityEquipment(player.entityId, slot, now));
        }
    }

    static boolean same(Slot a, Slot b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
        return a.itemId() == b.itemId() && a.count() == b.count() && a.damage() == b.damage() && java.util.Arrays.equals(a.nbt(), b.nbt());
    }

    // ---- messages --------------------------------------------------------------------------

    void broadcast(Packet packet) { for (ServerPlayer player : players.values()) player.connection.send(packet); }

    private void broadcastExcept(ServerPlayer except, Packet packet) {
        for (ServerPlayer player : players.values()) if (player != except) player.connection.send(packet);
    }

    static Clientbound.Chat chat(JsonObject component, int position) { return new Clientbound.Chat(component.toString(), position); }

    static JsonObject translate(String key, String color, String... with) {
        JsonObject component = new JsonObject();
        component.addProperty("translate", key);
        if (color != null) component.addProperty("color", color);
        JsonArray args = new JsonArray();
        for (String arg : with) args.add(arg);
        component.add("with", args);
        return component;
    }

    static JsonObject text(String text, String color) {
        JsonObject component = new JsonObject();
        component.addProperty("text", text);
        if (color != null) component.addProperty("color", color);
        return component;
    }

    Clientbound.PlayerAbilities abilities(ServerPlayer player) {
        int flags = switch (player.gameMode) {
            case 1 -> Clientbound.PlayerAbilities.INVULNERABLE | Clientbound.PlayerAbilities.ALLOW_FLYING | Clientbound.PlayerAbilities.CREATIVE;
            case 3 -> Clientbound.PlayerAbilities.INVULNERABLE | Clientbound.PlayerAbilities.ALLOW_FLYING | Clientbound.PlayerAbilities.FLYING;
            default -> 0;
        };
        return new Clientbound.PlayerAbilities(flags, 0.05f, 0.1f);
    }

    private static Clientbound.PlayerListItem.Entry listEntry(ServerPlayer player) {
        return new Clientbound.PlayerListItem.Entry(player.uuid, player.name, player.gameMode, 0);
    }

    private static long parseSeed(String text) {
        if (text.isBlank()) return new Random().nextLong();
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return text.hashCode();
        }
    }

    static void log(String message) { System.out.println("[Server] " + message); }
}
