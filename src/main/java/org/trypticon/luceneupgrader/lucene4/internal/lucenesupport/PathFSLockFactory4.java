package org.trypticon.luceneupgrader.lucene4.internal.lucenesupport;

import org.apache.lucene.store.FSLockFactory;
import org.apache.lucene.store.LockFactory;

import java.io.File;
import java.nio.file.Path;

/**
 * Clone of {@link FSLockFactory} accepting {@link Path} instead of {@link File}.
 */
public abstract class PathFSLockFactory4 extends LockFactory {

    /**
     * Directory for the lock files.
     */
    protected Path lockDir = null;

    /**
     * Set the lock directory. This method can be only called
     * once to initialize the lock directory. It is used by {@link PathFSDirectory4}
     * to set the lock directory to itself.
     * Subclasses can also use this method to set the directory
     * in the constructor.
     */
    protected final void setLockDir(Path lockDir) {
        if (this.lockDir != null)
            throw new IllegalStateException("You can set the lock directory for this factory only once.");
        this.lockDir = lockDir;
    }

    /**
     * Retrieve the lock directory.
     */
    public Path getLockDir() {
        return lockDir;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "@" + lockDir;
    }

}
