package org.trypticon.luceneupgrader.lucene3;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexInput;

import java.io.IOException;

/**
 * Index input adapter to Lucene 3.
 */
class IndexInputAdapter3 extends IndexInput {
    private final org.apache.lucene.store.IndexInput delegate;

    IndexInputAdapter3(org.apache.lucene.store.IndexInput delegate) {
        super(delegate.toString());
        this.delegate = delegate;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public long getFilePointer() {
        return delegate.getFilePointer();
    }

    @Override
    public void seek(long pos) throws IOException {
        delegate.seek(pos);
    }

    @Override
    public long length() {
        return delegate.length();
    }

    @Override
    public byte readByte() throws IOException {
        return delegate.readByte();
    }

    @Override
    public void readBytes(byte[] b, int offset, int len) throws IOException {
        delegate.readBytes(b, offset, len);
    }
}
