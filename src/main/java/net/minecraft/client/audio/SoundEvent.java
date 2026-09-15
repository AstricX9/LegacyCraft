package net.minecraft.client.audio;

import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Named sound event with weighted variants from sounds.json. */
public record SoundEvent(ResourceLocation name, String category, List<Variant> variants) {
    public SoundEvent {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        variants = List.copyOf(variants);
        if (variants.isEmpty()) throw new IllegalArgumentException("Sound event has no variants: " + name);
    }
    public Variant choose(Random random) {
        int total = variants.stream().mapToInt(Variant::weight).sum();
        int selected = random.nextInt(total);
        for (Variant variant : variants) {
            selected -= variant.weight();
            if (selected < 0) return variant;
        }
        throw new AssertionError("unreachable");
    }
    public record Variant(ResourceLocation file, float volume, float pitch, int weight, boolean stream) {
        public Variant {
            if (volume < 0 || pitch < 0 || weight < 1) throw new IllegalArgumentException("Invalid sound variant");
        }
    }
}
