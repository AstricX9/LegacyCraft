package net.minecraft.client.particle;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.state.ActualState;
import net.minecraft.client.gui.GuiTextures;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LightMap;
import net.minecraft.client.renderer.block.model.BakedModel;
import net.minecraft.client.renderer.block.model.BlockModelManager;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * Keeps, ticks and draws particles (1.8.9 EffectRenderer, HYPOTHESIS:
 * recalled rules): at most 4000 per texture, the oldest dropped first.
 * Breaking and mining effects always appear; everything else goes through
 * {@link #spawn}, which honours the Particles option (Decreased drops a
 * third, Minimal drops all) and skips anything more than 16 blocks from the
 * camera. Each tick also runs 1000 "random display ticks" on blocks within
 * 16 blocks of the player, which is where torch flames and smoke, furnace
 * fire, lava pops, drips, water motes and mycelium spores come from.
 */
public final class ParticleManager {
    public static final ResourceLocation PARTICLES = new ResourceLocation("minecraft:textures/particle/particles.png");
    private static final int MAX_PER_LAYER = 4000;

    public enum Kind { SMOKE, LARGE_SMOKE, FLAME, LAVA, DRIP_WATER, DRIP_LAVA, SUSPENDED, TOWN_AURA, REDDUST, BLOCK_CRACK, BUBBLE, SPLASH, PORTAL, ENCHANTMENT_TABLE }

    private final World world;
    private final TextureAtlas atlas;
    private final GuiTextures textures;
    private final BlockModelManager models;
    private final BiomeColors biomeColors;
    private final Random random = new Random();

    /** Where block display ticks send their ambient sounds (fire crackle, flowing water, lava pops, portals). */
    public interface Sounds { void play(String event, double x, double y, double z, float volume, float pitch); }
    public Sounds sounds = (event, x, y, z, volume, pitch) -> {};
    @SuppressWarnings("unchecked")
    private final ArrayDeque<Particle>[] layers = new ArrayDeque[]{new ArrayDeque<>(), new ArrayDeque<>()};
    private final List<Particle> added = new ArrayList<>();
    /** Particle option (0 all, 1 decreased, 2 minimal) and camera position, updated before each tick. */
    private int setting;
    private double cameraX, cameraY, cameraZ;

    public ParticleManager(World world, TextureAtlas atlas, GuiTextures textures, BlockModelManager models, BiomeColors biomeColors) {
        this.world = world;
        this.atlas = atlas;
        this.textures = textures;
        this.models = models;
        this.biomeColors = biomeColors;
    }

    public void setView(int particleSetting, double x, double y, double z) {
        setting = particleSetting;
        cameraX = x; cameraY = y; cameraZ = z;
    }

    public int count() { return layers[0].size() + layers[1].size(); }

    public void add(Particle particle) { added.add(particle); }

    private void admit(Particle particle) {
        ArrayDeque<Particle> layer = layers[particle.layer()];
        if (layer.size() >= MAX_PER_LAYER) layer.pollFirst();
        layer.addLast(particle);
    }

    /** A particle from the world (World.spawnParticle): subject to the Particles option and a 16-block range. */
    public void spawn(Kind kind, double x, double y, double z, double vx, double vy, double vz, BlockState state) {
        int level = setting;
        if (level == 1 && random.nextInt(3) == 0) level = 2;
        double dx = cameraX - x, dy = cameraY - y, dz = cameraZ - z;
        if (dx * dx + dy * dy + dz * dz > 256.0 || level > 1) return;
        Particle particle = switch (kind) {
            case SMOKE -> new Particles.Smoke(world, random, x, y, z, vx, vy, vz, 1f);
            case LARGE_SMOKE -> new Particles.Smoke(world, random, x, y, z, vx, vy, vz, 2.5f);
            case FLAME -> new Particles.Flame(world, random, x, y, z, vx, vy, vz);
            case LAVA -> new Particles.Lava(world, random, x, y, z, this);
            case DRIP_WATER -> new Particles.Drip(world, random, x, y, z, false, this);
            case DRIP_LAVA -> new Particles.Drip(world, random, x, y, z, true, this);
            case SUSPENDED -> new Particles.Suspended(world, random, x, y, z);
            case TOWN_AURA -> new Particles.Aura(world, random, x, y, z);
            case REDDUST -> new Particles.Reddust(world, random, x, y, z);
            case BLOCK_CRACK -> digging(state, x, y, z, vx, vy, vz, floor(x), floor(y), floor(z));
            case BUBBLE -> new Particles.Bubble(world, random, x, y, z, vx, vy, vz);
            case SPLASH -> new Particles.Splash(world, random, x, y, z, vx, vy, vz);
            case PORTAL -> new Particles.Portal(world, random, x, y, z, vx, vy, vz);
            case ENCHANTMENT_TABLE -> new Particles.EnchantmentTable(world, random, x, y, z, vx, vy, vz);
        };
        if (particle != null) add(particle);
    }

    /**
     * Something fell into water (Entity's splash on entering water): 1 +
     * width x 20 bubbles and as many splash droplets across its width at the
     * top of the water block its feet are in.
     */
    public void addWaterSplash(double x, double feetY, double z, float width, double motionX, double motionY, double motionZ) {
        double surface = Math.floor(feetY) + 1.0;
        int count = (int) (1.0f + width * 20.0f);
        for (int i = 0; i < count; i++) {
            float dx = (random.nextFloat() * 2f - 1f) * width, dz = (random.nextFloat() * 2f - 1f) * width;
            spawn(Kind.BUBBLE, x + dx, surface, z + dz, motionX, motionY - random.nextFloat() * 0.2f, motionZ, null);
        }
        for (int i = 0; i < count; i++) {
            float dx = (random.nextFloat() * 2f - 1f) * width, dz = (random.nextFloat() * 2f - 1f) * width;
            spawn(Kind.SPLASH, x + dx, surface, z + dz, motionX, motionY, motionZ, null);
        }
    }

    /** The 64 flecks a broken block bursts into (a 4x4x4 grid, each flying out from the centre). */
    public void addBlockDestroyEffects(int bx, int by, int bz, BlockState state) {
        if (state.block().isAir()) return;
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                for (int k = 0; k < 4; k++) {
                    double x = bx + (i + 0.5) / 4.0, y = by + (j + 0.5) / 4.0, z = bz + (k + 0.5) / 4.0;
                    Particle particle = digging(state, x, y, z, x - bx - 0.5, y - by - 0.5, z - bz - 0.5, bx, by, bz);
                    if (particle != null) add(particle);
                }
    }

    /** One fleck chipped off the struck face while mining: just outside the block's box, slow and small. */
    public void addBlockHitEffects(int bx, int by, int bz, Direction side) {
        BlockState state = world.getState(bx, by, bz);
        Block block = state.block();
        if (block.isAir()) return;
        AxisAlignedBB box = block.selectionBox(block.actualState(world, bx, by, bz, state), world, bx, by, bz);
        if (box == null) box = AxisAlignedBB.FULL;
        float f = 0.1f;
        double x = bx + random.nextDouble() * (box.maxX() - box.minX() - f * 2) + f + box.minX();
        double y = by + random.nextDouble() * (box.maxY() - box.minY() - f * 2) + f + box.minY();
        double z = bz + random.nextDouble() * (box.maxZ() - box.minZ() - f * 2) + f + box.minZ();
        switch (side) {
            case DOWN -> y = by + box.minY() - f;
            case UP -> y = by + box.maxY() + f;
            case NORTH -> z = bz + box.minZ() - f;
            case SOUTH -> z = bz + box.maxZ() + f;
            case WEST -> x = bx + box.minX() - f;
            case EAST -> x = bx + box.maxX() + f;
        }
        Particle particle = digging(state, x, y, z, 0, 0, 0, bx, by, bz);
        if (particle != null) add(particle.multiplyVelocity(0.2f).multiplyScale(0.6f));
    }

    private Particle digging(BlockState state, double x, double y, double z, double vx, double vy, double vz,
                             int bx, int by, int bz) {
        if (state == null || state.block().isAir()) return null;
        BakedModel model;
        ActualState actual;
        synchronized (models) {
            actual = state.block().actualState(world, bx, by, bz, state);
            model = models.getModel(actual, bx, by, bz);
        }
        if (model == null || model.particleTexture() == null) return null;
        TextureAtlas.Tile tile = atlas.tile(model.particleTexture());
        return new Particles.Digging(world, random, x, y, z, vx, vy, vz, tile, tint(actual, bx, bz));
    }

    /** The block's colour for its flecks (grass blocks are not tinted: their flecks are dirt). */
    private int tint(ActualState state, int x, int z) {
        Block block = state.block();
        BiomeColors.BiomeLookup lookup = world::biomeAt;
        return switch (block.name().path()) {
            case "tallgrass" -> biomeColors.grassAt(lookup, x, z);
            case "double_plant" -> state.get("variant").equals("double_grass") || state.get("variant").equals("double_fern")
                ? biomeColors.grassAt(lookup, x, z) : 0xffffff;
            case "leaves" -> switch (state.get("variant")) {
                case "spruce" -> 0x619961;
                case "birch" -> 0x80a755;
                default -> biomeColors.foliageAt(lookup, x, z);
            };
            case "leaves2", "vine" -> biomeColors.foliageAt(lookup, x, z);
            case "water", "flowing_water" -> biomeColors.waterAt(lookup, x, z);
            case "grass" -> 0xffffff;
            default -> block.tintColor();
        };
    }

    // ---- ticking ------------------------------------------------------------------------------

    public void tick() {
        for (ArrayDeque<Particle> layer : layers) {
            for (Iterator<Particle> it = layer.iterator(); it.hasNext(); ) {
                Particle particle = it.next();
                particle.tick();
                if (particle.isDead()) it.remove();
            }
        }
        for (Particle particle : added) admit(particle);
        added.clear();
    }

    /**
     * 1000 random blocks within 16 of the player (triangular spread, as
     * World.doVoidFogParticles) get their random display tick.
     */
    public void randomDisplayTicks(double playerX, double playerY, double playerZ) {
        int px = floor(playerX), py = floor(playerY), pz = floor(playerZ);
        for (int i = 0; i < 1000; i++) {
            int x = px + random.nextInt(16) - random.nextInt(16);
            int y = py + random.nextInt(16) - random.nextInt(16);
            int z = pz + random.nextInt(16) - random.nextInt(16);
            if (y < 0 || y >= World.HEIGHT || world.generatedChunk(x >> 4, z >> 4) == null) continue;
            displayTick(x, y, z, world.getState(x, y, z));
        }
    }

    void displayTick(int x, int y, int z, BlockState state) {
        String name = state.block().name().path();
        switch (name) {
            case "torch" -> torch(x, y, z, state, false);
            case "redstone_torch" -> torch(x, y, z, state, true);
            case "lit_furnace" -> furnace(x, y, z, state);
            case "fire" -> {
                if (random.nextInt(24) == 0)
                    sounds.play("fire.fire", x + 0.5, y + 0.5, z + 0.5, 1f + random.nextFloat(), random.nextFloat() * 0.7f + 0.3f);
                for (int i = 0; i < 3; i++)
                    spawn(Kind.LARGE_SMOKE, x + random.nextDouble(), y + random.nextDouble() * 0.5 + 0.5, z + random.nextDouble(), 0, 0, 0, null);
            }
            case "mycelium" -> {
                if (random.nextInt(10) == 0) spawn(Kind.TOWN_AURA, x + random.nextFloat(), y + 1.1f, z + random.nextFloat(), 0, 0, 0, null);
            }
            case "water", "flowing_water", "lava", "flowing_lava" -> liquid(x, y, z, state);
            case "portal" -> portal(x, y, z);
            case "enchanting_table" -> enchantingTable(x, y, z);
            default -> { }
        }
    }

    /**
     * BlockEnchantmentTable.randomDisplayTick: each bookshelf in the ring two
     * blocks out (at table height or one above), 1 time in 16, sends a glyph
     * to above the table, unless the block halfway between is not air.
     */
    private void enchantingTable(int x, int y, int z) {
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (i > -2 && i < 2 && j == -1) j = 2;
                if (random.nextInt(16) != 0) continue;
                for (int k = 0; k <= 1; k++) {
                    if (!world.inBounds(x + i, y + k, z + j) || !world.get(x + i, y + k, z + j).name().path().equals("bookshelf")) continue;
                    if (!world.get(x + i / 2, y, z + j / 2).isAir()) break;
                    spawn(Kind.ENCHANTMENT_TABLE, x + 0.5, y + 2.0, z + 0.5, i + random.nextFloat() - 0.5,
                        k - random.nextFloat() - 1f, j + random.nextFloat() - 0.5, null);
                }
            }
        }
    }

    /**
     * BlockPortal.randomDisplayTick: now and then the portal hum, and four
     * swirl particles, each starting a quarter block out from either face and
     * flying off across the sheet.
     */
    private void portal(int x, int y, int z) {
        if (random.nextInt(100) == 0)
            sounds.play("portal.portal", x + 0.5, y + 0.5, z + 0.5, 0.5f, random.nextFloat() * 0.4f + 0.8f);
        boolean alongX = world.get(x - 1, y, z).name().path().equals("portal") || world.get(x + 1, y, z).name().path().equals("portal");
        for (int i = 0; i < 4; i++) {
            double px = x + random.nextFloat(), py = y + random.nextFloat(), pz = z + random.nextFloat();
            double vx = (random.nextFloat() - 0.5) * 0.5, vy = (random.nextFloat() - 0.5) * 0.5, vz = (random.nextFloat() - 0.5) * 0.5;
            int side = random.nextInt(2) * 2 - 1;
            if (!alongX) {
                px = x + 0.5 + 0.25 * side;
                vx = random.nextFloat() * 2f * side;
            } else {
                pz = z + 0.5 + 0.25 * side;
                vz = random.nextFloat() * 2f * side;
            }
            spawn(Kind.PORTAL, px, py, pz, vx, vy, vz, null);
        }
    }

    private void torch(int x, int y, int z, BlockState state, boolean redstone) {
        String facing = state.get("facing");
        Direction direction = facing == null ? Direction.UP : Direction.fromName(facing);
        if (redstone) {
            double dx = x + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            double dy = y + 0.7 + (random.nextDouble() - 0.5) * 0.2;
            double dz = z + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            if (direction.isHorizontal()) {
                Direction opposite = direction.opposite();
                dx += 0.27 * opposite.xOffset;
                dy += 0.22;
                dz += 0.27 * opposite.zOffset;
            }
            spawn(Kind.REDDUST, dx, dy, dz, 0, 0, 0, null);
            return;
        }
        double cx = x + 0.5, cy = y + 0.7, cz = z + 0.5;
        if (direction.isHorizontal()) {
            Direction opposite = direction.opposite();
            cx += 0.27 * opposite.xOffset;
            cy += 0.22;
            cz += 0.27 * opposite.zOffset;
        }
        spawn(Kind.SMOKE, cx, cy, cz, 0, 0, 0, null);
        spawn(Kind.FLAME, cx, cy, cz, 0, 0, 0, null);
    }

    private void furnace(int x, int y, int z, BlockState state) {
        String facing = state.get("facing");
        if (facing == null) return;
        double cx = x + 0.5, cy = y + random.nextDouble() * 6.0 / 16.0, cz = z + 0.5;
        double out = 0.52, along = random.nextDouble() * 0.6 - 0.3;
        switch (Direction.fromName(facing)) {
            case WEST -> { cx -= out; cz += along; }
            case EAST -> { cx += out; cz += along; }
            case NORTH -> { cx += along; cz -= out; }
            case SOUTH -> { cx += along; cz += out; }
            default -> { return; }
        }
        spawn(Kind.SMOKE, cx, cy, cz, 0, 0, 0, null);
        spawn(Kind.FLAME, cx, cy, cz, 0, 0, 0, null);
    }

    private void liquid(int x, int y, int z, BlockState state) {
        boolean water = state.block().material().equals("water");
        String levelText = state.get("level");
        int level = levelText == null ? 0 : Integer.parseInt(levelText);
        if (water) {
            if (level > 0 && level < 8) {
                if (random.nextInt(64) == 0)
                    sounds.play("liquid.water", x + 0.5, y + 0.5, z + 0.5, random.nextFloat() * 0.25f + 0.75f, random.nextFloat() + 0.5f);
            } else if (random.nextInt(10) == 0) {
                spawn(Kind.SUSPENDED, x + random.nextFloat(), y + random.nextFloat(), z + random.nextFloat(), 0, 0, 0, null);
            }
        } else if (y + 1 < World.HEIGHT) {
            Block above = world.get(x, y + 1, z);
            if (above.isAir()) {
                if (random.nextInt(100) == 0) {
                    double px = x + random.nextFloat(), pz = z + random.nextFloat();
                    spawn(Kind.LAVA, px, y + 1.0, pz, 0, 0, 0, null);
                    sounds.play("liquid.lavapop", px, y + 1.0, pz, 0.2f + random.nextFloat() * 0.2f, 0.9f + random.nextFloat() * 0.15f);
                }
                if (random.nextInt(200) == 0)
                    sounds.play("liquid.lava", x, y, z, 0.2f + random.nextFloat() * 0.2f, 0.9f + random.nextFloat() * 0.15f);
            }
        }
        if (random.nextInt(10) == 0 && y >= 2 && world.get(x, y - 1, z).isOpaque()) {
            String below = world.get(x, y - 2, z).material();
            boolean blocksMovement = !below.equals("air") && !below.equals("plants") && !below.equals("vine")
                && !below.equals("circuits") && !below.equals("fire") && !below.equals("portal");
            boolean liquidBelow = below.equals("water") || below.equals("lava");
            if (!blocksMovement && !liquidBelow)
                spawn(water ? Kind.DRIP_WATER : Kind.DRIP_LAVA, x + random.nextFloat(), y - 1.05, z + random.nextFloat(), 0, 0, 0, null);
        }
    }

    // ---- drawing ------------------------------------------------------------------------------

    /**
     * Draws every particle facing the camera (the modelview holds the world
     * view). {@code yaw}/{@code pitch} are the camera's, vanilla convention.
     */
    public void render(float partialTicks, float yaw, float pitch) {
        if (count() == 0) return;
        float yawRad = (float) Math.toRadians(yaw), pitchRad = (float) Math.toRadians(pitch);
        float[] left = {(float) Math.cos(yawRad), 0f, (float) Math.sin(yawRad)};
        float[] up = {(float) (-Math.sin(yawRad) * Math.sin(pitchRad)), (float) Math.cos(pitchRad),
            (float) (Math.cos(yawRad) * Math.sin(pitchRad))};
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.003921569f);
        glDisable(GL_CULL_FACE);
        glDepthMask(true);
        for (int layer = 0; layer < 2; layer++) {
            if (layers[layer].isEmpty()) continue;
            if (layer == 0) textures.bind(PARTICLES);
            else atlas.bind();
            glBegin(GL_QUADS);
            for (Particle particle : layers[layer]) {
                int[] light = particle.light(partialTicks);
                particle.render(partialTicks, left, up, LightMap.color(light[0], light[1]));
            }
            glEnd();
        }
        glColor4f(1, 1, 1, 1);
        glAlphaFunc(GL_GREATER, 0.1f);
    }

    public void clear() {
        for (ArrayDeque<Particle> layer : layers) layer.clear();
        added.clear();
    }

    private static int floor(double value) { return (int) Math.floor(value); }
}
