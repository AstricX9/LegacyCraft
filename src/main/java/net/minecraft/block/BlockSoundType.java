package net.minecraft.block;

/**
 * A block's sound set (1.8.9 Block.SoundType, HYPOTHESIS: the per-block
 * assignments are recalled and inferred from the material, DEV-023): the
 * sound events for breaking, stepping and placing, and the volume and
 * frequency they are played with. Breaking plays {@code break} at
 * (volume + 1) / 2 and frequency x 0.8, placing the same; each fourth tick
 * of mining plays {@code step} at (volume + 1) / 8 and frequency x 0.5;
 * footsteps play {@code step} at volume x 0.15 and the plain frequency.
 */
public record BlockSoundType(String name, float volume, float frequency, String breakSound, String stepSound,
                             String placeSound) {
    public static final BlockSoundType STONE = plain("stone", 1f, 1f);
    public static final BlockSoundType WOOD = plain("wood", 1f, 1f);
    public static final BlockSoundType GRAVEL = plain("gravel", 1f, 1f);
    public static final BlockSoundType GRASS = plain("grass", 1f, 1f);
    public static final BlockSoundType METAL = plain("stone", 1f, 1.5f);
    public static final BlockSoundType GLASS = new BlockSoundType("stone", 1f, 1f, "dig.glass", "step.stone", "step.stone");
    public static final BlockSoundType CLOTH = plain("cloth", 1f, 1f);
    public static final BlockSoundType SAND = plain("sand", 1f, 1f);
    public static final BlockSoundType SNOW = plain("snow", 1f, 1f);
    public static final BlockSoundType LADDER = new BlockSoundType("ladder", 1f, 1f, "dig.wood", "step.ladder", "dig.wood");
    public static final BlockSoundType ANVIL = new BlockSoundType("anvil", 0.3f, 1f, "dig.stone", "step.stone", "random.anvil_land");
    public static final BlockSoundType SLIME = new BlockSoundType("slime", 1f, 1f, "mob.slime.big", "mob.slime.small", "mob.slime.big");

    private static BlockSoundType plain(String name, float volume, float frequency) {
        return new BlockSoundType(name, volume, frequency, "dig." + name, "step." + name, "dig." + name);
    }

    /** The sound set of a block: named exceptions first, then by material; stone when nothing matches (vanilla's default). */
    public static BlockSoundType of(Block block) {
        String name = block.name().path();
        switch (name) {
            case "ladder": return LADDER;
            case "anvil": return ANVIL;
            case "slime": return SLIME;
            case "gravel", "dirt", "farmland", "clay": return GRAVEL;
            case "bed", "cocoa", "pumpkin_stem", "melon_stem", "torch", "redstone_torch", "unlit_redstone_torch", "lever",
                 "wooden_button", "wooden_pressure_plate", "light_weighted_pressure_plate", "heavy_weighted_pressure_plate",
                 "unpowered_repeater", "powered_repeater", "unpowered_comparator", "powered_comparator", "fire",
                 "daylight_detector", "daylight_detector_inverted": return WOOD;
            case "rail", "golden_rail", "detector_rail", "activator_rail", "iron_door", "iron_bars", "iron_trapdoor",
                 "iron_block", "gold_block", "diamond_block", "emerald_block", "redstone_block", "hopper", "mob_spawner",
                 "command_block": return METAL;
            case "end_portal_frame", "beacon", "redstone_lamp", "lit_redstone_lamp": return GLASS;
            case "snow_layer", "snow": return SNOW;
            case "tnt", "sponge", "hay_block", "reeds": return GRASS;
            case "cactus", "cake", "carpet", "wool": return CLOTH;
            default: break;
        }
        return switch (block.material()) {
            case "wood", "gourd" -> WOOD;
            case "grass", "plants", "vine", "leaves" -> GRASS;
            case "ground" -> GRAVEL;
            case "sand" -> SAND;
            case "cloth", "carpet", "cactus", "cake" -> CLOTH;
            case "glass", "ice", "packed_ice", "portal", "redstone_light" -> GLASS;
            case "snow" -> SNOW;
            case "iron" -> METAL;
            default -> STONE;
        };
    }
}
