package org.trypticon.luceneupgrader.lucene4.internal.lucenesupport;

import org.apache.lucene.store.*;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Clone of {@link PathNIOFSDirectory4} accepting {@link Path} instead of {@link File}.
 */
public class PathNIOFSDirectory4 extends PathFSDirectory4 {
    /** Create a new PathNIOFSDirectory4 for the named location.
     *
     * @param path the path of the directory
     * @param lockFactory the lock factory to use, or null for the default
     * ({@link PathNativeFSLockFactory4});
     * @throws IOException if there is a low-level I/O error
     */
    public PathNIOFSDirectory4(Path path, LockFactory lockFactory) throws IOException {
        super(path, lockFactory);
    }

    /** Create a new PathNIOFSDirectory4 for the named location and {@link PathNativeFSLockFactory4}.
     *
     * @param path the path of the directory
     * @throws IOException if there is a low-level I/O error
     */
    public PathNIOFSDirectory4(Path path) throws IOException {
        super(path, null);
    }

    /** Creates an IndexInput for the file with the given name. */
    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
        ensureOpen();
        Path path = getDirectory().resolve(name);
        FileChannel fc = FileChannel.open(path, StandardOpenOption.READ);
        return new NIOFSIndexInput("NIOFSIndexInput(path=\"" + path + "\")", fc, context);
    }

    /**
     * Reads bytes with {@link FileChannel#read(ByteBuffer, long)}
     */
    static final class NIOFSIndexInput extends BufferedIndexInput {
        /**
         * The maximum chunk size for reads of 16384 bytes.
         */
        private static final int CHUNK_SIZE = 16384;

        /** the file channel we will read from */
        protected final FileChannel channel;
        /** is this instance a clone and hence does not own the file to close it */
        boolean isClone = false;
        /** start offset: non-zero in the slice case */
        protected final long off;
        /** end offset (start+length) */
        protected final long end;

        private ByteBuffer byteBuf; // wraps the buffer for NIO

        public NIOFSIndexInput(String resourceDesc, FileChannel fc, IOContext context) throws IOException {
            super(resourceDesc, context);
            this.channel = fc;
            this.off = 0L;
            this.end = fc.size();
        }

        public NIOFSIndexInput(String resourceDesc, FileChannel fc, long off, long length, int bufferSize) {
            super(resourceDesc, bufferSize);
            this.channel = fc;
            this.off = off;
            this.end = off + length;
            this.isClone = true;
        }

        @Override
        public void close() throws IOException {
            if (!isClone) {
                channel.close();
            }
        }

        @Override
        public NIOFSIndexInput clone() {
            NIOFSIndexInput clone = (NIOFSIndexInput)super.clone();
            clone.isClone = true;
            return clone;
        }

        @Override
        public IndexInput slice(String sliceDescription, long offset, long length) throws IOException {
            if (offset < 0 || length < 0 || offset + length > this.length()) {
                throw new IllegalArgumentException("slice() " + sliceDescription + " out of bounds: "  + this);
            }
            return new NIOFSIndexInput(sliceDescription, channel, off + offset, length, getBufferSize());
        }

        @Override
        public final long length() {
            return end - off;
        }

        @Override
        protected void newBuffer(byte[] newBuffer) {
            super.newBuffer(newBuffer);
            byteBuf = ByteBuffer.wrap(newBuffer);
        }

        @Override
        protected void readInternal(byte[] b, int offset, int len) throws IOException {
            final ByteBuffer bb;

            // Determine the ByteBuffer we should use
            if (b == buffer) {
                // Use our own pre-wrapped byteBuf:
                assert byteBuf != null;
                bb = byteBuf;
                byteBuf.clear().position(offset);
            } else {
                bb = ByteBuffer.wrap(b, offset, len);
            }

            long pos = getFilePointer() + off;

            if (pos + len > end) {
                throw new EOFException("read past EOF: " + this);
            }

            try {
                int readLength = len;
                while (readLength > 0) {
                    final int toRead = Math.min(CHUNK_SIZE, readLength);
                    bb.limit(bb.position() + toRead);
                    assert bb.remaining() == toRead;
                    final int i = channel.read(bb, pos);
                    if (i < 0) { // be defensive here, even though we checked before hand, something could have changed
                        throw new EOFException("read past EOF: " + this + " off: " + offset + " len: " + len + " pos: " + pos + " chunkLen: " + toRead + " end: " + end);
                    }
                    assert i > 0 : "FileChannel.read with non zero-length bb.remaining() must always read at least one byte (FileChannel is in blocking mode, see spec of ReadableByteChannel)";
                    pos += i;
                    readLength -= i;
                }
                assert readLength == 0;
            } catch (IOException ioe) {
                throw new IOException(ioe.getMessage() + ": " + this, ioe);
            }
        }

        @Override
        protected void seekInternal(long pos) throws IOException {}
    }
}
