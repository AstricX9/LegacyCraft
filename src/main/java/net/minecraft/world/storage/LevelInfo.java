package net.minecraft.world.storage;

import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTag;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A world's {@code level.dat}: the gzipped NBT {@code {Data: {...}}} compound
 * with 1.8.9's tag names and types (master context §11.5, §12;
 * COMMUNITY_CORROBORATED from the Minecraft Wiki level-format history,
 * HYPOTHESIS until diffed against a vanilla-written file — unknowns.yml).
 *
 * <p>Tags this class does not model are kept from the file that was read and
 * written back unchanged. Chunks still use LegacyCraft's own format
 * (DEV-003), so a world folder is not yet openable by vanilla.
 */
public final class LevelInfo {
    /** level.dat {@code version}: 19133 marks the Anvil format. */
    public static final int ANVIL_VERSION = 19133;

    public String levelName = "";
    public long randomSeed;
    public WorldType terrainType = WorldType.DEFAULT;
    public String generatorOptions = "";
    public WorldSettings.GameType gameType = WorldSettings.GameType.SURVIVAL;
    public boolean mapFeatures = true;
    public int spawnX, spawnY = 64, spawnZ;
    public long totalTime, worldTime;
    public long lastPlayed;
    public long sizeOnDisk;
    public boolean hardcore;
    public boolean allowCommands;
    public int difficulty = 2;
    public boolean difficultyLocked;
    public boolean initialized;
    public int rainTime, thunderTime, clearWeatherTime;
    public boolean raining, thundering;
    public final Map<String, String> gameRules = defaultGameRules();
    /** The player's saved position; null until the world has been played. */
    public PlayerPosition player;
    /** The dimension the player is in (Player.Dimension: 0 overworld, -1 the Nether). */
    public int playerDimension;
    private NbtTag.Compound original = new NbtTag.Compound();

    public record PlayerPosition(double x, double y, double z, float yaw, float pitch, boolean onGround) {}

    /**
     * One saved inventory stack, as 1.8.9's {@code Player.Inventory} list
     * stores it: {@code Slot} (0-35 main, 100-103 armor from boots up),
     * {@code id}, {@code Count}, {@code Damage}. LegacyCraft writes its own
     * item names as the id (DEV-015).
     */
    public record SavedStack(int slot, String id, int count, int damage, NbtTag.Compound tag) {
        public SavedStack(int slot, String id, int count, int damage) { this(slot, id, count, damage, null); }
    }

    /** 1.8.9's {@code Player.abilities} compound. */
    public record Abilities(boolean flying, boolean mayfly, boolean instabuild, boolean invulnerable, boolean mayBuild,
                            float flySpeed, float walkSpeed) {
        /** What a game mode grants (vanilla WorldSettings.GameType.configurePlayerCapabilities). */
        public static Abilities of(WorldSettings.GameType type, boolean flying) {
            boolean creative = type == WorldSettings.GameType.CREATIVE, spectator = type == WorldSettings.GameType.SPECTATOR;
            boolean mayfly = creative || spectator;
            return new Abilities(mayfly && flying || spectator, mayfly, creative, creative || spectator,
                type != WorldSettings.GameType.ADVENTURE && !spectator, 0.05f, 0.1f);
        }
    }

    /** The player's saved inventory; null when level.dat has none. */
    public List<SavedStack> inventory;
    public int selectedItemSlot;
    /** The player's saved abilities; null when level.dat has none. */
    public Abilities abilities;
    /** 1.8.9's {@code XpLevel}, {@code XpP} (progress to the next level) and {@code XpTotal}. */
    public record Experience(int level, float progress, int total) {}
    /** The player's saved experience; null when level.dat has none. */
    public Experience experience;
    /** 1.8.9's {@code HealF} (health), {@code Air} and {@code XpSeed} (the enchanting table's seed). */
    public record Vitals(float health, int air, int xpSeed) {}
    /** The player's saved health, air and table seed; null when level.dat has none. */
    public Vitals vitals;
    /** 1.8.9's {@code foodLevel}, {@code foodSaturationLevel}, {@code foodExhaustionLevel} and {@code foodTickTimer}. */
    public record Food(int level, float saturation, float exhaustion, int timer) {}
    /** The player's saved hunger; null when level.dat has none. */
    public Food food;
    /** One entry of 1.8.9's {@code ActiveEffects}: Id, Amplifier, Duration, Ambient, ShowParticles. */
    public record SavedEffect(int id, int amplifier, int duration, boolean ambient, boolean showParticles) {}
    /** The player's saved effects; null when level.dat has none. */
    public List<SavedEffect> effects;
    /** 1.8.9's {@code AbsorptionAmount}. */
    public float absorption;

    /** The 1.8.9 game rules and their defaults (VERIFIED_HISTORY: /gamerule list for 1.8). */
    public static Map<String, String> defaultGameRules() {
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("doFireTick", "true");
        rules.put("mobGriefing", "true");
        rules.put("keepInventory", "false");
        rules.put("doMobSpawning", "true");
        rules.put("doMobLoot", "true");
        rules.put("doTileDrops", "true");
        rules.put("doEntityDrops", "true");
        rules.put("commandBlockOutput", "true");
        rules.put("naturalRegeneration", "true");
        rules.put("doDaylightCycle", "true");
        rules.put("logAdminCommands", "true");
        rules.put("showDeathMessages", "true");
        rules.put("randomTickSpeed", "3");
        rules.put("sendCommandFeedback", "true");
        rules.put("reducedDebugInfo", "false");
        return rules;
    }

    /** A new world from the Create New World settings. */
    public static LevelInfo create(String name, WorldSettings settings) {
        LevelInfo info = new LevelInfo();
        info.levelName = name;
        info.randomSeed = settings.seed();
        info.terrainType = settings.worldType();
        info.generatorOptions = settings.generatorOptions();
        info.gameType = settings.gameType();
        info.mapFeatures = settings.mapFeatures();
        info.hardcore = settings.hardcore();
        info.allowCommands = settings.commandsAllowed();
        if (settings.hardcore()) info.difficulty = 3;
        return info;
    }

    public WorldSettings toSettings() {
        return new WorldSettings(randomSeed, gameType, mapFeatures, hardcore, terrainType, allowCommands, false, generatorOptions);
    }

    public NbtTag.Compound toNbt() {
        NbtTag.Compound data = new NbtTag.Compound();
        data.entries().putAll(original.entries());
        data.put("RandomSeed", new NbtTag.Long(randomSeed));
        data.put("generatorName", new NbtTag.Str(terrainType.getWorldTypeName()));
        data.put("generatorVersion", new NbtTag.Int(terrainType.getGeneratorVersion()));
        data.put("generatorOptions", new NbtTag.Str(generatorOptions));
        data.put("GameType", new NbtTag.Int(gameType.getID()));
        data.put("MapFeatures", bool(mapFeatures));
        data.put("SpawnX", new NbtTag.Int(spawnX));
        data.put("SpawnY", new NbtTag.Int(spawnY));
        data.put("SpawnZ", new NbtTag.Int(spawnZ));
        data.put("Time", new NbtTag.Long(totalTime));
        data.put("DayTime", new NbtTag.Long(worldTime));
        data.put("SizeOnDisk", new NbtTag.Long(sizeOnDisk));
        data.put("LastPlayed", new NbtTag.Long(lastPlayed));
        data.put("LevelName", new NbtTag.Str(levelName));
        data.put("version", new NbtTag.Int(ANVIL_VERSION));
        data.put("clearWeatherTime", new NbtTag.Int(clearWeatherTime));
        data.put("rainTime", new NbtTag.Int(rainTime));
        data.put("raining", bool(raining));
        data.put("thunderTime", new NbtTag.Int(thunderTime));
        data.put("thundering", bool(thundering));
        data.put("hardcore", bool(hardcore));
        data.put("initialized", bool(initialized));
        data.put("allowCommands", bool(allowCommands));
        data.put("Difficulty", new NbtTag.Byte((byte) difficulty));
        data.put("DifficultyLocked", bool(difficultyLocked));
        putDefault(data, "BorderCenterX", new NbtTag.Double(0));
        putDefault(data, "BorderCenterZ", new NbtTag.Double(0));
        putDefault(data, "BorderSize", new NbtTag.Double(6.0E7));
        putDefault(data, "BorderSizeLerpTime", new NbtTag.Long(0));
        putDefault(data, "BorderSizeLerpTarget", new NbtTag.Double(6.0E7));
        putDefault(data, "BorderSafeZone", new NbtTag.Double(5));
        putDefault(data, "BorderDamagePerBlock", new NbtTag.Double(0.2));
        putDefault(data, "BorderWarningBlocks", new NbtTag.Double(5));
        putDefault(data, "BorderWarningTime", new NbtTag.Double(15));
        NbtTag.Compound rules = new NbtTag.Compound();
        for (var rule : gameRules.entrySet()) rules.put(rule.getKey(), new NbtTag.Str(rule.getValue()));
        data.put("GameRules", rules);
        if (player != null) {
            NbtTag.Compound playerTag = data.get("Player") instanceof NbtTag.Compound existing ? copy(existing) : new NbtTag.Compound();
            playerTag.put("Pos", doubles(player.x(), player.y(), player.z()));
            playerTag.put("Rotation", floats(player.yaw(), player.pitch()));
            playerTag.put("OnGround", bool(player.onGround()));
            playerTag.put("Dimension", new NbtTag.Int(playerDimension));
            playerTag.put("playerGameType", new NbtTag.Int(gameType.getID()));
            if (inventory != null) {
                List<NbtTag> stacks = new ArrayList<>();
                for (SavedStack stack : inventory) {
                    NbtTag.Compound tag = new NbtTag.Compound();
                    tag.put("Slot", new NbtTag.Byte((byte) stack.slot()));
                    tag.put("id", new NbtTag.Str(stack.id()));
                    tag.put("Count", new NbtTag.Byte((byte) stack.count()));
                    tag.put("Damage", new NbtTag.Short((short) stack.damage()));
                    if (stack.tag() != null) tag.put("tag", stack.tag());
                    stacks.add(tag);
                }
                playerTag.put("Inventory", new NbtTag.NbtList(stacks.isEmpty() ? NbtTag.END : NbtTag.COMPOUND, stacks));
                playerTag.put("SelectedItemSlot", new NbtTag.Int(selectedItemSlot));
            }
            if (abilities != null) {
                NbtTag.Compound tag = new NbtTag.Compound();
                tag.put("flying", bool(abilities.flying()));
                tag.put("mayfly", bool(abilities.mayfly()));
                tag.put("instabuild", bool(abilities.instabuild()));
                tag.put("invulnerable", bool(abilities.invulnerable()));
                tag.put("mayBuild", bool(abilities.mayBuild()));
                tag.put("flySpeed", new NbtTag.Float(abilities.flySpeed()));
                tag.put("walkSpeed", new NbtTag.Float(abilities.walkSpeed()));
                playerTag.put("abilities", tag);
            }
            if (vitals != null) {
                playerTag.put("HealF", new NbtTag.Float(vitals.health()));
                playerTag.put("Health", new NbtTag.Short((short) Math.ceil(vitals.health())));
                playerTag.put("Air", new NbtTag.Short((short) vitals.air()));
                playerTag.put("XpSeed", new NbtTag.Int(vitals.xpSeed()));
            }
            if (food != null) {
                playerTag.put("foodLevel", new NbtTag.Int(food.level()));
                playerTag.put("foodTickTimer", new NbtTag.Int(food.timer()));
                playerTag.put("foodSaturationLevel", new NbtTag.Float(food.saturation()));
                playerTag.put("foodExhaustionLevel", new NbtTag.Float(food.exhaustion()));
            }
            playerTag.put("AbsorptionAmount", new NbtTag.Float(absorption));
            if (effects != null) {
                // EntityLivingBase.writeEntityToNBT writes the list only when something is active.
                playerTag.entries().remove("ActiveEffects");
                if (!effects.isEmpty()) {
                    List<NbtTag> list = new ArrayList<>();
                    for (SavedEffect effect : effects) {
                        NbtTag.Compound tag = new NbtTag.Compound();
                        tag.put("Id", new NbtTag.Byte((byte) effect.id()));
                        tag.put("Amplifier", new NbtTag.Byte((byte) effect.amplifier()));
                        tag.put("Duration", new NbtTag.Int(effect.duration()));
                        tag.put("Ambient", bool(effect.ambient()));
                        tag.put("ShowParticles", bool(effect.showParticles()));
                        list.add(tag);
                    }
                    playerTag.put("ActiveEffects", new NbtTag.NbtList(NbtTag.COMPOUND, list));
                }
            }
            if (experience != null) {
                playerTag.put("XpLevel", new NbtTag.Int(experience.level()));
                playerTag.put("XpP", new NbtTag.Float(experience.progress()));
                playerTag.put("XpTotal", new NbtTag.Int(experience.total()));
            }
            data.put("Player", playerTag);
        }
        NbtTag.Compound root = new NbtTag.Compound();
        root.put("Data", data);
        return root;
    }

    public static LevelInfo fromNbt(NbtTag.Compound root) {
        NbtTag.Compound data = root.getCompound("Data");
        LevelInfo info = new LevelInfo();
        info.original = copy(data);
        info.randomSeed = longOr(data, "RandomSeed", 0);
        if (data.get("generatorName") instanceof NbtTag.Str name) {
            WorldType type = WorldType.parse(name.value());
            if (type == WorldType.DEFAULT && intOr(data, "generatorVersion", 1) == 0) type = WorldType.DEFAULT_1_1;
            info.terrainType = type != null ? type : WorldType.DEFAULT;
        }
        info.generatorOptions = stringOr(data, "generatorOptions", "");
        info.gameType = WorldSettings.GameType.getByID(intOr(data, "GameType", 0));
        info.mapFeatures = byteOr(data, "MapFeatures", 1) != 0;
        info.spawnX = intOr(data, "SpawnX", 0);
        info.spawnY = intOr(data, "SpawnY", 64);
        info.spawnZ = intOr(data, "SpawnZ", 0);
        info.totalTime = longOr(data, "Time", 0);
        info.worldTime = data.has("DayTime") ? longOr(data, "DayTime", 0) : info.totalTime;
        info.lastPlayed = longOr(data, "LastPlayed", 0);
        info.sizeOnDisk = longOr(data, "SizeOnDisk", 0);
        info.levelName = stringOr(data, "LevelName", "");
        info.clearWeatherTime = intOr(data, "clearWeatherTime", 0);
        info.rainTime = intOr(data, "rainTime", 0);
        info.raining = byteOr(data, "raining", 0) != 0;
        info.thunderTime = intOr(data, "thunderTime", 0);
        info.thundering = byteOr(data, "thundering", 0) != 0;
        info.hardcore = byteOr(data, "hardcore", 0) != 0;
        info.initialized = byteOr(data, "initialized", 1) != 0;
        info.allowCommands = data.has("allowCommands") ? byteOr(data, "allowCommands", 0) != 0
            : info.gameType == WorldSettings.GameType.CREATIVE;
        info.difficulty = byteOr(data, "Difficulty", 2);
        info.difficultyLocked = byteOr(data, "DifficultyLocked", 0) != 0;
        if (data.get("GameRules") instanceof NbtTag.Compound rules)
            for (var rule : rules.entries().entrySet())
                if (rule.getValue() instanceof NbtTag.Str value) info.gameRules.put(rule.getKey(), value.value());
        if (data.get("Player") instanceof NbtTag.Compound playerTag && playerTag.get("Pos") instanceof NbtTag.NbtList pos
            && pos.size() == 3) {
            float yaw = 0, pitch = 0;
            if (playerTag.get("Rotation") instanceof NbtTag.NbtList rotation && rotation.size() == 2) {
                yaw = ((NbtTag.Float) rotation.values().get(0)).value();
                pitch = ((NbtTag.Float) rotation.values().get(1)).value();
            }
            info.player = new PlayerPosition(((NbtTag.Double) pos.values().get(0)).value(),
                ((NbtTag.Double) pos.values().get(1)).value(), ((NbtTag.Double) pos.values().get(2)).value(),
                yaw, pitch, byteOr(playerTag, "OnGround", 0) != 0);
            if (playerTag.get("Dimension") instanceof NbtTag.Int dimension) info.playerDimension = dimension.value() == -1 ? -1 : 0;
        }
        if (data.get("Player") instanceof NbtTag.Compound playerTag) {
            if (playerTag.get("Inventory") instanceof NbtTag.NbtList list) {
                info.inventory = new ArrayList<>();
                for (NbtTag element : list.values()) {
                    if (!(element instanceof NbtTag.Compound tag) || !(tag.get("id") instanceof NbtTag.Str id)) continue;
                    int damage = tag.get("Damage") instanceof NbtTag.Short value ? value.value() : 0;
                    info.inventory.add(new SavedStack(byteOr(tag, "Slot", 0) & 0xFF, id.value(), byteOr(tag, "Count", 1), damage,
                        tag.get("tag") instanceof NbtTag.Compound extra ? extra : null));
                }
                info.selectedItemSlot = intOr(playerTag, "SelectedItemSlot", 0);
            }
            if (playerTag.get("abilities") instanceof NbtTag.Compound tag) {
                info.abilities = new Abilities(byteOr(tag, "flying", 0) != 0, byteOr(tag, "mayfly", 0) != 0,
                    byteOr(tag, "instabuild", 0) != 0, byteOr(tag, "invulnerable", 0) != 0, byteOr(tag, "mayBuild", 1) != 0,
                    tag.get("flySpeed") instanceof NbtTag.Float fly ? fly.value() : 0.05f,
                    tag.get("walkSpeed") instanceof NbtTag.Float walk ? walk.value() : 0.1f);
            }
            if (playerTag.has("HealF") || playerTag.has("Health")) {
                float health = playerTag.get("HealF") instanceof NbtTag.Float heal ? heal.value()
                    : playerTag.get("Health") instanceof NbtTag.Short shortHealth ? shortHealth.value() : 20f;
                int air = playerTag.get("Air") instanceof NbtTag.Short value ? value.value() : 300;
                info.vitals = new Vitals(health, air, intOr(playerTag, "XpSeed", 0));
            }
            if (playerTag.has("foodLevel"))
                info.food = new Food(intOr(playerTag, "foodLevel", 20),
                    playerTag.get("foodSaturationLevel") instanceof NbtTag.Float sat ? sat.value() : 5f,
                    playerTag.get("foodExhaustionLevel") instanceof NbtTag.Float ex ? ex.value() : 0f,
                    intOr(playerTag, "foodTickTimer", 0));
            if (playerTag.get("AbsorptionAmount") instanceof NbtTag.Float absorb) info.absorption = absorb.value();
            if (playerTag.get("ActiveEffects") instanceof NbtTag.NbtList list) {
                info.effects = new ArrayList<>();
                for (NbtTag element : list.values()) {
                    if (!(element instanceof NbtTag.Compound tag)) continue;
                    info.effects.add(new SavedEffect(byteOr(tag, "Id", 0) & 0xFF, byteOr(tag, "Amplifier", 0),
                        intOr(tag, "Duration", 0), byteOr(tag, "Ambient", 0) != 0, byteOr(tag, "ShowParticles", 1) != 0));
                }
            }
            if (playerTag.has("XpLevel"))
                info.experience = new Experience(intOr(playerTag, "XpLevel", 0),
                    playerTag.get("XpP") instanceof NbtTag.Float progress ? progress.value() : 0f, intOr(playerTag, "XpTotal", 0));
        }
        return info;
    }

    public static LevelInfo read(Path levelDat) throws IOException { return fromNbt(NbtIo.readCompressed(levelDat)); }
    public void write(Path levelDat) throws IOException { NbtIo.writeCompressed(toNbt(), levelDat); }

    private static NbtTag.Byte bool(boolean value) { return new NbtTag.Byte((byte) (value ? 1 : 0)); }
    private static void putDefault(NbtTag.Compound data, String name, NbtTag value) { if (!data.has(name)) data.put(name, value); }

    private static NbtTag.NbtList doubles(double... values) {
        List<NbtTag> tags = new ArrayList<>();
        for (double value : values) tags.add(new NbtTag.Double(value));
        return new NbtTag.NbtList(NbtTag.DOUBLE, tags);
    }

    private static NbtTag.NbtList floats(float... values) {
        List<NbtTag> tags = new ArrayList<>();
        for (float value : values) tags.add(new NbtTag.Float(value));
        return new NbtTag.NbtList(NbtTag.FLOAT, tags);
    }

    private static NbtTag.Compound copy(NbtTag.Compound source) {
        NbtTag.Compound copy = new NbtTag.Compound();
        copy.entries().putAll(source.entries());
        return copy;
    }

    private static long longOr(NbtTag.Compound data, String name, long fallback) {
        return data.get(name) instanceof NbtTag.Long value ? value.value() : fallback;
    }

    private static int intOr(NbtTag.Compound data, String name, int fallback) {
        return data.get(name) instanceof NbtTag.Int value ? value.value() : fallback;
    }

    private static int byteOr(NbtTag.Compound data, String name, int fallback) {
        return data.get(name) instanceof NbtTag.Byte value ? value.value() : fallback;
    }

    private static String stringOr(NbtTag.Compound data, String name, String fallback) {
        return data.get(name) instanceof NbtTag.Str value ? value.value() : fallback;
    }
}
