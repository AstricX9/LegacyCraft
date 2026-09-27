package net.minecraft.command;

import net.minecraft.client.gui.I18n;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.LevelInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The built-in commands, written from how 1.8.9's commands behave
 * (HYPOTHESIS: argument parsing, messages and defaults are recalled; the
 * message text itself comes from the supplied lang files). Commands that
 * need systems LegacyCraft lacks (entities other than the player, items by
 * name, scoreboards, effects) are not here yet: deviations.yml DEV-018.
 */
public final class Commands {
    private Commands() {}

    static List<Command> all(CommandHandler handler) {
        return List.of(new Help(handler), new Time(), new GameRule(), new GameMode(), new Difficulty(), new Teleport(),
            new Seed(), new Kill(), new Say(), new Emote(), new Weather(), new SetWorldSpawn(), new Clear());
    }

    // ---- shared parsing ---------------------------------------------------------------------

    public static int parseInt(String text) throws CommandException {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException notANumber) {
            throw new CommandException("commands.generic.num.invalid", text);
        }
    }

    public static int parseInt(String text, int min) throws CommandException { return parseInt(text, min, Integer.MAX_VALUE); }

    public static int parseInt(String text, int min, int max) throws CommandException {
        int value = parseInt(text);
        if (value < min) throw new CommandException("commands.generic.num.tooSmall", value, min);
        if (value > max) throw new CommandException("commands.generic.num.tooBig", value, max);
        return value;
    }

    public static double parseDouble(String text) throws CommandException {
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value)) throw new CommandException("commands.generic.num.invalid", text);
            return value;
        } catch (NumberFormatException notANumber) {
            throw new CommandException("commands.generic.num.invalid", text);
        }
    }

    /**
     * A coordinate, absolute or relative to {@code base} with "~". Whole
     * absolute numbers get +0.5 when {@code centerBlock} (the middle of the
     * block); bounds apply unless both are 0.
     */
    public static double parseCoordinate(double base, String text, double min, double max, boolean centerBlock)
        throws CommandException {
        boolean relative = text.startsWith("~");
        double value = relative ? base : 0;
        if (!relative || text.length() > 1) {
            boolean decimal = text.contains(".");
            if (relative) text = text.substring(1);
            value += parseDouble(text);
            if (!relative && !decimal && centerBlock) value += 0.5;
        }
        if (min != 0 || max != 0) {
            if (value < min) throw new CommandException("commands.generic.double.tooSmall", value, min);
            if (value > max) throw new CommandException("commands.generic.double.tooBig", value, max);
        }
        return value;
    }

    /** Joins with ", " and a final " and ". */
    public static String joinNicely(List<String> items) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) out.append(i == items.size() - 1 ? " and " : ", ");
            out.append(items.get(i));
        }
        return out.toString();
    }

    /** The options starting with {@code word}, ignoring case. */
    public static List<String> matching(String word, List<String> options) {
        List<String> result = new ArrayList<>();
        for (String option : options) if (option.regionMatches(true, 0, word, 0, word.length())) result.add(option);
        return result;
    }

    static List<String> last(String[] args, String... options) { return matching(args[args.length - 1], List.of(options)); }

    static String join(String[] args, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
    }

    /** The one player: by name (any case) or a player selector. */
    static void player(CommandSender sender, String text) throws CommandException {
        if (text.equalsIgnoreCase(sender.name()) || text.equals("@p") || text.equals("@a") || text.equals("@r")) return;
        throw new CommandException("commands.generic.player.notFound");
    }

    static void entity(CommandSender sender, String text) throws CommandException {
        if (text.equalsIgnoreCase(sender.name()) || text.startsWith("@")) return;
        throw new CommandException("commands.generic.entity.notFound");
    }

    /** Command feedback, shown unless the sendCommandFeedback rule is off. */
    static void feedback(CommandSender sender, String key, Object... args) {
        if (!"false".equals(sender.level().gameRules.get("sendCommandFeedback")))
            sender.addChatMessage(I18n.translateChat(key, args), false);
    }

    static double blockX(CommandSender sender) { return Math.floor(sender.x()); }
    static double blockY(CommandSender sender) { return Math.floor(sender.y()); }
    static double blockZ(CommandSender sender) { return Math.floor(sender.z()); }

    // ---- commands ---------------------------------------------------------------------------

    static final class Help implements Command {
        private static final int PAGE = 7;
        private final CommandHandler handler;
        Help(CommandHandler handler) { this.handler = handler; }
        @Override public String name() { return "help"; }
        @Override public List<String> aliases() { return List.of("?"); }
        @Override public String usage() { return "commands.help.usage"; }
        @Override public int permissionLevel() { return 0; }

        @Override public void execute(CommandSender sender, String[] args) throws CommandException {
            List<Command> usable = handler.usable(sender);
            int lastPage = (usable.size() - 1) / PAGE;
            int page;
            try {
                page = args.length == 0 ? 0 : parseInt(args[0], 1, lastPage + 1) - 1;
            } catch (CommandException notAPage) {
                Command named = handler.get(args[0]);
                if (named != null) throw new CommandException.WrongUsage(named.usage());
                try {
                    Integer.parseInt(args[0]);
                    throw notAPage;
                } catch (NumberFormatException notANumber) {
                    throw new CommandException("commands.generic.notFound");
                }
            }
            sender.addChatMessage("§2" + I18n.translateChat("commands.help.header", page + 1, lastPage + 1), false);
            for (int i = page * PAGE; i < Math.min((page + 1) * PAGE, usable.size()); i++)
                sender.addChatMessage(I18n.translateChat(usable.get(i).usage()), false);
            if (page == 0) sender.addChatMessage("§a" + I18n.translateChat("commands.help.footer"), false);
        }

        @Override public List<String> complete(CommandSender sender, String[] args) {
            if (args.length != 1) return List.of();
            List<String> names = new ArrayList<>();
            for (Command command : handler.usable(sender)) names.add(command.name());
            return matching(args[0], names);
        }
    }

    static final class Time implements Command {
        @Override public String name() { return "time"; }
        @Override public String usage() { return "commands.time.usage"; }

        @Override public void execute(CommandSender sender, String[] args) throws CommandException {
            if (args.length > 1) {
                LevelInfo level = sender.level();
                switch (args[0]) {
                    case "set" -> {
                        int time = args[1].equals("day") ? 1000 : args[1].equals("night") ? 13000 : parseInt(args[1], 0);
                        level.worldTime = time;
                        feedback(sender, "commands.time.set", time);
                        return;
                    }
                    case "add" -> {
                        int amount = parseInt(args[1], 0);
                        level.worldTime += amount;
                        feedback(sender, "commands.time.added", amount);
                        return;
                    }
                    case "query" -> {
                        if (args[1].equals("daytime")) {
                            feedback(sender, "commands.time.query", (int) (level.worldTime % 24000L));
                            return;
                        }
                        if (args[1].equals("gametime")) {
                            feedback(sender, "commands.time.query", (int) (level.totalTime % Integer.MAX_VALUE));
                            return;
                        }
                    }
                    default -> { }
                }
            }
            throw new CommandException.WrongUsage(usage());
        }

        @Override public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return last(args, "set", "add", "query");
            if (args.length == 2 && args[0].equals("set")) return last(args, "day", "night");
            if (args.length == 2 && args[0].equals("query")) return last(args, "daytime", "gametime");
            return List.of();
        }
    }

    static final class GameRule implements Command {
        @Override public String name() { return "gamerule"; }
        @Override public String usage() { return "commands.gamerule.usage"; }

        private static boolean isBooleanRule(Map<String, String> rules, String name) {
            String value = rules.get(name);
            String original = LevelInfo.defaultGameRules().get(name);
            String sample = original != null ? original : value;
            return "true".equals(sample) || "false".equals(sample);
        }

        @Override public void execute(CommandSender sender, String[] args) throws CommandException {
            Map<String, String> rules = sender.level().gameRules;
            if (args.length == 0) {
                sender.addChatMessage(joinNicely(new ArrayList<>(rules.keySet())), false);
                return;
            }
            String name = args[0];
            if (args.length == 1) {
                if (!rules.containsKey(name)) throw new CommandException("commands.gamerule.norule", name);
                sender.addChatMessage(name + " = " + rules.get(name), false);
                return;
            }
            String value = join(args, 1);
            if (rules.containsKey(name) && isBooleanRule(rules, name) && !value.equals("true") && !value.equals("false"))
                throw new CommandException("commands.generic.boolean.invalid", value);
            rules.put(name, value);
            feedback(sender, "commands.gamerule.success");
        }

        @Override public List<String> complete(CommandSender sender, String[] args) {
            Map<String, String> rules = sender.level().gameRules;
            if (args.length == 1) return matching(args[0], new ArrayList<>(rules.keySet()));
            if (args.length == 2 && rules.containsKey(args[0]) && isBooleanRule(rules, args[0])) return last(args, "true", "false");
            return List.of();
        }
    }

    static final class GameMode implements Command {
        @Override public String name() { return "gamemode"; }
        @Override public String usage() { return "commands.gamemode.usage"; }

        static WorldSettings.GameType parse(String text) throws CommandException {
            if (text.equalsIgnoreCase("survival") || text.equalsIgnoreCase("s")) return WorldSettings.GameType.SURVIVAL;
            if (text.equalsIgnoreCase("creative") || text.equalsIgnoreCase("c")) return WorldSettings.GameType.CREATIVE;
            if (text.equalsIgnoreCase("adventure") || text.equalsIgnoreCase("a")) return WorldSettings.GameType.ADVENTURE;
            if (text.equalsIgnoreCase("spectator") || text.equalsIgnoreCase("sp")) return WorldSettings.GameType.SPECTATOR;
            return WorldSettings.GameType.getByID(parseInt(text, 0, 3));
        }

        @Override public void execute(CommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) throw new CommandException.WrongUsage(usage());
            WorldSettings.GameType type = parse(args[0]);
            if (args.length >= 2) player(sender, args[1]);
            sender.setGameType(type);
            feedback(sender, "gameMode.changed");
            feedback(sender, "commands.gamemode.success.self", I18n.translateChat(type.translationKey()));
        }

        @Override public List<String> complete(CommandSender sender, String[] args) {
            if (args.length == 1) return last(args, "survival", "creative", "adventure", "spectator");
            if (args.length == 2) return matching(args[1], List.of(sender.name()));
            return List.of();
        }
    }

    static final class Difficulty implements Command {
        private static final String[] NAMES = {"peaceful", "easy", "normal", "hard"};
        @Override public String name() { return "difficulty"; }
        @Override public String usage() { return "commands.difficulty.usage"; }

        @Override public void execute(CommandSender sender, String[] args) throws CommandException {
            if (args.length <= 0) throw new CommandException.WrongUsage(usage());
            String text = args[0];
            int difficulty = -1;
            for (int i = 0; i < NAMES.length; i++)
                if (text.equalsIgnoreCase(NAMES[i]) || text.equalsIgnoreCase(NAMES[i].substring(0, 1))) difficulty = i;
            if (difficulty < 0) difficulty = parseInt(text, 0, 3);
            sender.setDifficulty(difficulty);
            feedback(sender, "commands.difficulty.success", I18n.translateChat("options.difficulty." + NAMES[difficulty]));
        }

        @Override public List<String> complete(CommandSender sender, String[] args) {
            return args.length == 1 ? last(args, NAMES) : List.of();
        }
    }

    static final class Teleport implements Command {
        private static final double LIMIT = 30000000;
        @Override public String name() { return "tp"; }
        @Override public String usage() { return "commands.tp.usage"; }

        @Override public void execute(CommandSender sender, String[] args) throws CommandException {
            if (args.length < 1) throw new CommandException.WrongUsage(usage());
            int i = 0;
            if (args.length == 2 || args.length == 4 || args.length == 6) {
                entity(sender, args[0]);
                i = 1;
            }
            if (args.length == 1 || args.length == 2) {
                entity(sender, args[args.length - 1]);
                sender.teleport(sender.x(), sender.y(), sender.z(), sender.yaw(), sender.pitch());
                feedback(sender, "commands.tp.success", sender.name(), sender.name());
                return;
            }
            if (args.length < i + 3) throw new CommandException.WrongUsage(usage());
            double x = parseCoordinate(sender.x(), args[i++], -LIMIT, LIMIT, true);
            double y = parseCoordinate(sender.y(), args[i++], 0, 0, false);
            double z = parseCoordinate(sender.z(), args[i++], -LIMIT, LIMIT, true);
            double yaw = parseCoordinate(sender.yaw(), args.length > i ? args[i++] : "~", -LIMIT, LIMIT, false);
            double pitch = parseCoordinate(sender.pitch(), args.length > i ? args[i] : "~", -LIMIT, LIMIT, false);
            float wrappedYaw = wrapDegrees((float) yaw);
            float wrappedPitch = Math.max(-90f, Math.min(90f, wrapDegrees((float) pitch)));
            sender.teleport(x, y, z, wrappedYaw, wrappedPitch);
            feedback(sender, "commands.tp.success.coordinates", sender.name(), x, y, z);
        }

        private static float wrapDegrees(float degrees) {
            degrees %= 360f;
            if (degrees >= 180f) degrees -= 360f;
            if (degrees < -180f) degrees += 360f;
            return degrees;
        }

        @Override public List<String> complete(CommandSender sender, String[] args) {
            return args.length == 1 || args.length == 2 ? matching(args[args.length - 1], List.of(sender.name())) : List.of();
        }
    }

    static final class Seed implements Command {
        @Override public String name() { return "seed"; }
        @Override public String usage() { return "commands.seed.usage"; }
        @Override public void execute(CommandSender sender, String[] args) {
            sender.addChatMessage(I18n.translateChat("commands.seed.success", sender.level().randomSeed), false);
        }
    }

    static final class Kill implements Command {
        @Override public String name() { return "kill"; }
        @Override public String usage() { return "commands.kill.usage"; }
        @Override public void execute(CommandSender sender, String[] args) throws CommandException {
            if (args.length > 0) entity(sender, args[0]);
            sender.kill();
            feedback(sender, "commands.kill.successful", sender.name());
        }
        @Override public List<String> complete(CommandSender sender, String[] args) {
            return args.length == 1 ? matching(args[0], List.of(sender.name())) : List.of();
        }
    }

    static final class Say implements Command {
        @Override public String name() { return "say"; }
        @Override public String usage() { return "commands.say.usage"; }
        @Override public int permissionLevel() { return 1; }
        @Override public void execute(CommandSender sender, String[] args) throws CommandException {
            if (args.length == 0 || args[0].isEmpty()) throw new CommandException.WrongUsage(usage());
            sender.addChatMessage(I18n.translateChat("chat.type.announcement", sender.name(), join(args, 0)), false);
        }
    }

    static final class Emote implements Command {
        @Override public String name() { return "me"; }
        @Override public String usage() { return "commands.me.usage"; }
        @Override public int permissionLevel() { return 0; }
        @Override public void execute(CommandSender sender, String[] args) throws CommandException {
            if (args.length <= 0) throw new CommandException.WrongUsage(usage());
            sender.addChatMessage(I18n.translateChat("chat.type.emote", sender.name(), join(args, 0)), false);
        }
    }

    static final class Weather implements Command {
        private final Random random = new Random();
        @Override public String name() { return "weather"; }
        @Override public String usage() { return "commands.weather.usage"; }

        @Override public void execute(CommandSender sender, String[] args) throws CommandException {
            if (args.length < 1 || args.length > 2) throw new CommandException.WrongUsage(usage());
            int duration = (300 + random.nextInt(600)) * 20;
            if (args.length >= 2) duration = parseInt(args[1], 1, 1000000) * 20;
            LevelInfo level = sender.level();
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "clear" -> {
                    level.clearWeatherTime = duration;
                    level.rainTime = 0; level.thunderTime = 0;
                    level.raining = false; level.thundering = false;
                    feedback(sender, "commands.weather.clear");
                }
                case "rain" -> {
                    level.clearWeatherTime = 0;
                    level.rainTime = duration; level.thunderTime = duration;
                    level.raining = true; level.thundering = false;
                    feedback(sender, "commands.weather.rain");
                }
                case "thunder" -> {
                    level.clearWeatherTime = 0;
                    level.rainTime = duration; level.thunderTime = duration;
                    level.raining = true; level.thundering = true;
                    feedback(sender, "commands.weather.thunder");
                }
                default -> throw new CommandException.WrongUsage(usage());
            }
        }

        @Override public List<String> complete(CommandSender sender, String[] args) {
            return args.length == 1 ? last(args, "clear", "rain", "thunder") : List.of();
        }
    }

    static final class SetWorldSpawn implements Command {
        @Override public String name() { return "setworldspawn"; }
        @Override public String usage() { return "commands.setworldspawn.usage"; }

        @Override public void execute(CommandSender sender, String[] args) throws CommandException {
            int x, y, z;
            if (args.length == 0) {
                x = (int) blockX(sender); y = (int) blockY(sender); z = (int) blockZ(sender);
            } else if (args.length == 3) {
                x = (int) Math.floor(parseCoordinate(blockX(sender), args[0], -30000000, 30000000, false));
                y = (int) Math.floor(parseCoordinate(blockY(sender), args[1], -30000000, 30000000, false));
                z = (int) Math.floor(parseCoordinate(blockZ(sender), args[2], -30000000, 30000000, false));
            } else {
                throw new CommandException.WrongUsage(usage());
            }
            LevelInfo level = sender.level();
            level.spawnX = x; level.spawnY = y; level.spawnZ = z;
            feedback(sender, "commands.setworldspawn.success", x, y, z);
        }
    }

    static final class Clear implements Command {
        @Override public String name() { return "clear"; }
        @Override public String usage() { return "commands.clear.usage"; }

        @Override public void execute(CommandSender sender, String[] args) throws CommandException {
            // Item, data, count and tag filters need items by name, which the item registry does not use yet.
            if (args.length > 1) throw new CommandException.WrongUsage(usage());
            if (args.length == 1) player(sender, args[0]);
            int removed = sender.clearInventory();
            if (removed == 0) throw new CommandException("commands.clear.failure", sender.name());
            feedback(sender, "commands.clear.success", sender.name(), removed);
        }

        @Override public List<String> complete(CommandSender sender, String[] args) {
            return args.length == 1 ? matching(args[0], List.of(sender.name())) : List.of();
        }
    }
}
