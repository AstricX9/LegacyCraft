package net.minecraft.entity.boss;

import net.minecraft.block.BlockState;
import net.minecraft.entity.DamageSource;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import net.minecraft.world.WitherPattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The wither, its build pattern and mob heads (HYPOTHESIS: recalled 1.8.9 rules). */
final class WitherTest {
    private static final int FLOOR = 200;

    private static BlockState block(String name, int meta) { return BlockState.of(GameData.blocks().get("minecraft:" + name), meta); }

    /** A T of soul sand standing along x at (0..2, FLOOR+1..FLOOR+2, 0) with skulls on top. */
    private static World build(boolean lastSkull) {
        World world = GameData.newWorld(3L);
        for (int x = -3; x <= 5; x++) for (int y = FLOOR - 2; y <= FLOOR + 6; y++) for (int z = -3; z <= 3; z++)
            world.setState(x, y, z, block("air", 0));
        world.setState(1, FLOOR + 1, 0, block("soul_sand", 0));
        for (int x = 0; x <= 2; x++) world.setState(x, FLOOR + 2, 0, block("soul_sand", 0));
        for (int x = 0; x <= (lastSkull ? 2 : 1); x++) {
            world.setState(x, FLOOR + 3, 0, block("skull", 1));
            world.setSkull(x, FLOOR + 3, 0, 1, 0);
        }
        return world;
    }

    @Test
    void witherPatternIsFoundOnlyWhenComplete() {
        assertNull(WitherPattern.find(build(false), 1, FLOOR + 3, 0));
        WitherPattern.Match match = WitherPattern.find(build(true), 2, FLOOR + 3, 0);
        assertNotNull(match);
        int[] spawn = match.spawnBlock();
        assertEquals(1, spawn[0]);
        assertEquals(FLOOR + 1, spawn[1]);
        assertEquals(0, spawn[2]);
        assertEquals(9, match.all().size());
    }

    @Test
    void normalSkullsDontCount() {
        World world = build(true);
        world.setSkull(0, FLOOR + 3, 0, 0, 0);
        assertNull(WitherPattern.find(world, 2, FLOOR + 3, 0));
    }

    @Test
    void freshWitherIsInvulnerableAtAThirdOfItsHealth() {
        EntityWither wither = new EntityWither(GameData.newWorld(1L), 0.5, FLOOR, 0.5);
        assertEquals(300f, wither.maxHealth());
        wither.startSpawning();
        assertEquals(220, wither.invulTime);
        assertEquals(100f, wither.health, 1e-4);
        assertFalse(wither.attackEntityFrom(DamageSource.GENERIC, 10f, null));
    }

    @Test
    void armouredBelowHalfHealthAndArrowsBounce() {
        EntityWither wither = new EntityWither(GameData.newWorld(1L), 0.5, FLOOR, 0.5);
        wither.health = 149f;
        assertTrue(wither.isArmored());
        assertFalse(wither.attackEntityFrom(DamageSource.ARROW, 5f, null));
        assertTrue(wither.attackEntityFrom(DamageSource.GENERIC, 5f, null));
    }

    @Test
    void witherCantBreakBedrock() {
        assertFalse(EntityWither.canDestroy("bedrock"));
        assertFalse(EntityWither.canDestroy("command_block"));
        assertTrue(EntityWither.canDestroy("obsidian"));
    }
}
