package org.trypticon.luceneupgrader;

import org.trypticon.luceneupgrader.lucene3.VersionUpgrader3;
import org.trypticon.luceneupgrader.lucene4.VersionUpgrader4;
import org.trypticon.luceneupgrader.lucene5.VersionUpgrader5;

import java.nio.file.Path;

/**
 * Enumeration of versions of Lucene.
 */
public enum LuceneVersion {

    VERSION_1 {
        @Override
        protected VersionUpgrader createUpgrader(Path directory) {
            throw new UnsupportedOperationException("Upgrade from what?");
        }
    },

    VERSION_2 {
        @Override
        protected VersionUpgrader createUpgrader(Path directory) {
            throw new UnsupportedOperationException("TODO");
        }
    },

    VERSION_3 {
        @Override
        protected VersionUpgrader createUpgrader(Path directory) {
            return new VersionUpgrader3(directory);
        }
    },

    VERSION_4 {
        @Override
        protected VersionUpgrader createUpgrader(Path directory) {
            return new VersionUpgrader4(directory);
        }
    },

    VERSION_5 {
        @Override
        protected VersionUpgrader createUpgrader(Path directory) {
            return new VersionUpgrader5(directory);
        }
    };

    public boolean isOlderThan(LuceneVersion version) {
        return compareTo(version) < 0; // because we order them in the enum
    }

    protected abstract VersionUpgrader createUpgrader(Path directory);
}
