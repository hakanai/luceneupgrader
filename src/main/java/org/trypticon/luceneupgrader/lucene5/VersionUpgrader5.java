package org.trypticon.luceneupgrader.lucene5;

import org.apache.lucene.index.IndexUpgrader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.trypticon.luceneupgrader.VersionUpgrader;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Upgrades an index to Lucene 5 format.
 */
public class VersionUpgrader5 implements VersionUpgrader {
    private final Path path;

    public VersionUpgrader5(Path path) {
        this.path = path;
    }

    @Override
    public void upgrade() throws IOException {
        try (Directory directory = FSDirectory.open(path)) {
            IndexUpgrader upgrader = new IndexUpgrader(directory);
            upgrader.upgrade();
        }
    }
}
