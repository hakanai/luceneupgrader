package org.trypticon.luceneupgrader.cli;

import java.io.PrintStream;
import java.util.List;

/**
 * Base class for CLI commands.
 */
abstract class Command {
    private final String name;
    private final String description;
    private final String usage;

    /**
     * Constructs the command.
     *
     * @param name a short name for the command.
     * @param description a description of the command.
     * @param usage usage summary of arguments to the command.
     */
    protected Command(String name, String description, String usage) {
        this.name = name;
        this.description = description;
        this.usage = usage;
    }

    /**
     * Gets the name of the command.
     *
     * @return the name of the command.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets a description of the command.
     *
     * @return a description of the command.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Prints usage info for this command.
     *
     * @param err the error stream.
     */
    void usage(PrintStream err) {
        err.println("usage: " + Constants.APP_NAME + " " + name + " " + usage);
    }

    /**
     * Runs the command.
     *
     * @param args the arguments to the command.
     * @param out the output stream.
     * @param err the error stream.
     * @return the result of the command.
     */
    abstract int run(List<String> args, PrintStream out, PrintStream err);

    /**
     * Prints a summary of the given exception.
     *
     * @param err the error stream.
     * @param e the exception.
     */
    static void printErrorSummary(PrintStream err, Exception e) {
        Throwable temp = e;
        while (temp != null) {
            err.println(temp);
            temp = temp.getCause();
        }
    }
}
