package org.trypticon.luceneupgrader.cli;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

/**
 * Main entry class.
 */
public class Main {
    /**
     * Main entry point for calling from a launcher.
     *
     * Because Java wants us to use a static method, which has always been terrible.
     *
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        int result = new Main().run(Arrays.asList(args), System.out, System.err);
        System.exit(result);
    }

    /**
     * Main entry point for command-line interface.
     *
     * @param args the command-line arguments.
     * @param out the output stream.
     * @param err the error stream.
     * @return the result of running the command.
     */
    int run(List<String> args, PrintStream out, PrintStream err) {
        if (args.size() < 1) {
            usage(err);
            return 1;
        }

        String commandName = args.get(0);
        Command command = Commands.findCommand(commandName);
        if (command == null) {
            Commands.unknownCommand(err, commandName);
            return 1;
        }

        return command.run(args.subList(1, args.size()), out, err);
    }

    private static void usage(PrintStream err) {
        err.println("usage: " + Constants.APP_NAME + " <command> <args...>");
        Commands.availableCommands(err);
        err.println("Use " + Constants.APP_NAME + " help <command> for help on a specific command.");
    }
}
