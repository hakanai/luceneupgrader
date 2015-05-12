package org.trypticon.luceneupgrader;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene3.IndexUpgrader3;

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

            new IndexUpgrader3(directory).upgrade();

            try (IndexReader reader = IndexReader.open(directory)) {
                System.out.println(reader.numDocs());
            }
        }
    }
}
