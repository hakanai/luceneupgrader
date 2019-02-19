package org.trypticon.luceneupgrader.lucene3.internal.lucenesupport;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.*;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class PathSimpleFSDirectory3 extends PathFSDirectory3 {

    public PathSimpleFSDirectory3(Path path, LockFactory lockFactory) throws IOException {
        super(path, lockFactory);
    }

    @Override
    public IndexInput openInput(String name, int bufferSize) throws IOException {
        ensureOpen();
        final Path path = directory.resolve(name);
        return new SimpleFSIndexInput("SimpleFSIndexInput(path=\"" + path + "\")", path, bufferSize, getReadChunkSize());
    }

    protected static class SimpleFSIndexInput extends BufferedIndexInput {

        protected static class Descriptor implements Closeable {
            final FileChannel channel;
            // remember if the file is open, so that we don't try to close it
            // more than once
            protected volatile boolean isOpen;
            long position;
            final long length;

            public Descriptor(Path file) throws IOException {
                channel = FileChannel.open(file);
                isOpen = true;
                length = channel.size();
            }

            @Override
            public void close() throws IOException {
                if (isOpen) {
                    isOpen = false;
                    channel.close();
                }
            }
        }

        protected final Descriptor file;
        boolean isClone;
        //  LUCENE-1566 - maximum read length on a 32bit JVM to prevent incorrect OOM
        protected final int chunkSize;

        public SimpleFSIndexInput(String resourceDesc, Path path, int bufferSize, int chunkSize) throws IOException {
            super(resourceDesc, bufferSize);
            file = new Descriptor(path);
            this.chunkSize = chunkSize;
        }

        @Override
        protected void readInternal(byte[] b, int offset, int len)
                throws IOException {
            synchronized (file) {
                long position = getFilePointer();
                if (position != file.position) {
                    file.channel.position(position);
                    file.position = position;
                }
                int total = 0;

                try {
                    do {
                        final int readLength;
                        if (total + chunkSize > len) {
                            readLength = len - total;
                        } else {
                            // LUCENE-1566 - work around JVM Bug by breaking very large reads into chunks
                            readLength = chunkSize;
                        }
                        final int i = file.channel.read(ByteBuffer.wrap(b, offset + total, readLength));
                        if (i == -1) {
                            throw new EOFException("read past EOF: " + this);
                        }
                        file.position += i;
                        total += i;
                    } while (total < len);
                } catch (OutOfMemoryError e) {
                    // propagate OOM up and add a hint for 32bit VM Users hitting the bug
                    // with a large chunk size in the fast path.
                    final OutOfMemoryError outOfMemoryError = new OutOfMemoryError(
                            "OutOfMemoryError likely caused by the Sun VM Bug described in "
                                    + "https://issues.apache.org/jira/browse/LUCENE-1566; try calling FSDirectory.setReadChunkSize "
                                    + "with a value smaller than the current chunk size (" + chunkSize + ")");
                    outOfMemoryError.initCause(e);
                    throw outOfMemoryError;
                } catch (IOException ioe) {
                    IOException newIOE = new IOException(ioe.getMessage() + ": " + this);
                    newIOE.initCause(ioe);
                    throw newIOE;
                }
            }
        }

        @Override
        public void close() throws IOException {
            // only close the file if this is not a clone
            if (!isClone) file.close();
        }

        @Override
        protected void seekInternal(long pos) throws IOException {
        }

        @Override
        public long length() {
            return file.length;
        }

        @Override
        public Object clone() {
            SimpleFSIndexInput clone = (SimpleFSIndexInput)super.clone();
            clone.isClone = true;
            return clone;
        }

        @Override
        public void copyBytes(IndexOutput out, long numBytes) throws IOException {
            numBytes -= flushBuffer(out, numBytes);
            // If out is FSIndexOutput, the copy will be optimized
            out.copyBytes(this, numBytes);
        }
    }
}
