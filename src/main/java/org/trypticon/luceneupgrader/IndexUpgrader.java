package org.trypticon.luceneupgrader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Upgrades an index to a newer version.
 */
public class IndexUpgrader {

    @Nonnull
    private final Path directory;

    @Nonnull
    private final InfoStream infoStream;

    @Nonnull
    private LuceneVersion version;

    public IndexUpgrader(@Nonnull Path directory) throws IOException {
        this(directory, InfoStream.NO_OUTPUT);
    }

    public IndexUpgrader(@Nonnull Path directory, @Nonnull InfoStream infoStream) throws IOException {
        this.directory = directory;
        this.infoStream = infoStream;

        version = new VersionGuesser().guess(directory);
    }

    /**
     * Upgrades to a specific version of Lucene.
     *
     * @param destinationVersion the destination version.
     * @throws IOException if an error occurs reading or writing.
     */
    public void upgradeTo(LuceneVersion destinationVersion) throws IOException {
        while (version.isOlderThan(destinationVersion)) {
            upgradeOneStepTo(versionAfter(version));
        }
    }

    private void upgradeOneStepTo(LuceneVersion version) throws IOException {
        version.createUpgrader(directory, infoStream).upgrade();

        // Sanity check.
        LuceneVersion actualVersion = new VersionGuesser().guess(directory);
        if (actualVersion != version) {
            throw new IllegalStateException("We tried to upgrade from " + this.version + " to " + version +
                                            ", but it didn't actually happen");
        }

        this.version = version;
    }

    private static LuceneVersion versionAfter(LuceneVersion version) {
        // we know this only gets called when we have checked that version is older.
        return LuceneVersion.values()[version.ordinal() + 1];
    }
}
