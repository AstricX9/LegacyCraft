package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeSource;
import net.minecraft.world.chunk.Chunk;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Infinite horizontal world backed by lazily generated 16x16x16 chunk sections. */
public final class World {
    public static final int HEIGHT = Chunk.HEIGHT;
    public static final int SEA_LEVEL = 63;
    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final BlockRegistry registry;
    private final long seed;
    private long nextGenerationIndex;
    private final BiomeSource biomes;

    private final Block air, grass, dirt, stone, bedrock, sand, water;
    private final Block oakLog, oakLeaves, coalOre, ironOre, goldOre, diamondOre;

    public World(BlockRegistry registry, long seed) {
        this.registry = registry;
        this.seed = seed;
        air = registry.air();
        grass = registry.get("minecraft:grass"); dirt = registry.get("minecraft:dirt");
        stone = registry.get("minecraft:stone"); bedrock = registry.get("minecraft:bedrock");
        sand = registry.get("minecraft:sand"); water = registry.get("minecraft:water");
        oakLog = registry.get("minecraft:oak_log"); oakLeaves = registry.get("minecraft:oak_leaves");
        coalOre = registry.get("minecraft:coal_ore"); ironOre = registry.get("minecraft:iron_ore");
        goldOre = registry.get("minecraft:gold_ore"); diamondOre = registry.get("minecraft:diamond_ore");
        biomes = new BiomeSource(registry, seed);
    }

    public BlockRegistry registry() { return registry; }
    public boolean inBounds(int x, int y, int z) { return y >= 0 && y < HEIGHT; }

    public Block get(int x, int y, int z) {
        if (y < 0) return bedrock;
        if (y >= HEIGHT) return air;
        Chunk chunk = getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        return registry.byId(unpackBlockId(chunk.get(floorMod(x, Chunk.SIZE), y, floorMod(z, Chunk.SIZE))));
    }

    /**
     * Full (block, metadata) state at a position — see {@link BlockState}.
     * Out-of-world positions report the same synthetic bedrock/air {@link
     * #get} does, always at metadata 0.
     */
    public BlockState getState(int x, int y, int z) {
        if (y < 0) return BlockState.defaultOf(bedrock);
        if (y >= HEIGHT) return BlockState.defaultOf(air);
        Chunk chunk = getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        return BlockState.unpack(registry, chunk.get(floorMod(x, Chunk.SIZE), y, floorMod(z, Chunk.SIZE)));
    }

    /** Convenience for callers that don't care about metadata; sets metadata 0. */
    public void set(int x, int y, int z, Block block) { setState(x, y, z, BlockState.defaultOf(block)); }

    public void setState(int x, int y, int z, BlockState state) {
        if (!inBounds(x, y, z)) return;
        Chunk chunk = getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        chunk.set(floorMod(x, Chunk.SIZE), y, floorMod(z, Chunk.SIZE), state.packed());
    }

    public Chunk getChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        Chunk chunk = chunks.computeIfAbsent(key, ignored -> new Chunk(chunkX, chunkZ));
        if (!chunk.isGenerated()) generate(chunk);
        return chunk;
    }

    public Collection<Chunk> loadedChunks() { return chunks.values(); }
    public int loadedChunkCount() { return chunks.size(); }
    public long seed() { return seed; }
    public Biome biomeAt(int x, int z) { return biomes.sample(x, z); }

    /** Installs a persisted chunk and advances the generation sequence past it. */
    public void restoreChunk(Chunk chunk) {
        if (!chunk.isGenerated()) throw new IllegalArgumentException("Restored chunk is not generated");
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        if (chunks.putIfAbsent(key, chunk) != null)
            throw new IllegalStateException("Chunk is already loaded: " + chunk.chunkX() + "," + chunk.chunkZ());
        nextGenerationIndex = Math.max(nextGenerationIndex, chunk.generationIndex() + 1);
    }

    public int surfaceHeight(int x, int z) {
        Chunk chunk = getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        int localX = floorMod(x, Chunk.SIZE), localZ = floorMod(z, Chunk.SIZE);
        for (int y = HEIGHT - 1; y >= 0; y--) {
            Block block = registry.byId(unpackBlockId(chunk.get(localX, y, localZ)));
            if (block.isSolid() && block != oakLeaves && block != oakLog) return y;
        }
        return 0;
    }

    private void generate(Chunk chunk) {
        // Mark before feature work so a future cross-chunk decorator cannot recurse indefinitely.
        chunk.markGenerated(nextGenerationIndex++);
        int baseX = chunk.chunkX() * Chunk.SIZE;
        int baseZ = chunk.chunkZ() * Chunk.SIZE;
        Random random = new Random(mixSeed(chunk.chunkX(), chunk.chunkZ()));
        int[][] heights = new int[Chunk.SIZE][Chunk.SIZE];

        for (int z = 0; z < Chunk.SIZE; z++) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                int worldX = baseX + x, worldZ = baseZ + z;
                Biome biome = biomes.sample(worldX, worldZ);
                double continental = valueNoise(worldX * .018, worldZ * .018, 0) * 16;
                double hills = valueNoise(worldX * .055, worldZ * .055, 91) * 7;
                int top = clamp((int)Math.round(biome.baseHeight() + continental
                    + hills * biome.heightVariation() / 7.0), 20, 120);
                double spawnDistance = Math.hypot(worldX, worldZ);
                if (spawnDistance < 12) top = Math.max(top, SEA_LEVEL + 5);
                heights[x][z] = top;
                boolean beach = top <= SEA_LEVEL + 1;
                Block topBlock = beach ? sand : biome.topBlock();
                Block fillerBlock = beach ? sand : biome.fillerBlock();
                put(chunk, x, 0, z, bedrock);
                if (random.nextInt(4) == 0) put(chunk, x, 1, z, bedrock);
                for (int y = 1; y <= top; y++) {
                    Block block;
                    if (y == top) block = topBlock;
                    else if (y >= top - (beach ? 3 : 2)) block = fillerBlock;
                    else block = oreAt(random,y);
                    put(chunk, x, y, z, block);
                }
                for (int y = top + 1; y < SEA_LEVEL; y++) put(chunk, x, y, z, water);
            }
        }

        // Trees remain inside their owning chunk; a later population phase will coordinate borders.
        for (int z = 3; z < Chunk.SIZE - 3; z++) {
            for (int x = 3; x < Chunk.SIZE - 3; x++) {
                int worldX = baseX + x, worldZ = baseZ + z;
                Biome biome = biomes.sample(worldX, worldZ);
                if (Math.hypot(worldX, worldZ) < 10 || biome.treeChance() == 0
                    || random.nextInt(biome.treeChance()) != 0) continue;
                int y = heights[x][z];
                if (registry.byId(unpackBlockId(chunk.get(x,y,z))) == grass) growOak(chunk,x,y+1,z,4+random.nextInt(3));
            }
        }
    }

    private Block oreAt(Random random, int y) {
        int roll = random.nextInt(1000);
        if (y < 16 && roll < 5) return diamondOre;
        if (y < 32 && roll < 9) return goldOre;
        if (y < 64 && roll < 24) return ironOre;
        if (y < 128 && roll < 48) return coalOre;
        return stone;
    }

    private void growOak(Chunk chunk, int x, int y, int z, int height) {
        for (int i=0;i<height;i++) put(chunk, x, y+i, z, oakLog);
        int crown=y+height-2;
        for(int dy=0;dy<=3;dy++){
            int radius=dy>=2?1:2;
            for(int dz=-radius;dz<=radius;dz++) for(int dx=-radius;dx<=radius;dx++){
                if(Math.abs(dx)==radius&&Math.abs(dz)==radius&&((x+z+dx+dz+dy)&1)==0)continue;
                if(chunk.get(x+dx,crown+dy,z+dz)==packed(air))
                    put(chunk, x+dx, crown+dy, z+dz, oakLeaves);
            }
        }
    }

    private double valueNoise(double x,double z,int salt){int xi=(int)Math.floor(x),zi=(int)Math.floor(z);double fx=smooth(x-xi),fz=smooth(z-zi);double a=hash(xi,zi,salt),b=hash(xi+1,zi,salt),c=hash(xi,zi+1,salt),d=hash(xi+1,zi+1,salt);return lerp(lerp(a,b,fx),lerp(c,d,fx),fz);}
    private double hash(int x,int z,int salt){long n=x*341873128712L+z*132897987541L+seed+salt*31L;n=(n^(n>>>13))*1274126177L;return((n^(n>>>16))&0xffff)/32767.5-1;}
    private long mixSeed(int x,int z){long n=seed^x*341873128712L^z*132897987541L;return n^(n>>>29);}
    private static double smooth(double t){return t*t*(3-2*t);} private static double lerp(double a,double b,double t){return a+(b-a)*t;}
    private static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
    public static int floorDiv(int value,int divisor){return Math.floorDiv(value,divisor);} public static int floorMod(int value,int divisor){return Math.floorMod(value,divisor);}
    public static long chunkKey(int x,int z){return((long)x<<32)^(z&0xffffffffL);}

    /** Legacy extended-storage packing shared with {@link BlockState#packed()}, for internal direct-{@link Chunk} writes during generation. */
    private static int packed(Block block) { return block.registryId() << BlockState.METADATA_BITS; }
    private static void put(Chunk chunk, int x, int y, int z, Block block) { chunk.set(x, y, z, packed(block)); }
    private static int unpackBlockId(int packedState) { return packedState >>> BlockState.METADATA_BITS; }
}
