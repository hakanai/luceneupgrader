package org.trypticon.luceneupgrader.lucene3;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Lock;

import java.io.IOException;

/**
 * Lock adapter to Lucene 3.
 */
class LockAdapter3 extends Lock {
    private final Lock delegate;

    LockAdapter3(Lock delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean obtain() throws IOException {
        return delegate.obtain();
    }

    @Override
    public boolean obtain(long lockWaitTimeout) throws IOException {
        return delegate.obtain(lockWaitTimeout);
    }

    @Override
    public void release() throws IOException {
        delegate.release();
    }
}
