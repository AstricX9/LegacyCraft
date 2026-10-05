package net.minecraft.sim;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * A lock-free hand-off between two threads (or the same one): any thread
 * adds, one thread drains at a time of its choosing, in the order added.
 * Commands wait in one for the start of the next tick, which gives every
 * input a defined place in the tick whether it came from the local player or
 * a socket; events wait in another until the viewer is ready for them.
 */
public final class MessageQueue<T> implements Consumer<T> {
    private final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<>();

    public void add(T message) { queue.add(message); }
    @Override public void accept(T message) { add(message); }
    public boolean isEmpty() { return queue.isEmpty(); }

    /** Hands everything queued to {@code consumer}, in order, until the queue is empty (messages added meanwhile are included); returns how many. */
    public int drain(Consumer<? super T> consumer) { return drain(consumer, Integer.MAX_VALUE); }

    /** As {@link #drain(Consumer)} but stops after {@code max} messages, so a flood cannot hold up a tick; the rest wait. */
    public int drain(Consumer<? super T> consumer, int max) {
        int count = 0;
        T message;
        while (count < max && (message = queue.poll()) != null) {
            consumer.accept(message);
            count++;
        }
        return count;
    }
}
