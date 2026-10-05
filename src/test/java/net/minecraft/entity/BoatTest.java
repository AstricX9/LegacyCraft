package net.minecraft.entity;

import net.minecraft.block.BlockState;
import net.minecraft.client.entity.Player;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Boats on a walled pool of still water high above the terrain (HYPOTHESIS: behaviour recalled, DEV-038). */
final class BoatTest {
    private static final int FLOOR = 200;
    private World world;
    private FakeHost host;

    private static final class FakeHost implements EntityHost {
        final List<Entity> entities = new ArrayList<>();
        final List<int[]> broken = new ArrayList<>();
        boolean drops = true;
        @Override public Player player() { return null; }
        @Override public List<Entity> entities() { return new ArrayList<>(entities); }
        @Override public void spawn(Entity entity) {
            if (entity instanceof MovingEntity moving) moving.setHost(this);
            entities.add(entity);
        }
        @Override public void playSound(String event, double x, double y, double z, float volume, float pitch) {}
        @Override public void particle(String kind, double x, double y, double z, double vx, double vy, double vz) {}
        @Override public void explode(Entity exploder, double x, double y, double z, float size, boolean smoking, String attacker) {}
        @Override public int difficulty() { return 2; }
        @Override public boolean gameRule(String name) { return drops || !name.equals("doEntityDrops"); }
        @Override public long worldTime() { return 6000; }
        @Override public void breakBlock(int x, int y, int z, boolean drop) { broken.add(new int[] {x, y, z}); }

        List<String> dropped() {
            List<String> names = new ArrayList<>();
            for (Entity e : entities)
                if (e instanceof EntityItem item)
                    for (int i = 0; i < item.stack().count(); i++) names.add(item.stack().item().name().path());
            return names;
        }
    }

    /** A 21 x 21 pool, two deep, of still water with a stone floor and a stone rim. */
    @BeforeEach
    void pool() {
        world = GameData.newWorld(5L);
        BlockState stone = BlockState.defaultOf(GameData.blocks().get("minecraft:stone"));
        BlockState water = BlockState.defaultOf(GameData.blocks().get("minecraft:water"));
        for (int x = -11; x <= 11; x++)
            for (int z = -11; z <= 11; z++) {
                world.setState(x, FLOOR, z, stone);
                boolean rim = Math.abs(x) == 11 || Math.abs(z) == 11;
                for (int y = FLOOR + 1; y <= FLOOR + 2; y++) world.setState(x, y, z, rim ? stone : water);
            }
        host = new FakeHost();
        world.setSmelting(null, name -> GameData.items().get(name));
    }

    private EntityBoat boat(double x, double y, double z) {
        EntityBoat boat = new EntityBoat(world, x, y, z);
        host.spawn(boat);
        return boat;
    }

    private static void run(EntityBoat boat, int ticks) { for (int i = 0; i < ticks && !boat.isRemoved(); i++) boat.tick(); }

    @Test
    void floatsWithAboutHalfItsHeightUnderWater() {
        EntityBoat boat = boat(0.5, FLOOR + 3.5, 0.5);
        run(boat, 200);
        double surface = FLOOR + 3;
        assertTrue(boat.y() < surface && boat.y() > surface - boat.height, "rests in the surface, not on the floor: " + boat.y());
        assertTrue(boat.submerged > 0 && boat.submerged < 1, "partly under: " + boat.submerged);
        assertEquals(0, boat.motionX, 1e-9);
    }

    @Test
    void sinksToTheGroundOnLand() {
        EntityBoat boat = boat(0.5, FLOOR + 6, 20.5);
        BlockState stone = BlockState.defaultOf(GameData.blocks().get("minecraft:stone"));
        for (int x = -1; x <= 2; x++) for (int z = 19; z <= 22; z++) world.setState(x, FLOOR + 2, z, stone);
        run(boat, 100);
        assertEquals(FLOOR + 3, boat.y(), 1e-6);
        assertTrue(boat.onGround);
    }

    @Test
    void riderPaddlesTheWayTheyFaceUpToTheTopSpeed() {
        EntityBoat boat = boat(0.5, FLOOR + 2.7, -8.5);
        run(boat, 40);
        boat.ridden = true;
        boat.setRiderInput(1f, 0f);   // yaw 0 faces +z
        double start = boat.z();
        for (int i = 0; i < 30; i++) {
            boat.tick();
            assertTrue(boat.horizontalSpeed() <= EntityBoat.MAX_SPEED + 1e-9);
        }
        assertFalse(boat.isRemoved());
        assertTrue(boat.z() - start > 3, "moved forward: " + (boat.z() - start));
        assertEquals(0.5, boat.x(), 1e-6);
        assertEquals(0f, MovingEntity.wrapDegrees(boat.rotationYaw), 1f);
    }

    @Test
    void backingUpIsSlowerThanGoingForward() {
        EntityBoat fwd = boat(0.5, FLOOR + 2.7, -2.5), back = boat(4.5, FLOOR + 2.7, 2.5);
        run(fwd, 40);
        run(back, 40);
        fwd.ridden = back.ridden = true;
        fwd.setRiderInput(1f, 0f);
        back.setRiderInput(-1f, 0f);
        run(fwd, 10);
        run(back, 10);
        assertTrue(back.horizontalSpeed() < fwd.horizontalSpeed());
        assertTrue(back.motionZ < 0);
    }

    @Test
    void ramsIntoTheBankAndBreaksIntoPlanksAndSticks() {
        EntityBoat boat = boat(0.5, FLOOR + 2.7, 0.5);
        run(boat, 40);
        boat.ridden = true;
        boat.setRiderInput(1f, 0f);
        run(boat, 200);
        assertTrue(boat.isRemoved(), "smashed against the rim");
        List<String> drops = host.dropped();
        assertEquals(3, drops.stream().filter("planks"::equals).count());
        assertEquals(2, drops.stream().filter("stick"::equals).count());
    }

    @Test
    void driftingGentlyIntoTheBankDoesNotBreakIt() {
        EntityBoat boat = boat(0.5, FLOOR + 2.7, 8.5);
        run(boat, 40);
        boat.motionZ = 0.1;
        run(boat, 100);
        assertFalse(boat.isRemoved());
    }

    @Test
    void fivePunchesKnockItLooseAsABoat() {
        EntityBoat boat = boat(0.5, FLOOR + 2.7, 0.5);
        for (int i = 0; i < 4; i++) assertFalse(boat.hitByPlayer(false), "hit " + (i + 1));
        assertTrue(boat.hitByPlayer(false));
        assertTrue(boat.isRemoved());
        assertEquals(List.of("boat"), host.dropped());
    }

    @Test
    void damageWearsOffBetweenHits() {
        EntityBoat boat = boat(0.5, FLOOR + 2.7, 0.5);
        for (int i = 0; i < 6; i++) {
            assertFalse(boat.hitByPlayer(false));
            run(boat, 10);
        }
        assertFalse(boat.isRemoved());
    }

    @Test
    void creativeBreaksItAtOnceWithoutADrop() {
        EntityBoat boat = boat(0.5, FLOOR + 2.7, 0.5);
        assertTrue(boat.hitByPlayer(true));
        assertTrue(host.dropped().isEmpty());
    }

    @Test
    void savesAndLoadsAsBoat() {
        EntityBoat boat = boat(1.5, FLOOR + 2.7, 2.5);
        boat.rotationYaw = 45f;
        net.minecraft.nbt.NbtTag.Compound tag = new net.minecraft.nbt.NbtTag.Compound();
        boat.writeNbt(tag);
        MovingEntity loaded = EntityList.fromNbt(tag, world);
        assertInstanceOf(EntityBoat.class, loaded);
        assertEquals(1.5, loaded.x(), 1e-9);
        assertEquals(45f, loaded.rotationYaw, 1e-6);
    }
}
