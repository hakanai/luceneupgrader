package org.trypticon.luceneupgrader.cli;

import java.io.PrintStream;
import java.util.List;

/**
 * Command to show help on other commands.
 */
class HelpCommand extends Command {
    HelpCommand() {
        super("help", "Prints help for a command", "<command>");
    }

    @Override
    int run(List<String> args, PrintStream out, PrintStream err) {
        if (args.size() != 1) {
            usage(err);
            return 1;
        }
        String commandName = args.get(0);
        Command command = Commands.findCommand(commandName);
        if (command == null) {
            Commands.unknownCommand(err, commandName);
            return 1;
        }

        err.println(Constants.APP_NAME + " " + commandName + " - " + command.getDescription());
        command.usage(err);
        return 0;
    }
}
