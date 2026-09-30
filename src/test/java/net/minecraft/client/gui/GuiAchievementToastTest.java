package net.minecraft.client.gui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The achievement pop-up's slide (HYPOTHESIS: recalled GuiAchievement timing). */
final class GuiAchievementToastTest {
    @BeforeEach
    void noLanguage() { I18n.set(Map.of()); }

    @Test
    void anEarnedAchievementSlidesInHoldsAndLeavesWithinThreeSeconds() {
        GuiAchievementToast toast = new GuiAchievementToast();
        toast.display("Getting Wood", null, 1000);
        assertEquals(0.0, toast.slide(1000), 1e-9, "starts hidden");
        assertEquals(1.0, toast.slide(1000 + 375), 1e-9, "fully in after an eighth");
        assertEquals(1.0, toast.slide(2500), 1e-9);
        assertTrue(toast.slide(1000 + 2900) < 1.0, "leaving");
        assertEquals(-1, toast.slide(4001), "gone");
        assertEquals(-1, toast.slide(5000));
    }

    @Test
    void aHintAppearsLaterAndStays() {
        GuiAchievementToast toast = new GuiAchievementToast();
        toast.displayHint("Taking Inventory", "Press 'E' to open your inventory.", null, 0);
        assertEquals(-1, toast.slide(1000), "waits 2.5 seconds");
        assertEquals(1.0, toast.slide(10_000_000L), 1e-9, "stays");
        assertTrue(toast.isShowingHint());
        toast.display("Taking Inventory", null, 20_000_000L);
        assertFalse(toast.isShowingHint(), "earning replaces it");
    }
}
