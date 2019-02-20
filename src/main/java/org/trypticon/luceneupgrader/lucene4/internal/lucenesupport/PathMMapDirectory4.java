package org.trypticon.luceneupgrader.lucene4.internal.lucenesupport;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.*;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Constants;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Locale;

public class PathMMapDirectory4 extends PathFSDirectory4 {
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
    final int chunkSizePower;

    public PathMMapDirectory4(Path path, LockFactory lockFactory) throws IOException {
        this(path, lockFactory, DEFAULT_MAX_BUFF);
    }

    public PathMMapDirectory4(Path path) throws IOException {
        this(path, null);
    }

    public PathMMapDirectory4(Path path, LockFactory lockFactory, int maxChunkSize) throws IOException {
        super(path, lockFactory);
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("Maximum chunk size for mmap must be >0");
        }
        this.chunkSizePower = 31 - Integer.numberOfLeadingZeros(maxChunkSize);
        assert this.chunkSizePower >= 0 && this.chunkSizePower <= 30;
    }

    public void setUseUnmap(final boolean useUnmapHack) {
        if (useUnmapHack && !UNMAP_SUPPORTED)
            throw new IllegalArgumentException("Unmap hack not supported on this platform!");
        this.useUnmapHack=useUnmapHack;
    }

    public boolean getUseUnmap() {
        return useUnmapHack;
    }

    public final int getMaxChunkSize() {
        return 1 << chunkSizePower;
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
        ensureOpen();
        Path file = getDirectory().resolve(name);
        try (FileChannel c = FileChannel.open(file, StandardOpenOption.READ)) {
            final String resourceDescription = "MMapIndexInput(path=\"" + file.toString() + "\")";
            final boolean useUnmap = getUseUnmap();
            return PathByteBufferIndexInput4.newInstance(resourceDescription,
                    map(resourceDescription, c, 0, c.size()),
                    c.size(), chunkSizePower, useUnmap ? CLEANER : null, useUnmap);
        }
    }

    final ByteBuffer[] map(String resourceDescription, FileChannel fc, long offset, long length) throws IOException {
        if ((length >>> chunkSizePower) >= Integer.MAX_VALUE)
            throw new IllegalArgumentException("RandomAccessFile too big for chunk size: " + resourceDescription);

        final long chunkSize = 1L << chunkSizePower;

        // we always allocate one more buffer, the last one may be a 0 byte one
        final int nrBuffers = (int) (length >>> chunkSizePower) + 1;

        ByteBuffer buffers[] = new ByteBuffer[nrBuffers];

        long bufferStart = 0L;
        for (int bufNr = 0; bufNr < nrBuffers; bufNr++) {
            int bufSize = (int) ( (length > (bufferStart + chunkSize))
                    ? chunkSize
                    : (length - bufferStart)
            );
            try {
                buffers[bufNr] = fc.map(FileChannel.MapMode.READ_ONLY, offset + bufferStart, bufSize);
            } catch (IOException ioe) {
                throw convertMapFailedIOException(ioe, resourceDescription, bufSize);
            }
            bufferStart += bufSize;
        }

        return buffers;
    }

    private IOException convertMapFailedIOException(IOException ioe, String resourceDescription, int bufSize) {
        final String originalMessage;
        final Throwable originalCause;
        if (ioe.getCause() instanceof OutOfMemoryError) {
            // nested OOM confuses users, because its "incorrect", just print a plain message:
            originalMessage = "Map failed";
            originalCause = null;
        } else {
            originalMessage = ioe.getMessage();
            originalCause = ioe.getCause();
        }
        final String moreInfo;
        if (!Constants.JRE_IS_64BIT) {
            moreInfo = "PathMMapDirectory4 should only be used on 64bit platforms, because the address space on 32bit operating systems is too small. ";
        } else if (Constants.WINDOWS) {
            moreInfo = "Windows is unfortunately very limited on virtual address space. If your index size is several hundred Gigabytes, consider changing to Linux. ";
        } else if (Constants.LINUX) {
            moreInfo = "Please review 'ulimit -v', 'ulimit -m' (both should return 'unlimited'), and 'sysctl vm.max_map_count'. ";
        } else {
            moreInfo = "Please review 'ulimit -v', 'ulimit -m' (both should return 'unlimited'). ";
        }
        final IOException newIoe = new IOException(String.format(Locale.ENGLISH,
                "%s: %s [this may be caused by lack of enough unfragmented virtual address space "+
                        "or too restrictive virtual memory limits enforced by the operating system, "+
                        "preventing us to map a chunk of %d bytes. %sMore information: "+
                        "http://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html]",
                originalMessage, resourceDescription, bufSize, moreInfo), originalCause);
        newIoe.setStackTrace(ioe.getStackTrace());
        return newIoe;
    }

    private static final PathByteBufferIndexInput4.BufferCleaner CLEANER = new PathByteBufferIndexInput4.BufferCleaner() {
        @Override
        public void freeBuffer(final PathByteBufferIndexInput4 parent, final ByteBuffer buffer) throws IOException {
            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                    @Override
                    public Void run() throws Exception {
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
                throw new IOException("Unable to unmap the mapped buffer: " + parent.toString(), e.getCause());
            }
        }
    };
}
