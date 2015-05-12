package org.trypticon.luceneupgrader.lucene3;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.*;

import java.io.IOException;
import java.util.Collection;

/**
 * Directory adapter to Lucene 3.
 * Won't make a lot of sense at the moment because I have only done up to 3.
 */
class DirectoryAdapter3 extends Directory {
    private final Directory delegate;

    DirectoryAdapter3(Directory delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] listAll() throws IOException {
        return delegate.listAll();
    }

    @Override
    public boolean fileExists(String name) throws IOException {
        return delegate.fileExists(name);
    }

    @Override
    public void deleteFile(String name) throws IOException {
        delegate.deleteFile(name);
    }

    @Override
    public long fileLength(String name) throws IOException {
        return delegate.fileLength(name);
    }

    @Deprecated
    @Override
    public long fileModified(String name) throws IOException {
        return delegate.fileModified(name);
    }

    @Deprecated
    @Override
    public void touchFile(String name) throws IOException {
        delegate.touchFile(name);
    }

    @Override
    public IndexOutput createOutput(String name) throws IOException {
        return new IndexOutputAdapter3(delegate.createOutput(name));
    }

    @Override
    @Deprecated
    public void sync(String name) throws IOException {
        delegate.sync(name);
    }

    @Override
    public void sync(Collection<String> names) throws IOException {
        delegate.sync(names);
    }

    @Override
    public IndexInput openInput(String name) throws IOException {
        return new IndexInputAdapter3(delegate.openInput(name));
    }

    @Override
    public IndexInput openInput(String name, int bufferSize) throws IOException {
        return delegate.openInput(name, bufferSize);
    }

    @Override
    public Lock makeLock(String name) {
        return new LockAdapter3(delegate.makeLock(name));
    }

    @Override
    public void clearLock(String name) throws IOException {
        delegate.clearLock(name);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void setLockFactory(LockFactory lockFactory) throws IOException {
        delegate.setLockFactory(lockFactory);
    }

    @Override
    public LockFactory getLockFactory() {
        return delegate.getLockFactory();
    }

    @Override
    public String getLockID() {
        return delegate.getLockID();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public void copy(Directory to, String src, String dest) throws IOException {
        delegate.copy(to, src, dest);
    }

    @Deprecated
    public static void copy(Directory src, Directory dest, boolean closeDirSrc) throws IOException {
        Directory.copy(src, dest, closeDirSrc);
    }
}
