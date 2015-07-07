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
        return Arrays.asList("2.0.0",
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
                             "5.2.0", "5.2.1");
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
