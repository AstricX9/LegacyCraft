package net.minecraft.client.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Right-arm poses (HYPOTHESIS: recalled ModelBiped heldItemRight angles). */
final class PlayerModelTest {
    private static PlayerModel posed(boolean holding, boolean blocking) {
        PlayerModel model = new PlayerModel();
        model.setPose(new PlayerModel.Pose(0, 0, 0, 0, 0, 0, false, holding, blocking));
        return model;
    }

    @Test
    void holdingLowersTheArmATenthOfATurnAndBlockingRaisesItAcrossTheBody() {
        float empty = posed(false, false).rightArm.angleX;
        float holding = posed(true, false).rightArm.angleX;
        PlayerModel blocking = posed(true, true);
        assertEquals(-(float) Math.PI / 10f, holding - empty * 0.5f, 1e-5);
        assertEquals(-(float) Math.PI / 10f * 3f, blocking.rightArm.angleX - empty * 0.5f, 1e-5);
        assertEquals(-0.5235988f, blocking.rightArm.angleY, 1e-6, "turned 30 degrees inwards");
        assertEquals(0f, posed(true, false).rightArm.angleY, 1e-6);
    }
}
