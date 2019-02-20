package org.trypticon.luceneupgrader.lucene3.internal.lucenesupport;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.FSLockFactory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.LockFactory;

import java.io.File;
import java.nio.file.Path;

public abstract class PathFSLockFactory3 extends LockFactory {
    protected Path lockDir = null;

    protected final void setLockDir(Path lockDir) {
        if (this.lockDir != null)
            throw new IllegalStateException("You can set the lock directory for this factory only once.");
        this.lockDir = lockDir;
    }

    public Path getLockDir() {
        return lockDir;
    }
}
