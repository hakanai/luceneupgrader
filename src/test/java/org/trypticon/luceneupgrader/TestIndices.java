package org.trypticon.luceneupgrader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Common information about what versions we have test data for.
 */
class TestIndices {

    /**
     * Gets a list of all text index versions we have available.
     *
     * @return all Lucene versions.
     */
    static Collection<String> allVersions() {
        return Arrays.asList("1.2",
                             "1.3",
                             "1.4.1", "1.4.2", "1.4.3",
                             "1.9.1",
                             "2.0.0",
                             "2.1.0",
                             "2.2.0",
                             "2.3.0", "2.3.1", "2.3.2",
                             "2.4.0", "2.4.1",
                             "2.9.0", "2.9.1", "2.9.2", "2.9.3", "2.9.4",
                             "3.0.0", "3.0.1", "3.0.2", "3.0.3",
                             "3.1.0",
                             "3.2.0",
                             "3.3.0",
                             "3.4.0",
                             "3.5.0",
                             "3.6.0", "3.6.1", "3.6.2",
                             "4.0.0",
                             "4.1.0",
                             "4.2.0", "4.2.1",
                             "4.3.0", "4.3.1",
                             "4.4.0",
                             "4.5.0", "4.5.1",
                             "4.6.0", "4.6.1",
                             "4.7.0", "4.7.1", "4.7.2",
                             "4.8.0", "4.8.1",
                             "4.9.0", "4.9.1",
                             "4.10.0", "4.10.1", "4.10.2", "4.10.3", "4.10.4",
                             "5.0.0",
                             "5.1.0",
                             "5.2.0", "5.2.1",
                             "5.3.0", "5.3.1", "5.3.2",
                             "5.4.0", "5.4.1",
                             "5.5.0", "5.5.1", "5.5.2", "5.5.3", "5.5.4", "5.5.5",
                             "6.0.0", "6.0.1",
                             "6.1.0",
                             "6.2.0", "6.2.1",
                             "6.3.0",
                             "6.4.0", "6.4.1", "6.4.2",
                             "6.5.0", "6.5.1",
                             "6.6.0", "6.6.1", "6.6.2", "6.6.3", "6.6.4", "6.6.5",
                             "7.0.0", "7.0.1",
                             "7.1.0",
                             "7.2.0", "7.2.1",
                             "7.3.0", "7.3.1",
                             "7.4.0",
                             "7.5.0",
                             "7.6.0",
                             "7.7.0", "7.7.1", "7.7.2", "7.7.3",
                             "8.0.0",
                             "8.1.0", "8.1.1",
                             "8.2.0",
                             "8.3.0", "8.3.1",
                             "8.4.0", "8.4.1",
                             "8.5.0", "8.5.1", "8.5.2");
    }

    /**
     * Explodes a zipped index.
     *
     * @param version the version to use.
     * @param variant the variant to use.
     * @param destination the destination to put the files.
     * @throws Exception if an error occurs.
     */
    static void explodeZip(String version, String variant, Path destination) throws Exception {
        try (InputStream stream = IndexUpgraderTests.class.getResourceAsStream("/lucene-" + version + "-" + variant + ".zip");
             ZipInputStream zipStream = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path outputFile = destination.resolve(entry.getName());
                    Files.createDirectories(outputFile.getParent());
                    Files.copy(zipStream, outputFile);
                }
            }
        }
    }

}
