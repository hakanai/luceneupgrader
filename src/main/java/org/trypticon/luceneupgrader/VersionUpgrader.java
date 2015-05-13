package org.trypticon.luceneupgrader;

import java.io.IOException;

/**
 * An upgrader which handles an upgrade to a single version.
 */
public interface VersionUpgrader {
    void upgrade() throws IOException;
}
