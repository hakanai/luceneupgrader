package org.trypticon.luceneupgrader.lucene3.internal.lucenesupport;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.*;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ThreadInterruptedException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static java.util.Collections.synchronizedSet;

/**
 * Clone of {@link FSDirectory} accepting {@link Path} instead of {@link File}.
 */
public class PathFSDirectory3 extends Directory {
    /**
     * Default read chunk size.  This is a conditional default: on 32bit JVMs, it defaults to 100 MB.  On 64bit JVMs, it's
     * <code>Integer.MAX_VALUE</code>.
     *
     *
     */
    public static final int DEFAULT_READ_CHUNK_SIZE = Constants.JRE_IS_64BIT ? Integer.MAX_VALUE : 100 * 1024 * 1024;

    protected final Path directory; // The underlying filesystem directory
    protected final Set<String> staleFiles = synchronizedSet(new HashSet<String>()); // Files written, but not yet sync'ed
    private int chunkSize = DEFAULT_READ_CHUNK_SIZE; // LUCENE-1566

    // returns the canonical version of the directory, creating it if it doesn't exist.
    private static Path getCanonicalPath(Path file) throws IOException {
        return file.toRealPath();
    }

    /** Create a new PathFSDirectory3 for the named location (ctor for subclasses).
     * @param path the path of the directory
     * @param lockFactory the lock factory to use, or null for the default
     * ({@code NativeFSLockFactory});
     * @throws IOException
     */
    protected PathFSDirectory3(Path path, LockFactory lockFactory) throws IOException {
        // new ctors use always NativeFSLockFactory as default:
        if (lockFactory == null) {
            lockFactory = new PathNativeFSLockFactory3();
        }
        directory = getCanonicalPath(path);

        if (Files.exists(directory) && !Files.isDirectory(directory))
            throw new NoSuchDirectoryException("file '" + directory + "' exists but is not a directory");

        setLockFactory(lockFactory);
    }

    /** Creates an PathFSDirectory3 instance, trying to pick the
     *  best implementation given the current environment.
     *  The directory returned uses the {@code NativeFSLockFactory}.
     *
     *  <p>Currently this returns {@code PathMMapDirectory3} for most Solaris
     *  and Windows 64-bit JREs, {@code PathNIOFSDirectory3} for other
     *  non-Windows JREs, and {@code PathSimpleFSDirectory3} for other
     *  JREs on Windows. It is highly recommended that you consult the
     *  implementation's documentation for your platform before
     *  using this method.
     *
     * <p><b>NOTE</b>: this method may suddenly change which
     * implementation is returned from release to release, in
     * the event that higher performance defaults become
     * possible; if the precise implementation is important to
     * your application, please instantiate it directly,
     * instead. For optimal performance you should consider using
     * {@code MMapDirectory} on 64 bit JVMs.
     *
     * <p>See <a href="#subclasses">above</a> */
    public static PathFSDirectory3 open(Path path) throws IOException {
        return open(path, null);
    }

    /** Just like {@code #open(File)}, but allows you to
     *  also specify a custom {@code LockFactory}. */
    public static PathFSDirectory3 open(Path path, LockFactory lockFactory) throws IOException {
        if ((Constants.WINDOWS || Constants.SUN_OS || Constants.LINUX)
                && Constants.JRE_IS_64BIT && MMapDirectory.UNMAP_SUPPORTED) {
            return new PathMMapDirectory3(path, lockFactory);
        } else if (Constants.WINDOWS) {
            return new PathSimpleFSDirectory3(path, lockFactory);
        } else {
            return new PathNIOFSDirectory3(path, lockFactory);
        }
    }

    @Override
    public void setLockFactory(LockFactory lockFactory) throws IOException {
        super.setLockFactory(lockFactory);

        // for filesystem based LockFactory, delete the lockPrefix, if the locks are placed
        // in index dir. If no index dir is given, set ourselves
        if (lockFactory instanceof PathFSLockFactory3) {
            final PathFSLockFactory3 lf = (PathFSLockFactory3) lockFactory;
            final Path dir = lf.getLockDir();
            // if the lock factory has no lockDir set, use the this directory as lockDir
            if (dir == null) {
                lf.setLockDir(directory);
                lf.setLockPrefix(null);
            } else if (dir.toRealPath().equals(directory.toRealPath())) {
                lf.setLockPrefix(null);
            }
        }

    }

    /** Lists all files (not subdirectories) in the
     *  directory.  This method never returns null (throws
     *  {@code IOException} instead).
     *
     *  @throws NoSuchDirectoryException if the directory
     *   does not exist, or does exist but is not a
     *   directory.
     *  @throws IOException if list() returns null */
    public static String[] listAll(Path dir) throws IOException {
        List<String> entries = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return !Files.isDirectory(entry); // filter out entries that are definitely directories.
            }
        })) {
            for (Path path : stream) {
                entries.add(path.getFileName().toString());
            }
        }

        return entries.toArray(new String[entries.size()]);
    }

    /** Lists all files (not subdirectories) in the
     * directory.
     * */
    @Override
    public String[] listAll() throws IOException {
        ensureOpen();
        return listAll(directory);
    }

    /** Returns true iff a file with the given name exists. */
    @Override
    public boolean fileExists(String name) {
        ensureOpen();
        Path file = directory.resolve(name);
        return Files.exists(file);
    }

    /** Returns the length in bytes of a file in the directory. */
    @Override
    public long fileLength(String name) throws IOException {
        ensureOpen();
        return Files.size(directory.resolve(name));
    }

    /** Removes an existing file in the directory. */
    @Override
    public void deleteFile(String name) throws IOException {
        ensureOpen();
        Files.delete(directory.resolve(name));
    }

    /** Creates an IndexOutput for the file with the given name. */
    @Override
    public IndexOutput createOutput(String name) throws IOException {
        ensureOpen();

        ensureCanWrite(name);
        return new PathFSIndexOutput3(this, name);
    }

    protected void ensureCanWrite(String name) throws IOException {
        Files.deleteIfExists(directory.resolve(name)); // delete existing, if any
    }

    protected void onIndexOutputClosed(PathFSIndexOutput3 io) {
        staleFiles.add(io.name);
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public void sync(String name) throws IOException {
        sync(Collections.singleton(name));
    }

    @Override
    public void sync(Collection<String> names) throws IOException {
        ensureOpen();
        Set<String> toSync = new HashSet<>(names);
        toSync.retainAll(staleFiles);

        for (String name : toSync)
            fsync(name);

        staleFiles.removeAll(toSync);
    }

    // Inherit javadoc
    @Override
    public IndexInput openInput(String name) throws IOException {
        ensureOpen();
        return openInput(name, BufferedIndexInput.BUFFER_SIZE);
    }

    @Override
    public String getLockID() {
        ensureOpen();
        String dirName;                               // name to be hashed
        try {
            dirName = directory.toRealPath().toString();
        } catch (IOException e) {
            throw new RuntimeException(e.toString(), e);
        }

        int digest = 0;
        for(int charIDX=0;charIDX<dirName.length();charIDX++) {
            final char ch = dirName.charAt(charIDX);
            digest = 31 * digest + ch;
        }
        return "lucene-" + Integer.toHexString(digest);
    }

    /** Closes the store to future operations. */
    @Override
    public synchronized void close() {
        isOpen = false;
    }

    /** @return the underlying filesystem directory */
    public Path getDirectory() {
        ensureOpen();
        return directory;
    }

    /** For debug output. */
    @Override
    public String toString() {
        return this.getClass().getName() + "@" + directory + " lockFactory=" + getLockFactory();
    }

    /**
     * The maximum number of bytes to read at once from the
     * underlying file during {@code IndexInput#readBytes}.
     *
     */
    public final int getReadChunkSize() {
        // LUCENE-1566
        return chunkSize;
    }

    protected static class PathFSIndexOutput3 extends BufferedIndexOutput {
        private final PathFSDirectory3 parent;
        private final String name;
        private final FileChannel file;
        private volatile boolean isOpen; // remember if the file is open, so that we don't try to close it more than once

        public PathFSIndexOutput3(PathFSDirectory3 parent, String name) throws IOException {
            this.parent = parent;
            this.name = name;
            file = FileChannel.open(parent.directory.resolve(name), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
            isOpen = true;
        }

        /** output methods: */
        @Override
        public void flushBuffer(byte[] b, int offset, int size) throws IOException {
            file.write(ByteBuffer.wrap(b, offset, size));
        }

        @Override
        public void close() throws IOException {
            parent.onIndexOutputClosed(this);
            // only close the file if it has not been closed yet
            if (isOpen) {
                boolean success = false;
                try {
                    super.close();
                    success = true;
                } finally {
                    isOpen = false;
                    if (!success) {
                        try {
                            file.close();
                        } catch (Throwable t) {
                            // Suppress so we don't mask original exception
                        }
                    } else {
                        file.close();
                    }
                }
            }
        }

        /** Random-access methods */
        @Override
        public void seek(long pos) throws IOException {
            super.seek(pos);
            file.position(pos);
        }

        @Override
        public long length() throws IOException {
            return file.size();
        }

        @Override
        public void setLength(long length) throws IOException {
            file.truncate(length);
        }
    }

    protected void fsync(String name) throws IOException {
        Path fullFile = directory.resolve(name);
        boolean success = false;
        int retryCount = 0;
        IOException exc = null;
        while (!success && retryCount < 5) {
            retryCount++;
            FileChannel file = null;
            try {
                try {
                    file = FileChannel.open(fullFile, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
                    file.force(true);
                    success = true;
                } finally {
                    if (file != null)
                        file.close();
                }
            } catch (IOException ioe) {
                if (exc == null)
                    exc = ioe;
                try {
                    // Pause 5 msec
                    Thread.sleep(5);
                } catch (InterruptedException ie) {
                    throw new ThreadInterruptedException(ie);
                }
            }
        }
        if (!success)
            // Throw original exception
            throw exc;
    }
}
