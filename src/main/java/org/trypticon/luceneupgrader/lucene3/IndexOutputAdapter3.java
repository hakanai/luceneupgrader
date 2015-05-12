package org.trypticon.luceneupgrader.lucene3;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexOutput;

import java.io.IOException;
import java.util.Map;

/**
 * Index output adapter to Lucene 3.
 */
class IndexOutputAdapter3 extends IndexOutput {
    private final IndexOutput delegate;

    IndexOutputAdapter3(IndexOutput delegate) {
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
    public void setLength(long length) throws IOException {
        delegate.setLength(length);
    }

    @Override
    public void writeByte(byte b) throws IOException {
        delegate.writeByte(b);
    }

    @Override
    public void writeBytes(byte[] b, int length) throws IOException {
        delegate.writeBytes(b, length);
    }

    @Override
    public void writeBytes(byte[] b, int offset, int length) throws IOException {
        delegate.writeBytes(b, offset, length);
    }

    @Override
    public void writeInt(int i) throws IOException {
        delegate.writeInt(i);
    }

    @Override
    public void writeLong(long i) throws IOException {
        delegate.writeLong(i);
    }

    @Override
    public void writeString(String s) throws IOException {
        delegate.writeString(s);
    }

    @Override
    public void copyBytes(DataInput input, long numBytes) throws IOException {
        delegate.copyBytes(input, numBytes);
    }

    @Override
    public void writeStringStringMap(Map<String, String> map) throws IOException {
        delegate.writeStringStringMap(map);
    }
}
