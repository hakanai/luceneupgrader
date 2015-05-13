package org.trypticon.luceneupgrader.lucene4.internal;

import org.trypticon.luceneupgrader.VersionUpgrader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexUpgrader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Version;
import org.trypticon.luceneupgrader.lucene4.internal.lucenesupport.PathFSDirectory4;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Upgrades an index to Lucene 4 format.
 */
public class VersionUpgrader4 implements VersionUpgrader {
    private final Path path;

    public VersionUpgrader4(Path path) {
        this.path = path;
    }

    @Override
    public void upgrade() throws IOException {
        try (Directory directory = PathFSDirectory4.open(path)) {
            IndexUpgrader upgrader = new IndexUpgrader(directory, Version.LUCENE_4_10_4);
            upgrader.upgrade();
        }
    }
}
