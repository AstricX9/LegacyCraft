package net.minecraft.command;

/**
 * A command failed; the sender sees the translated message in red
 * (1.8.9 CommandException and its subclasses).
 */
public class CommandException extends Exception {
    private final String key;
    private final Object[] args;

    public CommandException(String key, Object... args) {
        super(key, null, false, false);
        this.key = key;
        this.args = args;
    }

    public String key() { return key; }
    public Object[] args() { return args; }

    /** The command's usage was wrong: shown as "Usage: <usage>". */
    public static final class WrongUsage extends CommandException {
        public WrongUsage(String usageKey, Object... args) { super(usageKey, args); }
    }
}
