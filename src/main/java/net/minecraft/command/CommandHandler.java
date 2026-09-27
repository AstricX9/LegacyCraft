package net.minecraft.command;

import net.minecraft.client.gui.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs slash commands the way 1.8.9's command handler does (HYPOTHESIS,
 * recalled behaviour): the text after "/" is split on single spaces, the
 * first word picks the command (case-insensitive), and failures come back
 * as red chat lines: unknown command, no permission, "Usage: ...", or the
 * command's own error. Singleplayer without cheats may only use the
 * everyone-level commands, plus /seed.
 */
public final class CommandHandler {
    public static final String RED = "§c";
    private final Map<String, Command> commands = new TreeMap<>();
    private final Map<String, Command> byAlias = new TreeMap<>();

    public CommandHandler() {
        for (Command command : Commands.all(this)) register(command);
    }

    public void register(Command command) {
        commands.put(command.name(), command);
        byAlias.put(command.name(), command);
        for (String alias : command.aliases()) byAlias.put(alias, command);
    }

    public Command get(String name) { return byAlias.get(name.toLowerCase(Locale.ROOT)); }

    /** Commands the sender may use, sorted by name (what /help lists). */
    public List<Command> usable(CommandSender sender) {
        List<Command> result = new ArrayList<>();
        for (Command command : commands.values()) if (canUse(sender, command)) result.add(command);
        return result;
    }

    public static boolean canUse(CommandSender sender, Command command) {
        if (command.name().equals("seed")) return true;
        return command.permissionLevel() == 0 || sender.commandsAllowed();
    }

    /** Runs a command line (with or without its leading "/"); returns whether it succeeded. */
    public boolean execute(CommandSender sender, String line) {
        String text = line.trim();
        if (text.startsWith("/")) text = text.substring(1);
        String[] words = text.split(" ");
        String name = words[0];
        String[] args = new String[words.length - 1];
        System.arraycopy(words, 1, args, 0, args.length);
        Command command = get(name);
        if (command == null) {
            error(sender, "commands.generic.notFound");
            return false;
        }
        if (!canUse(sender, command)) {
            error(sender, "commands.generic.permission");
            return false;
        }
        try {
            command.execute(sender, args);
            return true;
        } catch (CommandException.WrongUsage usage) {
            sender.addChatMessage(RED + I18n.translateChat("commands.generic.usage",
                I18n.translateChat(usage.key(), usage.args())), false);
        } catch (CommandException failure) {
            error(sender, failure.key(), failure.args());
        } catch (RuntimeException unexpected) {
            error(sender, "commands.generic.exception");
            unexpected.printStackTrace();
        }
        return false;
    }

    private static void error(CommandSender sender, String key, Object... args) {
        sender.addChatMessage(RED + I18n.translateChat(key, args), false);
    }

    /**
     * Tab completions for the word before the cursor: command names (with
     * their "/") for the first word, the command's own suggestions after
     * that, and player names in plain chat.
     */
    public List<String> complete(CommandSender sender, String beforeCursor) {
        if (!beforeCursor.startsWith("/")) {
            String word = beforeCursor.substring(beforeCursor.lastIndexOf(' ') + 1);
            return Commands.matching(word, List.of(sender.name()));
        }
        String text = beforeCursor.substring(1);
        String[] words = text.split(" ", -1);
        if (words.length == 1) {
            List<String> names = new ArrayList<>();
            for (Command command : usable(sender)) {
                names.add(command.name());
                names.addAll(command.aliases());
            }
            names.sort(null);
            List<String> result = new ArrayList<>();
            for (String name : Commands.matching(words[0], names)) result.add("/" + name);
            return result;
        }
        Command command = get(words[0]);
        if (command == null || !canUse(sender, command)) return List.of();
        String[] args = new String[words.length - 1];
        System.arraycopy(words, 1, args, 0, args.length);
        return command.complete(sender, args);
    }
}
