package org.trypticon.luceneupgrader.lucene3.internal.lucenesupport;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.LockFactory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.MMapDirectory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.WeakIdentityMap;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;

public class PathMMapDirectory3 extends PathFSDirectory3 {
    public static final boolean UNMAP_SUPPORTED;
    static {
        boolean v;
        try {
            Class.forName("sun.misc.Cleaner");
            Class.forName("java.nio.DirectByteBuffer")
                    .getMethod("cleaner");
            v = true;
        } catch (Exception e) {
            v = false;
        }
        UNMAP_SUPPORTED = v;
    }

    private boolean useUnmapHack = UNMAP_SUPPORTED;
    public static final int DEFAULT_MAX_BUFF = Constants.JRE_IS_64BIT ? (1 << 30) : (1 << 28);
    private int chunkSizePower;

    public PathMMapDirectory3(Path path, LockFactory lockFactory) throws IOException {
        super(path, lockFactory);
        setMaxChunkSize(DEFAULT_MAX_BUFF);
    }

    final void cleanMapping(final ByteBuffer buffer) throws IOException {
        if (useUnmapHack) {
            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                    public Object run() throws Exception {
                        final Method getCleanerMethod = buffer.getClass()
                                .getMethod("cleaner");
                        getCleanerMethod.setAccessible(true);
                        final Object cleaner = getCleanerMethod.invoke(buffer);
                        if (cleaner != null) {
                            cleaner.getClass().getMethod("clean")
                                    .invoke(cleaner);
                        }
                        return null;
                    }
                });
            } catch (PrivilegedActionException e) {
                final IOException ioe = new IOException("unable to unmap the mapped buffer");
                ioe.initCause(e.getCause());
                throw ioe;
            }
        }
    }

    public final void setMaxChunkSize(final int maxChunkSize) {
        if (maxChunkSize <= 0)
            throw new IllegalArgumentException("Maximum chunk size for mmap must be >0");
        //System.out.println("Requested chunk size: "+maxChunkSize);
        this.chunkSizePower = 31 - Integer.numberOfLeadingZeros(maxChunkSize);
        assert this.chunkSizePower >= 0 && this.chunkSizePower <= 30;
        //System.out.println("Got chunk size: "+getMaxChunkSize());
    }

    @Override
    public IndexInput openInput(String name, int bufferSize) throws IOException {
        ensureOpen();
        Path f = getDirectory().resolve(name);
        try (FileChannel channel = FileChannel.open(f)) {
            return new MMapIndexInput("MMapIndexInput(path=\"" + f + "\")", channel, chunkSizePower);
        }
    }

    // Because Java's ByteBuffer uses an int to address the
    // values, it's necessary to access a file >
    // Integer.MAX_VALUE in size using multiple byte buffers.
    private final class MMapIndexInput extends IndexInput {

        private ByteBuffer[] buffers;

        private final long length, chunkSizeMask, chunkSize;
        private final int chunkSizePower;

        private int curBufIndex;

        private ByteBuffer curBuf; // redundant for speed: buffers[curBufIndex]

        private boolean isClone = false;
        private final WeakIdentityMap<MMapIndexInput,Boolean> clones = WeakIdentityMap.newConcurrentHashMap();

        MMapIndexInput(String resourceDescription, FileChannel channel, int chunkSizePower) throws IOException {
            super(resourceDescription);
            this.length = channel.size();
            this.chunkSizePower = chunkSizePower;
            this.chunkSize = 1L << chunkSizePower;
            this.chunkSizeMask = chunkSize - 1L;

            if (chunkSizePower < 0 || chunkSizePower > 30)
                throw new IllegalArgumentException("Invalid chunkSizePower used for ByteBuffer size: " + chunkSizePower);

            if ((length >>> chunkSizePower) >= Integer.MAX_VALUE)
                throw new IllegalArgumentException("RandomAccessFile too big for chunk size: " + channel.toString());

            // we always allocate one more buffer, the last one may be a 0 byte one
            final int nrBuffers = (int) (length >>> chunkSizePower) + 1;

            //System.out.println("length="+length+", chunkSizePower=" + chunkSizePower + ", chunkSizeMask=" + chunkSizeMask + ", nrBuffers=" + nrBuffers);

            this.buffers = new ByteBuffer[nrBuffers];

            long bufferStart = 0L;
            for (int bufNr = 0; bufNr < nrBuffers; bufNr++) {
                int bufSize = (int) ( (length > (bufferStart + chunkSize))
                        ? chunkSize
                        : (length - bufferStart)
                );
                this.buffers[bufNr] = channel.map(FileChannel.MapMode.READ_ONLY, bufferStart, bufSize);
                bufferStart += bufSize;
            }
            seek(0L);
        }

        @Override
        public byte readByte() throws IOException {
            try {
                return curBuf.get();
            } catch (BufferUnderflowException e) {
                do {
                    curBufIndex++;
                    if (curBufIndex >= buffers.length) {
                        throw new EOFException("read past EOF: " + this);
                    }
                    curBuf = buffers[curBufIndex];
                    curBuf.position(0);
                } while (!curBuf.hasRemaining());
                return curBuf.get();
            } catch (NullPointerException npe) {
                throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
            }
        }

        @Override
        public void readBytes(byte[] b, int offset, int len) throws IOException {
            try {
                curBuf.get(b, offset, len);
            } catch (BufferUnderflowException e) {
                int curAvail = curBuf.remaining();
                while (len > curAvail) {
                    curBuf.get(b, offset, curAvail);
                    len -= curAvail;
                    offset += curAvail;
                    curBufIndex++;
                    if (curBufIndex >= buffers.length) {
                        throw new EOFException("read past EOF: " + this);
                    }
                    curBuf = buffers[curBufIndex];
                    curBuf.position(0);
                    curAvail = curBuf.remaining();
                }
                curBuf.get(b, offset, len);
            } catch (NullPointerException npe) {
                throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
            }
        }

        @Override
        public int readInt() throws IOException {
            try {
                return curBuf.getInt();
            } catch (BufferUnderflowException e) {
                return super.readInt();
            } catch (NullPointerException npe) {
                throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
            }
        }

        @Override
        public long readLong() throws IOException {
            try {
                return curBuf.getLong();
            } catch (BufferUnderflowException e) {
                return super.readLong();
            } catch (NullPointerException npe) {
                throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
            }
        }

        @Override
        public long getFilePointer() {
            try {
                return (((long) curBufIndex) << chunkSizePower) + curBuf.position();
            } catch (NullPointerException npe) {
                throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
            }
        }

        @Override
        public void seek(long pos) throws IOException {
            // we use >> here to preserve negative, so we will catch AIOOBE:
            final int bi = (int) (pos >> chunkSizePower);
            try {
                final ByteBuffer b = buffers[bi];
                b.position((int) (pos & chunkSizeMask));
                // write values, on exception all is unchanged
                this.curBufIndex = bi;
                this.curBuf = b;
            } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException aioobe) {
                if (pos < 0L) {
                    throw new IllegalArgumentException("Seeking to negative position: " + this);
                }
                throw new EOFException("seek past EOF: " + this);
            } catch (NullPointerException npe) {
                throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
            }
        }

        @Override
        public long length() {
            return length;
        }

        @Override
        public Object clone() {
            if (buffers == null) {
                throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
            }
            final MMapIndexInput clone = (MMapIndexInput)super.clone();
            clone.isClone = true;
            // we keep clone.clones, so it shares the same map with original and we have no additional cost on clones
            assert clone.clones == this.clones;
            clone.buffers = new ByteBuffer[buffers.length];
            for (int bufNr = 0; bufNr < buffers.length; bufNr++) {
                clone.buffers[bufNr] = buffers[bufNr].duplicate();
            }
            try {
                clone.seek(getFilePointer());
            } catch(IOException ioe) {
                throw new RuntimeException("Should never happen: " + this, ioe);
            }

            // register the new clone in our clone list to clean it up on closing:
            this.clones.put(clone, Boolean.TRUE);

            return clone;
        }

        private void unsetBuffers() {
            buffers = null;
            curBuf = null;
            curBufIndex = 0;
        }

        @Override
        public void close() throws IOException {
            try {
                if (isClone || buffers == null) return;

                // make local copy, then un-set early
                final ByteBuffer[] bufs = buffers;
                unsetBuffers();

                // for extra safety unset also all clones' buffers:
                for (Iterator<MMapIndexInput> it = this.clones.keyIterator(); it.hasNext();) {
                    final MMapIndexInput clone = it.next();
                    assert clone.isClone;
                    clone.unsetBuffers();
                }
                this.clones.clear();

                for (final ByteBuffer b : bufs) {
                    cleanMapping(b);
                }
            } finally {
                unsetBuffers();
            }
        }
    }
}
