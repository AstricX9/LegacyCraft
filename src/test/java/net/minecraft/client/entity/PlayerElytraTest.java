package net.minecraft.client.entity;

import net.minecraft.item.ItemStack;
import net.minecraft.testing.GameData;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 1.9 elytra gliding (HYPOTHESIS: recalled EntityLivingBase.moveEntityWithHeading). */
final class PlayerElytraTest {
    private static final Player.Input JUMP = new Player.Input(false, false, false, false, true, false, false);

    private static Player falling(float pitch, boolean elytra) {
        Player player = new Player(GameData.newWorld(3L));
        player.setPositionAndRotation(0.5, 240, 0.5, 180f, pitch);
        if (elytra) player.armor[2] = new ItemStack(TestItems.item("elytra", 1, 432));
        for (int i = 0; i < 3; i++) player.tick(Player.Input.NONE);
        player.tick(JUMP);
        return player;
    }

    @Test
    void jumpingWhileFallingOpensTheElytraAndShrinksTheHitbox() {
        Player player = falling(0f, true);
        assertTrue(player.elytraFlying);
        assertEquals(0.6, player.boundingBox().maxY() - player.boundingBox().minY(), 1e-9);
        assertFalse(falling(0f, false).elytraFlying);
    }

    @Test
    void levelGlideFallsSlowlyAndMovesForward() {
        Player player = falling(0f, true);
        double startY = player.y, startZ = player.z;
        for (int i = 0; i < 100; i++) player.tick(Player.Input.NONE);
        double dropped = startY - player.y, travelled = player.z - startZ;
        assertTrue(player.elytraFlying);
        assertTrue(travelled > dropped * 3, "glide ratio: " + travelled + " forward for " + dropped + " down");
        assertTrue(player.fallDistance < 3f, "landing from a glide does no harm");
    }

    @Test
    void divingBuildsSpeedThatPullingUpTradesForHeight() {
        Player player = falling(60f, true);
        for (int i = 0; i < 40; i++) player.tick(Player.Input.NONE);
        double speed = Math.hypot(player.motionX, player.motionZ);
        double sinking = player.motionY;
        player.pitch = -30f;
        for (int i = 0; i < 30; i++) player.tick(Player.Input.NONE);
        assertTrue(speed > 0.5, "dive speed " + speed);
        assertTrue(sinking < -0.5 && player.motionY > 0, "pulling up turns the dive into a climb: " + sinking + " -> " + player.motionY);
    }

    @Test
    void elytraWearsOneASecondAndStopsJustBeforeBreaking() {
        Player player = falling(0f, true);
        for (int i = 0; i < 40; i++) player.tick(Player.Input.NONE);
        assertEquals(2, player.armor[2].damage());
        player.armor[2].setDamage(431);
        player.tick(Player.Input.NONE);
        assertFalse(player.elytraFlying);
    }
}
