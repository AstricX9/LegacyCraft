package net.minecraft.client.multiplayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Block breaking progress: survival mining over ticks, restarting on a new
 * block, and creative's instant breaks.
 */
final class PlayerControllerTest {
    @Test
    void survivalBreaksAfterEnoughTicksOnTheSameBlock() {
        PlayerController controller = new PlayerController();
        float perTick = 0.3f;
        assertFalse(controller.clickBlock(1, 2, 3, false, perTick));
        int ticks = 0;
        boolean broken = false;
        while (!broken && ticks < 10) { broken = controller.onPlayerDamageBlock(1, 2, 3, false, perTick); ticks++; }
        assertTrue(broken);
        assertEquals(4, ticks, "0.3 per tick reaches 1.0 on the fourth tick");
        assertFalse(controller.isHittingBlock());
        for (int i = 0; i < PlayerController.HIT_DELAY; i++) assertFalse(controller.onPlayerDamageBlock(1, 2, 3, false, 1f));
        assertTrue(controller.onPlayerDamageBlock(1, 2, 3, false, 1f), "instant blocks break once the delay is over");
    }

    @Test
    void movingToAnotherBlockOrReleasingRestarts() {
        PlayerController controller = new PlayerController();
        controller.clickBlock(0, 64, 0, false, 0.25f);
        controller.onPlayerDamageBlock(0, 64, 0, false, 0.25f);
        controller.onPlayerDamageBlock(0, 64, 0, false, 0.25f);
        assertEquals(0.5f, controller.curBlockDamage(), 1e-6);
        assertEquals(4, controller.destroyStage());
        controller.onPlayerDamageBlock(1, 64, 0, false, 0.25f);
        assertEquals(0f, controller.curBlockDamage(), "a new block starts over");
        controller.resetBlockRemoving();
        assertEquals(-1, controller.destroyStage());
    }

    @Test
    void creativeBreaksAtOnceThenEverySixTicksWhileHeld() {
        PlayerController controller = new PlayerController();
        assertTrue(controller.clickBlock(0, 64, 0, true, 0f));
        int ticks = 0;
        while (!controller.onPlayerDamageBlock(0, 63, 0, true, 0f)) ticks++;
        assertEquals(PlayerController.HIT_DELAY, ticks);
    }

    /** PlayerControllerMP.stepSoundTickCounter: the hit sound on the first mining tick and every fourth after. */
    @Test
    void miningMakesAHitSoundEveryFourthTick() {
        PlayerController controller = new PlayerController();
        controller.clickBlock(0, 64, 0, false, 0.01f);
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            controller.onPlayerDamageBlock(0, 64, 0, false, 0.01f);
            pattern.append(controller.takeHitSound() ? 'x' : '.');
        }
        assertEquals("x...x...x", pattern.toString());
        assertTrue(controller.clickBlock(0, 64, 0, true, 0f));
        assertFalse(controller.takeHitSound(), "creative breaks silently apart from the break sound");
    }
}
