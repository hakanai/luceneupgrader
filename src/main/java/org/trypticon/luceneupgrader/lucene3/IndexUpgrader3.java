package org.trypticon.luceneupgrader.lucene3;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexUpgrader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;

import java.io.IOException;

/**
 * Upgrades an index to Lucene 3 format.
 */
public class IndexUpgrader3 {
    private final IndexUpgrader delegate;

    public IndexUpgrader3(Directory directory) {
        delegate = new IndexUpgrader(new DirectoryAdapter3(directory));
    }

    public void upgrade() throws IOException {
        delegate.upgrade();
    }
}
