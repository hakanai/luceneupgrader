package org.trypticon.luceneupgrader;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexUpgrader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.FSDirectory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexOutput;

import java.io.File;
import java.io.IOException;

public class Test {
    public static void main(String[] args) throws IOException {
        try (Directory directory = FSDirectory.open(new File("/Data/Lucene/TextIndexCopy"))) {
            // copy some test data in
            for (String name : directory.listAll()) {
                directory.deleteFile(name);
            }
            try (Directory original = FSDirectory.open(new File("/Data/Lucene/TextIndex"))) {
                for (String name : original.listAll()) {
                    try (IndexInput source = original.openInput(name);
                         IndexOutput destination = directory.createOutput(name)) {
                        source.copyBytes(destination, source.length());
                    }
                }
            }

            IndexUpgrader upgrader = new IndexUpgrader(directory);
            upgrader.upgrade();
        }
    }
}
