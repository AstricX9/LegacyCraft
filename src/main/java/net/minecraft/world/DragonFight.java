package net.minecraft.world;

import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The End's dragon fight (1.9 DragonFightManager, HYPOTHESIS: recalled):
 * whether the dragon is dead, whether it was ever killed (the egg and the
 * 12000 experience come only the first time), and the end gateways still
 * to open, twenty around the island in a shuffled order.
 * shortcut: saved as dragon_fight.dat in DIM1 instead of level.dat's DimensionData; move it with a level.dat rewrite.
 */
public final class DragonFight {
    public boolean dragonKilled, previouslyKilled;
    /** Gateway slots (0-19) not opened yet, in the order they open. */
    public final List<Integer> gateways = new ArrayList<>();
    /** Where each gateway leads (TileEntityEndGateway.exitPortal), by the gateway's "x,y,z"; exact for gateways built as exits. */
    public final java.util.Map<String, Exit> exits = new java.util.HashMap<>();
    public record Exit(int x, int y, int z, boolean exact) {}

    public static String key(int x, int y, int z) { return x + "," + y + "," + z; }

    public DragonFight(long seed) {
        for (int i = 0; i < 20; i++) gateways.add(i);
        Collections.shuffle(gateways, new Random(seed));
    }

    /** A gateway slot's position (DragonFightManager.spawnNewGateway): a ring of radius 96 at y 75. */
    public static int[] gatewayPosition(int slot) {
        int x = (int) Math.floor(96.0 * Math.cos(2.0 * (-Math.PI + 0.15707963267948966 * slot)));
        int z = (int) Math.floor(96.0 * Math.sin(2.0 * (-Math.PI + 0.15707963267948966 * slot)));
        return new int[]{x, 75, z};
    }

    public static DragonFight load(Path directory, long seed) {
        DragonFight fight = new DragonFight(seed);
        Path file = directory.resolve("dragon_fight.dat");
        if (!Files.isRegularFile(file)) return fight;
        try {
            NbtTag.Compound tag = NbtIo.readCompressed(file);
            fight.dragonKilled = tag.get("DragonKilled") instanceof NbtTag.Byte b && b.value() != 0;
            fight.previouslyKilled = tag.get("PreviouslyKilled") instanceof NbtTag.Byte b && b.value() != 0;
            if (tag.get("Gateways") instanceof NbtTag.NbtList list) {
                fight.gateways.clear();
                for (NbtTag value : list.values()) if (value instanceof NbtTag.Int i) fight.gateways.add(i.value());
            }
            if (tag.get("GatewayExits") instanceof NbtTag.NbtList list)
                for (NbtTag value : list.values()) {
                    if (!(value instanceof NbtTag.Compound c)) continue;
                    fight.exits.put(key(c.getInt("X"), c.getInt("Y"), c.getInt("Z")),
                        new Exit(c.getInt("ExitX"), c.getInt("ExitY"), c.getInt("ExitZ"), c.getByte("ExactTeleport") != 0));
                }
        } catch (IOException | RuntimeException error) {
            System.err.println("Couldn't read the dragon fight; starting a new one: " + error.getMessage());
        }
        return fight;
    }

    public void save(Path directory) throws IOException {
        NbtTag.Compound tag = new NbtTag.Compound();
        tag.put("DragonKilled", new NbtTag.Byte((byte) (dragonKilled ? 1 : 0)));
        tag.put("PreviouslyKilled", new NbtTag.Byte((byte) (previouslyKilled ? 1 : 0)));
        List<NbtTag> list = new ArrayList<>();
        for (int slot : gateways) list.add(new NbtTag.Int(slot));
        tag.put("Gateways", new NbtTag.NbtList(NbtTag.INT, list));
        List<NbtTag> exitList = new ArrayList<>();
        for (var entry : exits.entrySet()) {
            String[] at = entry.getKey().split(",");
            NbtTag.Compound c = new NbtTag.Compound();
            c.put("X", new NbtTag.Int(Integer.parseInt(at[0])));
            c.put("Y", new NbtTag.Int(Integer.parseInt(at[1])));
            c.put("Z", new NbtTag.Int(Integer.parseInt(at[2])));
            c.put("ExitX", new NbtTag.Int(entry.getValue().x()));
            c.put("ExitY", new NbtTag.Int(entry.getValue().y()));
            c.put("ExitZ", new NbtTag.Int(entry.getValue().z()));
            c.put("ExactTeleport", new NbtTag.Byte((byte) (entry.getValue().exact() ? 1 : 0)));
            exitList.add(c);
        }
        tag.put("GatewayExits", new NbtTag.NbtList(exitList.isEmpty() ? NbtTag.END : NbtTag.COMPOUND, exitList));
        NbtIo.writeCompressed(tag, directory.resolve("dragon_fight.dat"));
    }
}
