package org.trypticon.luceneupgrader;

import org.trypticon.luceneupgrader.lucene3.VersionUpgrader3;
import org.trypticon.luceneupgrader.lucene4.VersionUpgrader4;
import org.trypticon.luceneupgrader.lucene5.VersionUpgrader5;
import org.trypticon.luceneupgrader.lucene6.VersionUpgrader6;
import org.trypticon.luceneupgrader.lucene7.VersionUpgrader7;
import org.trypticon.luceneupgrader.lucene8.VersionUpgrader8;
import org.trypticon.luceneupgrader.lucene9.VersionUpgrader9;

import javax.annotation.Nonnull;
import java.nio.file.Path;

/**
 * Enumeration of versions of Lucene.
 */
public enum LuceneVersion {

    VERSION_1(1) {
        @Override
        protected VersionUpgrader createUpgrader(@Nonnull Path directory, @Nonnull InfoStream infoStream) {
            throw new UnsupportedOperationException("Upgrade from what?");
        }
    },

    VERSION_2(2) {
        @Override
        protected VersionUpgrader createUpgrader(@Nonnull Path directory, @Nonnull InfoStream infoStream) {
            throw new UnsupportedOperationException("TODO");
        }
    },

    VERSION_3(3) {
        @Override
        protected VersionUpgrader createUpgrader(@Nonnull Path directory, @Nonnull InfoStream infoStream) {
            return new VersionUpgrader3(directory, infoStream);
        }
    },

    VERSION_4(4) {
        @Override
        protected VersionUpgrader createUpgrader(@Nonnull Path directory, @Nonnull InfoStream infoStream) {
            return new VersionUpgrader4(directory, infoStream);
        }
    },

    VERSION_5(5) {
        @Override
        protected VersionUpgrader createUpgrader(@Nonnull Path directory, @Nonnull InfoStream infoStream) {
            return new VersionUpgrader5(directory, infoStream);
        }
    },

    VERSION_6(6) {
        @Override
        protected VersionUpgrader createUpgrader(@Nonnull Path directory, @Nonnull InfoStream infoStream) {
            return new VersionUpgrader6(directory, infoStream);
        }
    },

    VERSION_7(7) {
        @Override
        protected VersionUpgrader createUpgrader(@Nonnull Path directory, @Nonnull InfoStream infoStream) {
            return new VersionUpgrader7(directory, infoStream);
        }
    },

    VERSION_8(8) {
        @Override
        protected VersionUpgrader createUpgrader(@Nonnull Path directory, @Nonnull InfoStream infoStream) {
            return new VersionUpgrader8(directory, infoStream);
        }
    },

    VERSION_9(9) {
        @Override
        protected VersionUpgrader createUpgrader(@Nonnull Path directory, @Nonnull InfoStream infoStream) {
            return new VersionUpgrader9(directory, infoStream);
        }
    };

    private final int number;

    LuceneVersion(int number) {
        this.number = number;
    }

    /**
     * Gets the number for this version.
     *
     * @return the version number.
     */
    public int getNumber() {
        return number;
    }

    /**
     * Tries to find a Lucene version by its number.
     *
     * @param number the version number.
     * @return the Lucene version enum value. Returns {@code null} if not found.
     */
    public static LuceneVersion findByNumber(int number) {
        for (LuceneVersion version : values()) {
            if (version.number == number) {
                return version;
            }
        }
        return null;
    }

    /**
     * Tests whether this version is older than the given version.
     * 
     * @param version the version to compare against.
     * @return {@code true} if this version is older than the given version,
     *         {@code false} otherwise.
     */
    public boolean isOlderThan(LuceneVersion version) {
        return compareTo(version) < 0; // because we order them in the enum
    }

    /**
     * Overridden for each version to create an upgrader suitable for upgrading to that version.
     *
     * @param directory a directory containing the index.
     * @param infoStream an info stream to log to.
     * @return the upgrader.
     */
    protected abstract VersionUpgrader createUpgrader(@Nonnull Path directory, @Nonnull InfoStream infoStream);
}
