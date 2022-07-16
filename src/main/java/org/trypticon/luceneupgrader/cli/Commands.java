package org.trypticon.luceneupgrader.cli;

import java.io.PrintStream;

/**
 * Utilities for finding and listing commands.
 */
public class Commands {
    private static final Command[] commands = {
            new HelpCommand(),
            new InfoCommand(),
            new UpgradeCommand(),
    };

    /**
     * Finds a command by name.
     *
     * @param name the command name.
     * @return the command. Returns {@code null} if no command with that name was found.
     */
    static Command findCommand(String name) {
        for (Command command : commands) {
            if (command.getName().equals(name)) {
                return command;
            }
        }
        return null;
    }

    /**
     * Prints a message for an unknown command.
     *
     * @param err the error stream.
     * @param command the command name which was unknown.
     */
    static void unknownCommand(PrintStream err, String command) {
        err.println("Unknown command: " + command);
        availableCommands(err);
    }

    /**
     * Prints a message listing the available commands.
     *
     * @param err the error stream.
     */
    static void availableCommands(PrintStream err) {
        err.println("Available commands:");
        for (Command command : commands) {
            err.println("  " + command.getName());
        }
    }
}
