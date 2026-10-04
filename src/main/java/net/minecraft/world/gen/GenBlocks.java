package net.minecraft.world.gen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;

/**
 * The blocks and block properties world generation works with, resolved once
 * per registry. Generation handles blocks as packed states
 * ({@code id << 4 | metadata}, the legacy chunk layout) and asks questions of
 * their vanilla Material through the tables here.
 *
 * <p>The material questions follow 1.8.9's Material classes (HYPOTHESIS,
 * recalled): "logic" materials (plants, vines, circuits, carpet, the snow
 * layer), liquids, fire and portals neither block movement nor count as
 * solid; air, liquids, vines, fire and snow layers can be replaced.
 */
public final class GenBlocks {
    private final BlockRegistry registry;
    private final boolean[] air, water, lava, leaves, vineMaterial, blocksMovement, solid, replaceable, fullBlock, fullCube;
    private final int[] opacity, emission;

    public final int airId, stoneId, grassId, dirtId, gravelId, sandId, sandstoneId, redSandstoneId, clayId,
        hardenedClayId, stainedClayId, myceliumId, snowLayerId, snowId, iceId, packedIceId, waterId, flowingWaterId,
        lavaId, flowingLavaId, logId, log2Id, saplingId, vineId, farmlandId, reedsId, cactusId, chestId, bedrockId,
        netherrackId, soulSandId, glowstoneId, netherBrickId;

    /** Packed states. */
    public final int AIR, STONE, GRASS, DIRT, COARSE_DIRT, PODZOL, GRAVEL, SAND, RED_SAND, SANDSTONE, RED_SANDSTONE,
        CLAY, HARDENED_CLAY, WHITE_CLAY, ORANGE_CLAY, YELLOW_CLAY, BROWN_CLAY, RED_CLAY, SILVER_CLAY, MYCELIUM,
        SNOW_LAYER, SNOW, ICE, PACKED_ICE, WATER, LAVA, FLOWING_LAVA, BEDROCK, COBBLESTONE, MOSSY_COBBLESTONE,
        MOB_SPAWNER, CHEST, WATERLILY, DEADBUSH, REEDS, CACTUS, PUMPKIN, MELON, EMERALD_ORE, SILVERFISH_STONE,
        SANDSTONE_SLAB, BROWN_MUSHROOM, RED_MUSHROOM, BROWN_MUSHROOM_BLOCK, RED_MUSHROOM_BLOCK, VINE, COCOA,
        TALLGRASS, YELLOW_FLOWER, RED_FLOWER, DOUBLE_PLANT, LOG, LOG2, LEAVES, LEAVES2,
        COAL_ORE, IRON_ORE, GOLD_ORE, REDSTONE_ORE, DIAMOND_ORE, LAPIS_ORE,
        NETHERRACK, SOUL_SAND, GLOWSTONE, QUARTZ_ORE, FIRE, NETHER_BRICK, NETHER_BRICK_FENCE, NETHER_WART;

    public GenBlocks(BlockRegistry registry) {
        this.registry = registry;
        int size = registry.size();
        air = new boolean[size]; water = new boolean[size]; lava = new boolean[size]; leaves = new boolean[size]; vineMaterial = new boolean[size];
        blocksMovement = new boolean[size]; solid = new boolean[size]; replaceable = new boolean[size];
        fullBlock = new boolean[size]; fullCube = new boolean[size]; opacity = new int[size]; emission = new int[size];
        for (Block block : registry.values()) {
            int id = block.registryId();
            String material = block.material() == null ? "rock" : block.material();
            String name = block.name().path();
            air[id] = material.equals("air");
            water[id] = material.equals("water");
            lava[id] = material.equals("lava");
            leaves[id] = material.equals("leaves");
            vineMaterial[id] = material.equals("vine");
            boolean snowLayer = name.equals("snow_layer");
            boolean logic = switch (material) {
                case "plants", "vine", "circuits", "carpet" -> true;
                default -> snowLayer;
            };
            boolean passable = logic || air[id] || water[id] || lava[id]
                || material.equals("fire") || material.equals("portal");
            blocksMovement[id] = !passable && !material.equals("web");
            solid[id] = !passable;
            replaceable[id] = air[id] || water[id] || lava[id] || material.equals("vine") || material.equals("fire") || snowLayer;
            fullBlock[id] = block.isOpaque();
            fullCube[id] = block.isFullCube();
            opacity[id] = block.lightOpacity();
            emission[id] = block.lightLevel();
        }
        airId = id("air"); stoneId = id("stone"); grassId = id("grass"); dirtId = id("dirt"); gravelId = id("gravel");
        sandId = id("sand"); sandstoneId = id("sandstone"); redSandstoneId = id("red_sandstone"); clayId = id("clay");
        hardenedClayId = id("hardened_clay"); stainedClayId = id("stained_hardened_clay"); myceliumId = id("mycelium");
        snowLayerId = id("snow_layer"); snowId = id("snow"); iceId = id("ice"); packedIceId = id("packed_ice");
        waterId = id("water"); flowingWaterId = id("flowing_water"); lavaId = id("lava"); flowingLavaId = id("flowing_lava");
        logId = id("log"); log2Id = id("log2"); saplingId = id("sapling"); vineId = id("vine"); farmlandId = id("farmland");
        reedsId = id("reeds"); cactusId = id("cactus"); chestId = id("chest"); bedrockId = id("bedrock");
        netherrackId = id("netherrack"); soulSandId = id("soul_sand"); glowstoneId = id("glowstone");
        netherBrickId = id("nether_brick");

        AIR = 0; STONE = state("stone", 0); GRASS = state("grass", 0); DIRT = state("dirt", 0);
        COARSE_DIRT = state("dirt", 1); PODZOL = state("dirt", 2); GRAVEL = state("gravel", 0);
        SAND = state("sand", 0); RED_SAND = state("sand", 1); SANDSTONE = state("sandstone", 0);
        RED_SANDSTONE = state("red_sandstone", 0); CLAY = state("clay", 0); HARDENED_CLAY = state("hardened_clay", 0);
        WHITE_CLAY = state("stained_hardened_clay", 0); ORANGE_CLAY = state("stained_hardened_clay", 1);
        YELLOW_CLAY = state("stained_hardened_clay", 4); BROWN_CLAY = state("stained_hardened_clay", 12);
        RED_CLAY = state("stained_hardened_clay", 14); SILVER_CLAY = state("stained_hardened_clay", 8);
        MYCELIUM = state("mycelium", 0); SNOW_LAYER = state("snow_layer", 0); SNOW = state("snow", 0);
        ICE = state("ice", 0); PACKED_ICE = state("packed_ice", 0); WATER = state("water", 0); LAVA = state("lava", 0);
        FLOWING_LAVA = state("flowing_lava", 0); BEDROCK = state("bedrock", 0); COBBLESTONE = state("cobblestone", 0);
        MOSSY_COBBLESTONE = state("mossy_cobblestone", 0); MOB_SPAWNER = state("mob_spawner", 0);
        CHEST = state("chest", 2); WATERLILY = state("waterlily", 0); DEADBUSH = state("deadbush", 0);
        REEDS = state("reeds", 0); CACTUS = state("cactus", 0); PUMPKIN = state("pumpkin", 0);
        MELON = state("melon_block", 0); EMERALD_ORE = state("emerald_ore", 0); SILVERFISH_STONE = state("monster_egg", 0);
        SANDSTONE_SLAB = state("stone_slab", 1); BROWN_MUSHROOM = state("brown_mushroom", 0);
        RED_MUSHROOM = state("red_mushroom", 0); BROWN_MUSHROOM_BLOCK = state("brown_mushroom_block", 0);
        RED_MUSHROOM_BLOCK = state("red_mushroom_block", 0); VINE = state("vine", 0); COCOA = state("cocoa", 0);
        TALLGRASS = state("tallgrass", 0); YELLOW_FLOWER = state("yellow_flower", 0); RED_FLOWER = state("red_flower", 0);
        DOUBLE_PLANT = state("double_plant", 0); LOG = state("log", 0); LOG2 = state("log2", 0);
        LEAVES = state("leaves", 0); LEAVES2 = state("leaves2", 0);
        COAL_ORE = state("coal_ore", 0); IRON_ORE = state("iron_ore", 0); GOLD_ORE = state("gold_ore", 0);
        REDSTONE_ORE = state("redstone_ore", 0); DIAMOND_ORE = state("diamond_ore", 0); LAPIS_ORE = state("lapis_ore", 0);
        NETHERRACK = state("netherrack", 0); SOUL_SAND = state("soul_sand", 0); GLOWSTONE = state("glowstone", 0);
        QUARTZ_ORE = state("quartz_ore", 0); FIRE = state("fire", 0); NETHER_BRICK = state("nether_brick", 0);
        NETHER_BRICK_FENCE = state("nether_brick_fence", 0); NETHER_WART = state("nether_wart", 0);
    }

    public BlockRegistry registry() { return registry; }

    private int id(String name) {
        Block block = registry.get("minecraft:" + name);
        if (block == null) throw new IllegalStateException("World generation needs minecraft:" + name);
        return block.registryId();
    }

    /** A packed state by block name and metadata. */
    public int state(String name, int metadata) { return id(name) << 4 | metadata; }

    public static int id(int packed) { return packed >>> 4; }
    public static int meta(int packed) { return packed & 15; }
    /** The same block with other metadata. */
    public static int withMeta(int packed, int metadata) { return packed & ~15 | metadata; }

    public boolean isAir(int packed) { return air[packed >>> 4]; }
    /** Material.water (still or flowing). */
    public boolean isWater(int packed) { return water[packed >>> 4]; }
    public boolean isLava(int packed) { return lava[packed >>> 4]; }
    public boolean isLiquid(int packed) { return water[packed >>> 4] || lava[packed >>> 4]; }
    public boolean isLeaves(int packed) { return leaves[packed >>> 4]; }
    public boolean blocksMovement(int packed) { return blocksMovement[packed >>> 4]; }
    /** Material.isSolid(). */
    public boolean isSolid(int packed) { return solid[packed >>> 4]; }
    public boolean isReplaceable(int packed) { return replaceable[packed >>> 4]; }
    /** Block.isFullBlock(): the opaque full cubes. */
    public boolean isFullBlock(int packed) { return fullBlock[packed >>> 4]; }
    /** Block.isFullCube(): a whole-block shape, opaque or not (leaves count). */
    public boolean isFullCube(int packed) { return fullCube[packed >>> 4]; }
    public int opacity(int packed) { return opacity[packed >>> 4]; }
    public int emission(int packed) { return emission[packed >>> 4]; }
    /** Material.vine: vines, tall grass, dead bushes and double plants. */
    public boolean isVineMaterial(int packed) { return vineMaterial[packed >>> 4]; }

    /** Trees may grow into (and replace) these: WorldGenAbstractTree.func_150523_a. */
    public boolean canTreeGrowInto(int packed) {
        int id = packed >>> 4;
        return air[id] || leaves[id] || id == grassId || id == dirtId || id == logId || id == log2Id
            || id == saplingId || id == vineId;
    }
}
