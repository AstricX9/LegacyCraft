package net.minecraft.testing;

import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The real 1.8.9 world that {@link Oracle} tests compare against: the
 * {@code legacycraft.vanillaWorld} system property, or {@code C:/Server/world}.
 * Pass it to Gradle as {@code -Dlegacycraft.vanillaWorld=<folder>}.
 */
public final class VanillaWorld {
    public static final Path PATH = Path.of(System.getProperty("legacycraft.vanillaWorld", "C:/Server/world"));

    private VanillaWorld() {}

    /** Skips the calling test (rather than failing it) when no vanilla world with region files is there. */
    public static void assumePresent() {
        Assumptions.assumeTrue(Files.isDirectory(PATH.resolve("region")), "no vanilla 1.8.9 world at " + PATH);
    }

    public static Path region() { return PATH.resolve("region"); }
    public static Path levelDat() { return PATH.resolve("level.dat"); }
}
