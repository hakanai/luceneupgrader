package org.trypticon.luceneupgrader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Tests for {@link IndexUpgrader} against indices created against various versions of Lucene.
 */
@RunWith(Parameterized.class)
public class IndexUpgraderTests {
    private final String version;
    private Path temp;

    public IndexUpgraderTests(String version) {
        this.version = version;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        List<Object[]> data = new LinkedList<>();
        for (String version : TestIndices.allVersions()) {
            if ("1.2".equals(version) || "1.3".equals(version)) {
                //TODO: Version 1 not supported yet.
                continue;
            }

            data.add(new Object[] { version });
        }
        return data;
    }

    @Before
    public void setUp() throws Exception {
        temp = Files.createTempDirectory("test");
    }

    @After
    public void tearDown() throws Exception {
        Utils.recursiveDeleteIfExists(temp);
    }

    @Test
    public void testEmpty() throws Exception {
        TestIndices.explodeZip(version, "empty", temp);
        IndexUpgrader upgrader = new IndexUpgrader(temp);
        upgrader.upgradeTo(LuceneVersion.VERSION_8);
    }

    @Test
    public void testNonEmpty() throws Exception {
        TestIndices.explodeZip(version, "nonempty", temp);
        IndexUpgrader upgrader = new IndexUpgrader(temp);
        upgrader.upgradeTo(LuceneVersion.VERSION_8);
    }
}
