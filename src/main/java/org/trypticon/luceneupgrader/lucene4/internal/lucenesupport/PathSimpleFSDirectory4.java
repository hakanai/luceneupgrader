package org.trypticon.luceneupgrader.lucene4.internal.lucenesupport;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.*;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class PathSimpleFSDirectory4 extends PathFSDirectory4 {
    public PathSimpleFSDirectory4(Path path, LockFactory lockFactory) throws IOException {
        super(path, lockFactory);
    }

    public PathSimpleFSDirectory4(Path path) throws IOException {
        super(path, null);
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
        ensureOpen();
        final Path path = directory.resolve(name);
        FileChannel channel = FileChannel.open(path);
        return new SimpleFSIndexInput("SimpleFSIndexInput(path=\"" + path + "\")", channel, context);
    }

    static final class SimpleFSIndexInput extends BufferedIndexInput {
        private static final int CHUNK_SIZE = 8192;

        protected final FileChannel file;
        boolean isClone = false;
        protected final long off;
        protected final long end;

        public SimpleFSIndexInput(String resourceDesc, FileChannel file, IOContext context) throws IOException {
            super(resourceDesc, context);
            this.file = file;
            this.off = 0L;
            this.end = file.size();
        }

        public SimpleFSIndexInput(String resourceDesc, FileChannel file, long off, long length, int bufferSize) {
            super(resourceDesc, bufferSize);
            this.file = file;
            this.off = off;
            this.end = off + length;
            this.isClone = true;
        }

        @Override
        public void close() throws IOException {
            if (!isClone) {
                file.close();
            }
        }

        @Override
        public SimpleFSIndexInput clone() {
            SimpleFSIndexInput clone = (SimpleFSIndexInput)super.clone();
            clone.isClone = true;
            return clone;
        }

        @Override
        public IndexInput slice(String sliceDescription, long offset, long length) throws IOException {
            if (offset < 0 || length < 0 || offset + length > this.length()) {
                throw new IllegalArgumentException("slice() " + sliceDescription + " out of bounds: "  + this);
            }
            return new SimpleFSIndexInput(sliceDescription, file, off + offset, length, getBufferSize());
        }

        @Override
        public final long length() {
            return end - off;
        }

        @Override
        protected void readInternal(byte[] b, int offset, int len)
                throws IOException {
            synchronized (file) {
                long position = off + getFilePointer();
                file.position(position);
                int total = 0;

                if (position + len > end) {
                    throw new EOFException("read past EOF: " + this);
                }

                try {
                    while (total < len) {
                        final int toRead = Math.min(CHUNK_SIZE, len - total);
                        final int i = file.read(ByteBuffer.wrap(b, offset + total, toRead));
                        if (i < 0) { // be defensive here, even though we checked before hand, something could have changed
                            throw new EOFException("read past EOF: " + this + " off: " + offset + " len: " + len + " total: " + total + " chunkLen: " + toRead + " end: " + end);
                        }
                        assert i > 0 : "RandomAccessFile.read with non zero-length toRead must always read at least one byte";
                        total += i;
                    }
                    assert total == len;
                } catch (IOException ioe) {
                    throw new IOException(ioe.getMessage() + ": " + this, ioe);
                }
            }
        }

        @Override
        protected void seekInternal(long position) {
        }

//        boolean isFDValid() throws IOException {
//            return file.getFD().valid();
//        }
    }
}
