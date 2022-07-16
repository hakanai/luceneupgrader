package org.trypticon.luceneupgrader.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import org.trypticon.luceneupgrader.LuceneVersion;
import org.trypticon.luceneupgrader.VersionGuesser;

/**
 * Command to show info about a text index.
 */
class InfoCommand extends Command {
    InfoCommand() {
        super("info", "Gives info about a text index", "<index dir>");
    }

    @Override
    int run(List<String> args, PrintStream out, PrintStream err) {
        if (args.size() != 1) {
            usage(err);
            return 1;
        }
        Path directory = Path.of(args.get(0));
        try {
            LuceneVersion version = new VersionGuesser().guess(directory);
            out.println("Lucene index version: " + version.getNumber());
            return 0;
        } catch (IOException e) {
            err.println("Error getting info for Lucene index at: " + directory);
            printErrorSummary(err, e);
            return 1;
        }
    }
}
