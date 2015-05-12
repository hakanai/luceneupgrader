package org.trypticon.luceneupgrader.lucene3;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexOutput;

import java.io.IOException;

/**
 * Index output adapter to Lucene 3.
 */
class IndexOutputAdapter3 extends IndexOutput {
    private final org.apache.lucene.store.IndexOutput delegate;

    IndexOutputAdapter3(org.apache.lucene.store.IndexOutput delegate) {
        this.delegate = delegate;
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
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
    public long length() throws IOException {
        return delegate.length();
    }

    @Override
    public void writeByte(byte b) throws IOException {
        delegate.writeByte(b);
    }

    @Override
    public void writeBytes(byte[] b, int offset, int length) throws IOException {
        delegate.writeBytes(b, offset, length);
    }
}
