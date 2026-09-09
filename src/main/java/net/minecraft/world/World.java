package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
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
    }

    public BlockRegistry registry() { return registry; }
    public boolean inBounds(int x, int y, int z) { return y >= 0 && y < HEIGHT; }

    public Block get(int x, int y, int z) {
        if (y < 0) return bedrock;
        if (y >= HEIGHT) return air;
        Chunk chunk = getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        return registry.byId(chunk.get(floorMod(x, Chunk.SIZE), y, floorMod(z, Chunk.SIZE)));
    }

    public void set(int x, int y, int z, Block block) {
        if (!inBounds(x,y,z)) return;
        Chunk chunk = getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        chunk.set(floorMod(x, Chunk.SIZE), y, floorMod(z, Chunk.SIZE), block.registryId());
    }

    public Chunk getChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        Chunk chunk = chunks.computeIfAbsent(key, ignored -> new Chunk(chunkX, chunkZ));
        if (!chunk.isGenerated()) generate(chunk);
        return chunk;
    }

    public Collection<Chunk> loadedChunks() { return chunks.values(); }
    public int loadedChunkCount() { return chunks.size(); }

    public int surfaceHeight(int x, int z) {
        Chunk chunk = getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        int localX = floorMod(x, Chunk.SIZE), localZ = floorMod(z, Chunk.SIZE);
        for (int y = HEIGHT - 1; y >= 0; y--) {
            Block block = registry.byId(chunk.get(localX,y,localZ));
            if (block.isSolid() && block != oakLeaves && block != oakLog) return y;
        }
        return 0;
    }

    private void generate(Chunk chunk) {
        // Mark before feature work so a future cross-chunk decorator cannot recurse indefinitely.
        chunk.markGenerated();
        int baseX = chunk.chunkX() * Chunk.SIZE;
        int baseZ = chunk.chunkZ() * Chunk.SIZE;
        Random random = new Random(mixSeed(chunk.chunkX(), chunk.chunkZ()));
        int[][] heights = new int[Chunk.SIZE][Chunk.SIZE];

        for (int z = 0; z < Chunk.SIZE; z++) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                int worldX = baseX + x, worldZ = baseZ + z;
                double continental = valueNoise(worldX * .018, worldZ * .018, 0) * 16;
                double hills = valueNoise(worldX * .055, worldZ * .055, 91) * 7;
                int top = clamp((int)Math.round(65 + continental + hills), 20, 120);
                double spawnDistance = Math.hypot(worldX, worldZ);
                if (spawnDistance < 12) top = Math.max(top, SEA_LEVEL + 5);
                heights[x][z] = top;
                boolean beach = top <= SEA_LEVEL + 1;
                chunk.set(x, 0, z, bedrock.registryId());
                if (random.nextInt(4) == 0) chunk.set(x, 1, z, bedrock.registryId());
                for (int y = 1; y <= top; y++) {
                    Block block;
                    if (y == top) block = beach ? sand : grass;
                    else if (y >= top - (beach ? 3 : 2)) block = beach ? sand : dirt;
                    else block = oreAt(random,y);
                    chunk.set(x,y,z,block.registryId());
                }
                for (int y = top + 1; y < SEA_LEVEL; y++) chunk.set(x,y,z,water.registryId());
            }
        }

        // Trees remain inside their owning chunk; a later population phase will coordinate borders.
        for (int z = 3; z < Chunk.SIZE - 3; z++) {
            for (int x = 3; x < Chunk.SIZE - 3; x++) {
                int worldX = baseX + x, worldZ = baseZ + z;
                if (Math.hypot(worldX, worldZ) < 10 || random.nextInt(110) != 0) continue;
                int y = heights[x][z];
                if (registry.byId(chunk.get(x,y,z)) == grass) growOak(chunk,x,y+1,z,4+random.nextInt(3));
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
        for (int i=0;i<height;i++) chunk.set(x,y+i,z,oakLog.registryId());
        int crown=y+height-2;
        for(int dy=0;dy<=3;dy++){
            int radius=dy>=2?1:2;
            for(int dz=-radius;dz<=radius;dz++) for(int dx=-radius;dx<=radius;dx++){
                if(Math.abs(dx)==radius&&Math.abs(dz)==radius&&((x+z+dx+dz+dy)&1)==0)continue;
                if(chunk.get(x+dx,crown+dy,z+dz)==air.registryId())
                    chunk.set(x+dx,crown+dy,z+dz,oakLeaves.registryId());
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
}
