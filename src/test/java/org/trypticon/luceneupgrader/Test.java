package org.trypticon.luceneupgrader;

import org.trypticon.luceneupgrader.lucene3.IndexUpgrader3;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.FSDirectory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexOutput;

import java.io.File;
import java.io.IOException;

public class Test {
    public static void main(String[] args) throws IOException {
        try (Directory directory = FSDirectory.open(new File("/Data/Lucene/TextIndexCopy"))) {
            // copy an old v2 index
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

            // Sanity check
            try (Directory expectedDirectory = FSDirectory.open(new File("/Data/Lucene/TextIndexReferenceResult"))) {
                for (String name : expectedDirectory.listAll()) {
                    try (IndexInput input = directory.openInput(name);
                         IndexInput expectedInput = expectedDirectory.openInput(name)) {
                        long length = expectedInput.length();
                        if (input.length() != length) {
                            throw new AssertionError();
                        }

                        while (input.getFilePointer() < length) {
                            byte expected = expectedInput.readByte();
                            byte actual = input.readByte();
                            if (actual != expected) {
                                throw new AssertionError();
                            }
                        }
                    }
                }
            }
        }
    }
}
