package net.minecraft.command;

import java.util.List;

/** One slash command. */
public interface Command {
    String name();
    default List<String> aliases() { return List.of(); }
    /** Translation key of the usage line ("commands.time.usage"). */
    String usage();
    /** 0 for everyone, 2 for cheats (ops); singleplayer grants 2+ only with cheats on. */
    default int permissionLevel() { return 2; }
    void execute(CommandSender sender, String[] args) throws CommandException;
    /** Completions for the last argument, already filtered by what was typed. */
    default List<String> complete(CommandSender sender, String[] args) { return List.of(); }
}
