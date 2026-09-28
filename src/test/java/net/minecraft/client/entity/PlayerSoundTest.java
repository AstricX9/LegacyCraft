package net.minecraft.client.entity;

import net.minecraft.block.BlockState;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The player's footsteps and splashes (HYPOTHESIS: recalled Entity.moveEntity step rules). */
final class PlayerSoundTest {
    private static final int FLOOR = 240;
    private static final Player.Input FORWARD = new Player.Input(true, false, false, false, false, false, false);
    private static final Player.Input SNEAK = new Player.Input(true, false, false, false, false, true, false);
    private World world;
    private Player player;
    private final List<String> heard = new ArrayList<>();

    @BeforeEach
    void runway() {
        world = GameData.newWorld(3L);
        world.disablePopulation();
        BlockState planks = BlockState.defaultOf(GameData.blocks().get("minecraft:planks"));
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 60; z++) world.setState(x, FLOOR, z, planks);
        player = new Player(world);
        player.setPositionAndRotation(0.5, FLOOR + 1, 0.5, 180f, 0f);
        player.sounds = (event, volume, pitch) -> heard.add(event);
        for (int i = 0; i < 5; i++) player.tick(Player.Input.NONE);
        heard.clear();
    }

    @Test
    void walkingStepsOnceEveryBlockAndTwoThirdsOnTheBlockUnderfoot() {
        double start = player.z;
        for (int i = 0; i < 100; i++) player.tick(FORWARD);
        double walked = player.z - start;
        assertTrue(heard.stream().allMatch("step.wood"::equals), heard.toString());
        int expected = (int) (walked * 0.6);
        assertTrue(Math.abs(heard.size() - expected) <= 1, heard.size() + " steps over " + walked + " blocks");
    }

    @Test
    void sneakingAndFlyingAreSilent() {
        for (int i = 0; i < 60; i++) player.tick(SNEAK);
        assertTrue(heard.isEmpty(), heard.toString());
        player.allowFlying = true;
        player.setPositionAndRotation(0.5, FLOOR + 1.5, player.z, 180f, 0f);
        player.isFlying = true;
        for (int i = 0; i < 60; i++) player.tick(FORWARD);
        assertTrue(heard.isEmpty(), heard.toString());
    }

    @Test
    void fallingIntoWaterSplashes() {
        BlockState water = BlockState.defaultOf(GameData.blocks().get("minecraft:water"));
        for (int x = -2; x <= 2; x++) for (int z = 10; z <= 14; z++) for (int y = FLOOR - 6; y <= FLOOR; y++) world.setState(x, y, z, water);
        player.setPositionAndRotation(0.5, FLOOR + 3, 12.5, 180f, 0f);
        for (int i = 0; i < 20; i++) player.tick(Player.Input.NONE);
        assertTrue(heard.contains("game.player.swim.splash"), heard.toString());
    }
}
