package org.trypticon.luceneupgrader.lucene3;

import org.trypticon.luceneupgrader.VersionUpgrader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexUpgrader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;
import org.trypticon.luceneupgrader.lucene3.internal.lucenesupport.PathFSDirectory3;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Upgrades an index to Lucene 3 format.
 */
public class VersionUpgrader3 implements VersionUpgrader {
    private final Path path;

    public VersionUpgrader3(Path path) {
        this.path = path;
    }

    @Override
    public void upgrade() throws IOException {
        try (Directory directory = PathFSDirectory3.open(path)) {
            IndexUpgrader upgrader = new IndexUpgrader(directory, Version.LUCENE_36);
            upgrader.upgrade();
        }
    }
}
