package org.trypticon.luceneupgrader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

public class FileUtils {
    public static void insecureRecursiveDelete(Path path) throws IOException {
        try (DirectoryStream<? extends Path> stream = Files.newDirectoryStream(path)) {
            for (Path child : stream) {
                insecureRecursiveDelete(child);
            }
        } catch (NotDirectoryException | NoSuchFileException e) {
            // Fine.
        }
        Files.deleteIfExists(path);
    }
}