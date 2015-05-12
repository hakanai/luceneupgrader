package org.trypticon.luceneupgrader.lucene3;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexOutput;

import java.io.IOException;
import java.util.Map;

/**
 * Index input adapter to Lucene 3.
 */
class IndexInputAdapter3 extends IndexInput {
    // not final thanks to clone().
    private IndexInput delegate;

    IndexInputAdapter3(IndexInput delegate) {
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
    public void copyBytes(IndexOutput out, long numBytes) throws IOException {
        delegate.copyBytes(out, numBytes);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public void setModifiedUTF8StringsMode() {
        delegate.setModifiedUTF8StringsMode();
    }

    @Override
    public byte readByte() throws IOException {
        return delegate.readByte();
    }

    @Override
    public void readBytes(byte[] b, int offset, int len) throws IOException {
        delegate.readBytes(b, offset, len);
    }

    @Override
    public void readBytes(byte[] b, int offset, int len, boolean useBuffer) throws IOException {
        delegate.readBytes(b, offset, len, useBuffer);
    }

    @Override
    public short readShort() throws IOException {
        return delegate.readShort();
    }

    @Override
    public int readInt() throws IOException {
        return delegate.readInt();
    }

    @Override
    public int readVInt() throws IOException {
        return delegate.readVInt();
    }

    @Override
    public long readLong() throws IOException {
        return delegate.readLong();
    }

    @Override
    public long readVLong() throws IOException {
        return delegate.readVLong();
    }

    @Override
    public String readString() throws IOException {
        return delegate.readString();
    }

    @Override
    @Deprecated
    public void readChars(char[] buffer, int start, int length) throws IOException {
        delegate.readChars(buffer, start, length);
    }

    @Override
    public Object clone() {
        IndexInputAdapter3 cloned = (IndexInputAdapter3) super.clone();
        cloned.delegate = (IndexInput) cloned.delegate.clone();
        return cloned;
    }

    @Override
    public Map<String, String> readStringStringMap() throws IOException {
        return delegate.readStringStringMap();
    }

    @Override
    @Deprecated
    public void skipChars(int length) throws IOException {
        delegate.skipChars(length);
    }
}
