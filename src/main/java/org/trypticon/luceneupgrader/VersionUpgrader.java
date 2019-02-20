package org.trypticon.luceneupgrader;

import java.io.IOException;

/**
 * An upgrader which handles an upgrade to a single version.
 */
public interface VersionUpgrader {

    /**
     * Performs the upgrade.
     *
     * @throws IOException if an error occurs performing the upgrade.
     */
    void upgrade() throws IOException;
}
