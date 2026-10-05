package net.minecraft.sim;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovingEntity;
import net.minecraft.client.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

final class SimSeamTest {
    @Test
    void queuePreservesOrderAndDrainsOnlyWhatIsAsked() {
        MessageQueue<Integer> q = new MessageQueue<>();
        for (int i = 0; i < 10; i++) q.add(i);
        List<Integer> got = new ArrayList<>();
        assertEquals(4, q.drain(got::add, 4));
        assertEquals(List.of(0, 1, 2, 3), got);
        assertFalse(q.isEmpty());
        assertEquals(6, q.drain(got::add));
        assertEquals(10, got.size());
        assertEquals(9, got.get(9).intValue());
        assertTrue(q.isEmpty());
        assertEquals(0, q.drain(got::add));
    }

    @Test
    void queueLosesNothingAndKeepsEachProducersOrderAcrossThreads() throws Exception {
        MessageQueue<long[]> q = new MessageQueue<>();
        int producers = 4, each = 50_000;
        List<Thread> threads = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            final int id = p;
            Thread t = new Thread(() -> { for (int i = 0; i < each; i++) q.add(new long[] {id, i}); });
            threads.add(t);
            t.start();
        }
        long[] next = new long[producers];
        AtomicBoolean ordered = new AtomicBoolean(true);
        int[] total = {0};
        while (total[0] < producers * each) {
            q.drain(m -> {
                if (m[1] != next[(int) m[0]]++) ordered.set(false);
                total[0]++;
            });
        }
        for (Thread t : threads) t.join();
        assertTrue(ordered.get(), "each producer's messages arrived in order");
        assertEquals(producers * each, total[0]);
        assertTrue(q.isEmpty());
    }

    @Test
    void commandsAreImmutableValuesThatCompareByContent() {
        assertEquals(new PlayerCommand.Dig(PlayerCommand.Dig.Stage.START, 1, 2, 3, 1), new PlayerCommand.Dig(PlayerCommand.Dig.Stage.START, 1, 2, 3, 1));
        assertFalse(new PlayerCommand.UseEntity(5, PlayerCommand.UseEntity.Kind.ATTACK).equals(new PlayerCommand.UseEntity(5, PlayerCommand.UseEntity.Kind.INTERACT)));
        assertThrows(IllegalArgumentException.class, () -> new PlayerCommand.Chat("x".repeat(PlayerCommand.Chat.MAX_LENGTH + 1)));
        assertDoesNotThrow(() -> new PlayerCommand.Chat("x".repeat(PlayerCommand.Chat.MAX_LENGTH)));
    }

    @Test
    void everyCommandAndEventIsAnImmutableRecord() {
        for (Class<?> type : PlayerCommand.class.getPermittedSubclasses()) assertTrue(type.isRecord(), type.getName());
        for (Class<?> type : GameEvent.class.getPermittedSubclasses()) {
            assertTrue(type.isRecord(), type.getName());
            for (var component : type.getRecordComponents())
                assertFalse(component.getType().isArray(), type.getSimpleName() + "." + component.getName() + " is an array (mutable)");
        }
        assertTrue(PlayerCommand.class.getPermittedSubclasses().length >= 12);
        assertTrue(GameEvent.class.getPermittedSubclasses().length >= 20);
    }

    /** Effects go out as events, in the order the entity made them. */
    @Test
    void effectCallsOnTheEntityHostBecomeEventsInOrder() {
        MessageQueue<GameEvent> events = new MessageQueue<>();
        EventEntityHost host = new EventEntityHost(events::add) {
            @Override protected int entityId(Entity entity) { return 77; }
            @Override public Player player() { return null; }
            @Override public List<Entity> entities() { return List.of(); }
            @Override public void spawn(Entity entity) {}
            @Override public void explode(Entity exploder, double x, double y, double z, float size, boolean smoking, String attacker) {}
            @Override public int difficulty() { return 2; }
            @Override public boolean gameRule(String name) { return true; }
            @Override public long worldTime() { return 0; }
        };
        host.playSound("mob.irongolem.hit", 1, 2, 3, 0.5f, 1.5f);
        host.globalSound("mob.wither.spawn", 4, 5, 6);
        host.particle("smoke", 1, 1, 1, 0, 0.1, 0);
        host.itemCrack("snowball", 2, 2, 2, 0, 0, 0);
        host.potionSmash(7, 8, 9, 16385);
        host.waterSplash(null);
        host.achievement("breedCow");
        host.giveExperience(5);
        List<GameEvent> got = new ArrayList<>();
        events.drain(got::add);
        assertEquals(List.of(
            new GameEvent.Sound("mob.irongolem.hit", 1, 2, 3, 0.5f, 1.5f),
            new GameEvent.GlobalSound("mob.wither.spawn", 4, 5, 6),
            new GameEvent.Particle("smoke", 1, 1, 1, 0, 0.1, 0),
            new GameEvent.ItemCrack("snowball", 2, 2, 2, 0, 0, 0),
            new GameEvent.PotionSmash(7, 8, 9, 16385),
            new GameEvent.EntityStatus(77, GameEvent.EntityStatus.Status.SPLASH),
            new GameEvent.AchievementAwarded("breedCow"),
            new GameEvent.ExperienceGained(5)), got);
    }
}
