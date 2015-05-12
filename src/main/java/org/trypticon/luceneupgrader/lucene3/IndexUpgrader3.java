package org.trypticon.luceneupgrader.lucene3;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexUpgrader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;

import java.io.IOException;

/**
 * Upgrades an index to Lucene 3 format.
 */
public class IndexUpgrader3 {
    private final IndexUpgrader delegate;

    public IndexUpgrader3(Directory directory) {
        delegate = new IndexUpgrader(new DirectoryAdapter3(directory), Version.LUCENE_36);
    }

    public void upgrade() throws IOException {
        delegate.upgrade();
    }
}
