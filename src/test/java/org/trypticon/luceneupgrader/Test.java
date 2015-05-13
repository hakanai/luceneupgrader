package org.trypticon.luceneupgrader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class Test {
    public static void main(String[] args) throws IOException {
        Path directory = Paths.get("/Data/Lucene/TextIndexCopy");
        copySampleIndex(directory);

        new IndexUpgrader(directory).upgradeTo(LuceneVersion.VERSION_3);

//        sanityCheck(directory);
    }

    private static void copySampleIndex(Path destination) throws IOException {
        Files.walkFileTree(destination, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        Path original = Paths.get("/Data/Lucene/TextIndex");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(original)) {
            for (Path file : stream) {
                Files.copy(file, destination.resolve(file.getFileName()));
            }
        }
    }

    private static void sanityCheck(Path directory) throws IOException {
        // Sanity check
        Path expectedDirectory = Paths.get("/Data/Lucene/TextIndexReferenceResult");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(expectedDirectory)) {
            for (Path file : stream) {
                try (FileChannel input = FileChannel.open(directory.resolve(file.getFileName()));
                     FileChannel expectedInput = FileChannel.open(file)) {
                    long length = expectedInput.size();
                    if (input.size() != length) {
                        throw new AssertionError();
                    }

                    InputStream inputStream = Channels.newInputStream(input);
                    InputStream expectedInputStream = Channels.newInputStream(expectedInput);
                    int expected;
                    while ((expected = expectedInputStream.read()) >= 0) {
                        int actual = inputStream.read();
                        if (actual != expected) {
                            throw new AssertionError();
                        }
                    }
                }
            }
        }
    }
}
