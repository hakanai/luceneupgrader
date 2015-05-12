package org.trypticon.luceneupgrader;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexUpgrader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.FSDirectory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;

import java.io.File;
import java.io.IOException;

public class Test {
    public static void main(String[] args) throws IOException {
        try (Directory directory = FSDirectory.open(new File("/Data/Lucene/TextIndexCopy"))) {
            // copy some test data in
            try (Directory original = FSDirectory.open(new File("/Data/Lucene/TextIndex"))) {
                for (String file : original.listAll()) {
                    try (IndexInput source = original.openInput(file);
                         IndexOutput destination = directory.createOutput(file)) {
                        source.copyBytes(destination, source.length());
                    }
                }
            }

            IndexUpgrader upgrader = new IndexUpgrader(directory, Version.LUCENE_36);
            upgrader.upgrade();
        }
    }
}
