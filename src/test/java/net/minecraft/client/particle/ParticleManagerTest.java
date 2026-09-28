package net.minecraft.client.particle;

import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Particle spawning rules and motion (HYPOTHESIS: recalled 1.8.9 constants). */
final class ParticleManagerTest {
    private static final World WORLD = GameData.newWorld(20260928L);
    private static final int GROUND = WORLD.surfaceHeight(0, 0);

    private static ParticleManager manager(int setting) {
        ParticleManager manager = new ParticleManager(WORLD, null, null, null, null);
        manager.setView(setting, 0.5, GROUND + 2, 0.5);
        return manager;
    }

    @Test
    void theParticlesOptionAndRangeDecideWhatSpawns() {
        ParticleManager all = manager(0), minimal = manager(2);
        for (int i = 0; i < 30; i++) {
            all.spawn(ParticleManager.Kind.FLAME, 0.5, GROUND + 2, 0.5, 0, 0, 0, null);
            minimal.spawn(ParticleManager.Kind.FLAME, 0.5, GROUND + 2, 0.5, 0, 0, 0, null);
            all.spawn(ParticleManager.Kind.FLAME, 40.5, GROUND + 2, 0.5, 0, 0, 0, null);
        }
        all.tick();
        minimal.tick();
        assertEquals(30, all.count(), "all nearby flames, none 40 blocks away");
        assertEquals(0, minimal.count());
    }

    @Test
    void decreasedDropsAboutAThird() {
        ParticleManager decreased = manager(1);
        for (int i = 0; i < 3000; i++) decreased.spawn(ParticleManager.Kind.FLAME, 0.5, GROUND + 2, 0.5, 0, 0, 0, null);
        decreased.tick();
        assertTrue(decreased.count() > 1800 && decreased.count() < 2200, "kept " + decreased.count());
    }

    @Test
    void eachTextureKeepsAtMost4000() {
        ParticleManager manager = manager(0);
        for (int i = 0; i < 4500; i++) manager.add(new Particles.Aura(WORLD, new Random(i), 0.5, GROUND + 2, 0.5));
        manager.tick();
        assertEquals(4000, manager.count());
    }

    @Test
    void smokeRisesAndDies() {
        Particles.Smoke smoke = new Particles.Smoke(WORLD, new Random(1), 0.5, GROUND + 3, 0.5, 0, 0, 0, 1f);
        double start = smoke.y;
        for (int i = 0; i < 10; i++) smoke.tick();
        assertTrue(smoke.y > start - 0.2, "smoke drifts up, not down");
        for (int i = 0; i < 200 && !smoke.isDead(); i++) smoke.tick();
        assertTrue(smoke.isDead());
    }

    @Test
    void aDripHangsBeforeFalling() {
        Particles.Drip drip = new Particles.Drip(WORLD, new Random(2), 0.5, GROUND + 20, 0.5, false, manager(0));
        double start = drip.y;
        for (int i = 0; i < 30; i++) drip.tick();
        assertEquals(start, drip.y, 0.05, "hanging");
        for (int i = 0; i < 20; i++) drip.tick();
        assertTrue(drip.y < start - 0.5, "falling");
    }

    @Test
    void fallingParticlesLandOnTheGround() {
        Particles.Splash splash = new Particles.Splash(WORLD, new Random(3), 0.5, GROUND + 1.5, 0.5, 0, 0, 0);
        for (int i = 0; i < 6; i++) splash.tick();
        assertTrue(splash.y >= GROUND - 1e-6, "stopped by the ground at " + splash.y);
    }
}
