package net.minecraft.client.entity;

import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Gaining experience points and levels (HYPOTHESIS: recalled EntityPlayer.addExperience and xpBarCap). */
final class PlayerExperienceTest {
    private static Player player() { return new Player(GameData.newWorld(1L)); }

    @Test
    void levelsNeedMorePointsAsTheyRise() {
        Player player = player();
        assertEquals(7, player.xpBarCap());
        player.experienceLevel = 15;
        assertEquals(37, player.xpBarCap());
        player.experienceLevel = 30;
        assertEquals(112, player.xpBarCap());
        player.experienceLevel = 31;
        assertEquals(121, player.xpBarCap());
    }

    @Test
    void pointsFillTheBarAndSpillIntoLevels() {
        Player player = player();
        player.addExperience(3);
        assertEquals(0, player.experienceLevel);
        assertEquals(3f / 7f, player.experience, 1e-6);
        player.addExperience(4 + 9 + 2);
        assertEquals(2, player.experienceLevel, "7 for level 1 and 9 for level 2");
        assertEquals(2f / 11f, player.experience, 1e-5);
        assertEquals(18, player.experienceTotal);
    }
}
