package net.minecraft.world.storage.anvil;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTag;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Converting a vanilla Anvil world folder into LegacyCraft chunks in place. */
final class AnvilConversionTest {
    @TempDir Path world;

    @Test
    void convertsRegionChunksSoTheWorldOpens() throws Exception {
        BlockRegistry blocks = GameData.blocks();
        int brick = blocks.get("minecraft:brick_block").registryId();
        // Chunk (-1, 2): a brick at local (3, 70, 5) with light 9/4, an unknown block ID next to it, a chest
        // (dropped), a spawner, a mob head, a lit furnace and a zombie.
        NbtTag.Compound section = section(4);
        set(section, 3, 6, 5, brick, 0);
        set(section, 4, 6, 5, 300, 0);
        set(section, 3, 0, 8, blocks.get("minecraft:lit_furnace").registryId(), 2); // (-13, 64, 40)
        nibble(section.getByteArray("SkyLight"), index(3, 6, 5), 9);
        nibble(section.getByteArray("BlockLight"), index(3, 6, 5), 4);
        NbtTag.Compound zombie = new NbtTag.Compound();
        zombie.put("id", new NbtTag.Str("Zombie"));
        NbtTag.Compound overworldChunk = chunk(-1, 2, section, true, List.of(
            blockEntity("Chest", -10, 70, 37),
            with(blockEntity("MobSpawner", -11, 40, 38), "EntityId", new NbtTag.Str("Skeleton")),
            with(with(blockEntity("Skull", -12, 71, 39), "SkullType", new NbtTag.Byte((byte) 4)), "Rot", new NbtTag.Byte((byte) 6)),
            with(with(with(blockEntity("Furnace", -13, 64, 40), "BurnTime", new NbtTag.Short((short) 900)),
                "CookTime", new NbtTag.Short((short) 50)),
                "Items", new NbtTag.NbtList(NbtTag.COMPOUND, List.of(item(0, "minecraft:iron_ore", 7, 0), item(1, "minecraft:coal", 3, 0))))),
            List.of(zombie));
        writeRegion(world.resolve("region").resolve("r.-1.0.mca"), -1, 2, overworldChunk);
        // Nether chunk (0, 0): netherrack at y = 10.
        NbtTag.Compound nether = section(0);
        set(nether, 0, 10, 0, blocks.get("minecraft:netherrack").registryId(), 0);
        writeRegion(world.resolve("DIM-1").resolve("region").resolve("r.0.0.mca"), 0, 0, chunk(0, 0, nether, false, List.of(), List.of()));

        var items = GameData.items();
        WorldStorage.ItemNames names = (id, data) -> {
            var item = items.resolve(id, data);
            return item == null ? null : item.name().toString();
        };
        WorldStorage.Conversion result = WorldStorage.convertAnvil(world, 1234L, blocks.size(), names, progress -> {});
        assertEquals(2, result.chunks());
        assertEquals(1, result.unknownBlocks());
        assertEquals(3, result.blockEntities());
        assertEquals(1, result.droppedBlockEntities(), "the chest");
        assertEquals(1, result.entities());
        assertEquals(0, result.damagedChunks());
        assertTrue(WorldStorage.exists(world));
        assertTrue(WorldStorage.exists(world.resolve("DIM-1")));

        var recipes = net.minecraft.item.crafting.FurnaceRecipes.load(GameData.resources(), items);
        java.util.function.Function<String, net.minecraft.item.Item> byName =
            name -> items.get(new net.minecraft.util.ResourceLocation("minecraft", name));
        World loaded = WorldStorage.load(world, blocks, seed -> {
            World created = new World(blocks, GameData.biomes(), seed);
            created.setSmelting(recipes, byName);
            return created;
        });
        assertEquals(1234L, loaded.seed());
        int x = -16 + 3, z = 32 + 5;
        assertEquals(blocks.get("minecraft:brick_block"), loaded.get(x, 70, z));
        assertEquals(blocks.air(), loaded.get(x + 1, 70, z), "unknown IDs become air");
        var chunk = loaded.getChunk(-1, 2);
        assertTrue(chunk.isPopulated());
        assertEquals(9, chunk.skyLight(3, 70, 5));
        assertEquals(4, chunk.blockLight(3, 70, 5));
        assertEquals(15, chunk.skyLight(3, 200, 5), "sky above the top section is open");
        assertEquals("Skeleton", loaded.spawners().get(new World.BlockPos(-11, 40, 38)));
        assertEquals(new World.Skull(4, 6), loaded.skull(-12, 71, 39));
        var furnace = loaded.furnaceAt(-13, 64, 40);
        assertNotNull(furnace);
        assertEquals(byName.apply("iron_ore"), furnace.get(net.minecraft.inventory.Furnace.INPUT).item());
        assertEquals(7, furnace.get(net.minecraft.inventory.Furnace.INPUT).count());
        assertEquals(3, furnace.get(net.minecraft.inventory.Furnace.FUEL).count());
        assertEquals(900, furnace.burnTime());
        assertEquals(50, furnace.cookTime());
        NbtTag.Compound entities = NbtIo.readCompressed(world.resolve("entities.dat"));
        assertEquals("Zombie", entities.getList("Entities").compound(0).getString("id"));
    }

    @Test
    void refusesWorldsSavedWithTheBlockPalette() throws Exception {
        NbtTag.Compound section = new NbtTag.Compound();
        section.put("Y", new NbtTag.Byte((byte) 0));
        section.put("BlockStates", new NbtTag.ByteArray(new byte[8]));
        writeRegion(world.resolve("region").resolve("r.0.0.mca"), 0, 0, chunk(0, 0, section, true, List.of(), List.of()));
        IOException error = assertThrows(IOException.class, () -> WorldStorage.convertAnvil(world, 1L, 10, null, p -> {}));
        assertTrue(error.getMessage().contains("1.13"), error.getMessage());
        assertFalse(WorldStorage.exists(world), "a failed conversion leaves the world unconverted");
    }

    private static NbtTag.Compound section(int y) {
        NbtTag.Compound section = new NbtTag.Compound();
        section.put("Y", new NbtTag.Byte((byte) y));
        section.put("Blocks", new NbtTag.ByteArray(new byte[4096]));
        section.put("Add", new NbtTag.ByteArray(new byte[2048]));
        section.put("Data", new NbtTag.ByteArray(new byte[2048]));
        section.put("SkyLight", new NbtTag.ByteArray(new byte[2048]));
        section.put("BlockLight", new NbtTag.ByteArray(new byte[2048]));
        return section;
    }

    private static int index(int x, int y, int z) { return y << 8 | z << 4 | x; }

    private static void set(NbtTag.Compound section, int x, int y, int z, int id, int metadata) {
        int index = index(x, y, z);
        section.getByteArray("Blocks")[index] = (byte) id;
        nibble(section.getByteArray("Add"), index, id >> 8);
        nibble(section.getByteArray("Data"), index, metadata);
    }

    private static void nibble(byte[] array, int index, int value) {
        int shift = index % 2 == 0 ? 0 : 4;
        array[index / 2] = (byte) (array[index / 2] & ~(15 << shift) | (value & 15) << shift);
    }

    private static NbtTag.Compound blockEntity(String id, int x, int y, int z) {
        NbtTag.Compound tag = new NbtTag.Compound();
        tag.put("id", new NbtTag.Str(id));
        tag.put("x", new NbtTag.Int(x));
        tag.put("y", new NbtTag.Int(y));
        tag.put("z", new NbtTag.Int(z));
        return tag;
    }

    private static NbtTag.Compound with(NbtTag.Compound tag, String name, NbtTag value) {
        tag.put(name, value);
        return tag;
    }

    private static NbtTag.Compound item(int slot, String id, int count, int damage) {
        NbtTag.Compound item = new NbtTag.Compound();
        item.put("Slot", new NbtTag.Byte((byte) slot));
        item.put("id", new NbtTag.Str(id));
        item.put("Count", new NbtTag.Byte((byte) count));
        item.put("Damage", new NbtTag.Short((short) damage));
        return item;
    }

    private static NbtTag.Compound chunk(int x, int z, NbtTag.Compound section, boolean populated,
                                         List<NbtTag> tileEntities, List<NbtTag> entities) {
        NbtTag.Compound level = new NbtTag.Compound();
        level.put("xPos", new NbtTag.Int(x));
        level.put("zPos", new NbtTag.Int(z));
        level.put("TerrainPopulated", new NbtTag.Byte((byte) (populated ? 1 : 0)));
        level.put("Sections", new NbtTag.NbtList(NbtTag.COMPOUND, List.of(section)));
        level.put("TileEntities", new NbtTag.NbtList(NbtTag.COMPOUND, tileEntities));
        level.put("Entities", new NbtTag.NbtList(NbtTag.COMPOUND, entities));
        NbtTag.Compound root = new NbtTag.Compound();
        root.put("Level", level);
        return root;
    }

    /** A region file holding one chunk, zlib-compressed, in sector 2. */
    private static void writeRegion(Path file, int chunkX, int chunkZ, NbtTag.Compound root) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (var out = new DeflaterOutputStream(compressed)) { NbtIo.write(root, out); }
        byte[] payload = compressed.toByteArray();
        int sectors = (payload.length + 5 + 4095) / 4096;
        ByteBuffer region = ByteBuffer.allocate(8192 + sectors * 4096);
        int slot = (Math.floorMod(chunkX, 32) + Math.floorMod(chunkZ, 32) * 32) * 4;
        region.putInt(slot, 2 << 8 | sectors);
        region.position(8192);
        region.putInt(payload.length + 1).put((byte) 2).put(payload);
        Files.createDirectories(file.getParent());
        Files.write(file, region.array());
    }
}
