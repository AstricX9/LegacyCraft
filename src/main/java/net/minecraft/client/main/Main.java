package net.minecraft.client.main;

import net.minecraft.client.Minecraft;

/** Process entry point. Runtime ownership lives in {@link Minecraft}. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        Minecraft.main(args);
    }
}
