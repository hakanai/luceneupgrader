package org.trypticon.luceneupgrader.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import org.trypticon.luceneupgrader.IndexUpgrader;
import org.trypticon.luceneupgrader.LuceneVersion;

/**
 * Command to upgrade a text index.
 */
class UpgradeCommand extends Command {
    UpgradeCommand() {
        super("upgrade", "Upgrades a text index", "<index dir> <version>");
    }

    @Override
    int run(List<String> args, PrintStream out, PrintStream err) {
        if (args.size() != 2) {
            usage(err);
            return 1;
        }

        Path directory = Path.of(args.get(0));
        int versionNumber;
        try {
            versionNumber = Integer.parseInt(args.get(1));
        } catch (NumberFormatException e) {
            err.println("Not a number: " + args.get(1));
            return 1;
        }
        LuceneVersion version = LuceneVersion.findByNumber(versionNumber);
        if (version == null) {
            err.println("Not a known Lucene version: " + versionNumber);
            return 1;
        }

        try {
            out.println("Upgrading Lucene index at: " + directory + " to version " + versionNumber + "...");
            new IndexUpgrader(directory).upgradeTo(version);
            out.println("Index upgraded successfully.");
            return 0;
        } catch (IOException e) {
            err.println("Error upgrading Lucene index at: " + directory);
            printErrorSummary(err, e);
            return 1;
        }
    }
}
