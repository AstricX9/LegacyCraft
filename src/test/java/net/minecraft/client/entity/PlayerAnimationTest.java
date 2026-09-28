package net.minecraft.client.entity;

import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tick maths behind the view bob, arm swing, body turn and FOV (HYPOTHESIS constants). */
final class PlayerAnimationTest {
    private static final World WORLD = GameData.newWorld(1L);

    private static Player player() {
        Player player = new Player(WORLD);
        player.onGround = true;
        return player;
    }

    @Test
    void walkingBobsUpToTheCapAndStopsInTheAir() {
        Player player = player();
        PlayerAnimation animation = new PlayerAnimation();
        animation.tick(player);
        for (int i = 0; i < 20; i++) { player.z += 0.2158; animation.tick(player); }
        assertEquals(0.1f, animation.cameraYaw, 0.001f);
        assertEquals(20 * 0.2158f * 0.6f, animation.distanceWalked, 0.01f);
        player.onGround = false;
        for (int i = 0; i < 20; i++) { player.z += 0.2158; animation.tick(player); }
        assertEquals(0f, animation.cameraYaw, 0.001f);
    }

    @Test
    void aSwingLastsSixTicks() {
        Player player = player();
        PlayerAnimation animation = new PlayerAnimation();
        animation.swing();
        float[] seen = new float[7];
        for (int i = 0; i < 7; i++) { animation.tick(player); seen[i] = animation.swingProgress; }
        assertEquals(0f, seen[0], 0f);
        assertEquals(5f / 6f, seen[5], 1e-6f);
        assertEquals(0f, seen[6], 0f);
    }

    @Test
    void sprintAndFlightWidenTheView() {
        Player player = player();
        PlayerAnimation animation = new PlayerAnimation();
        player.sprinting = true;
        for (int i = 0; i < 30; i++) animation.tick(player);
        assertEquals(1.15f, animation.fovModifier, 1e-4f);
        player.isFlying = true;
        for (int i = 0; i < 30; i++) animation.tick(player);
        assertEquals(1.1f * 1.15f, animation.fovModifier, 1e-4f);
    }

    @Test
    void theBodyStaysWithin75DegreesOfTheHead() {
        Player player = player();
        PlayerAnimation animation = new PlayerAnimation();
        animation.tick(player);
        player.yaw += 170f;
        animation.tick(player);
        float offset = PlayerAnimation.wrap(PlayerAnimation.vanillaYaw(player) - animation.bodyYaw);
        assertTrue(Math.abs(offset) <= 75f + 1e-3f, "offset " + offset);
    }
}
