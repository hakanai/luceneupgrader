package org.trypticon.luceneupgrader.lucene4.internal.lucenesupport;

import org.apache.lucene.store.*;
import org.apache.lucene.util.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.util.Collections.synchronizedSet;

/**
 * Clone of {@link FSDirectory} accepting {@link Path} instead of {@link File}.
 */
public abstract class PathFSDirectory4 extends BaseDirectory {
    /**
     * Default read chunk size: 8192 bytes (this is the size up to which the JDK
     does not allocate additional arrays while reading/writing)
     @deprecated This constant is no longer used since Lucene 4.5.
     */
    @Deprecated
    public static final int DEFAULT_READ_CHUNK_SIZE = 8192;

    protected final Path directory; // The underlying filesystem directory
    protected final Set<String> staleFiles = synchronizedSet(new HashSet<String>()); // Files written, but not yet sync'ed
    private int chunkSize = DEFAULT_READ_CHUNK_SIZE;

    /** Create a new PathFSDirectory4 for the named location (ctor for subclasses).
     * @param path the path of the directory
     * @param lockFactory the lock factory to use, or null for the default
     * ({@link NativeFSLockFactory});
     * @throws IOException if there is a low-level I/O error
     */
    protected PathFSDirectory4(Path path, LockFactory lockFactory) throws IOException {
        // new ctors use always NativeFSLockFactory as default:
        if (lockFactory == null) {
            lockFactory = new NativeFSLockFactory();
        }
        directory = path.toRealPath();

        if (Files.exists(directory) && !Files.isDirectory(directory))
            throw new NoSuchDirectoryException("file '" + directory + "' exists but is not a directory");

        setLockFactory(lockFactory);

    }

    /** Creates an PathFSDirectory4 instance, trying to pick the
     *  best implementation given the current environment.
     *  The directory returned uses the {@link NativeFSLockFactory}.
     *
     *  <p>Currently this returns {@link MMapDirectory} for most Solaris
     *  and Windows 64-bit JREs, {@link NIOFSDirectory} for other
     *  non-Windows JREs, and {@link SimpleFSDirectory} for other
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
     * {@link MMapDirectory} on 64 bit JVMs.
     *
     * <p>See <a href="#subclasses">above</a> */
    public static PathFSDirectory4 open(Path path) throws IOException {
        return open(path, null);
    }

    /** Just like {@link #open(Path)}, but allows you to
     *  also specify a custom {@link LockFactory}. */
    public static PathFSDirectory4 open(Path path, LockFactory lockFactory) throws IOException {
        if (Constants.JRE_IS_64BIT && MMapDirectory.UNMAP_SUPPORTED) {
            return new PathMMapDirectory4(path, lockFactory);
        } else if (Constants.WINDOWS) {
            return new PathSimpleFSDirectory4(path, lockFactory);
        } else {
            return new PathNIOFSDirectory4(path, lockFactory);
        }
    }

    @Override
    public void setLockFactory(LockFactory lockFactory) throws IOException {
        super.setLockFactory(lockFactory);

        // for filesystem based LockFactory, delete the lockPrefix, if the locks are placed
        // in index dir. If no index dir is given, set ourselves
        if (lockFactory instanceof PathFSLockFactory4) {
            final PathFSLockFactory4 lf = (PathFSLockFactory4) lockFactory;
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
     *  {@link IOException} instead).
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
     * @see #listAll(Path) */
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
    public IndexOutput createOutput(String name, IOContext context) throws IOException {
        ensureOpen();

        ensureCanWrite(name);
        return new FSIndexOutput(name);
    }

    protected void ensureCanWrite(String name) throws IOException {
        Files.deleteIfExists(directory.resolve(name)); // delete existing, if any
    }

    /**
     * Sub classes should call this method on closing an open {@link IndexOutput}, reporting the name of the file
     * that was closed. {@code PathFSDirectory4} needs this information to take care of syncing stale files.
     */
    protected void onIndexOutputClosed(String name) {
        staleFiles.add(name);
    }

    @Override
    public void sync(Collection<String> names) throws IOException {
        ensureOpen();
        Set<String> toSync = new HashSet<>(names);
        toSync.retainAll(staleFiles);

        for (String name : toSync) {
            fsync(name);
        }

        // fsync the directory itsself, but only if there was any file fsynced before
        // (otherwise it can happen that the directory does not yet exist)!
        if (!toSync.isEmpty()) {
            PathIOUtils4.fsync(directory, true);
        }

        staleFiles.removeAll(toSync);
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
        return this.getClass().getSimpleName() + "@" + directory + " lockFactory=" + getLockFactory();
    }

    /**
     * This setting has no effect anymore.
     * @deprecated This is no longer used since Lucene 4.5.
     */
    @Deprecated
    public final void setReadChunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        this.chunkSize = chunkSize;
    }

    /**
     * This setting has no effect anymore.
     * @deprecated This is no longer used since Lucene 4.5.
     */
    @Deprecated
    public final int getReadChunkSize() {
        return chunkSize;
    }

    final class FSIndexOutput extends OutputStreamIndexOutput {
        /**
         * The maximum chunk size is 8192 bytes, because {@link FileOutputStream} mallocs
         * a native buffer outside of stack if the write buffer size is larger.
         */
        static final int CHUNK_SIZE = 8192;

        private final String name;

        public FSIndexOutput(String name) throws IOException {
            super(new FilterOutputStream(Files.newOutputStream(directory.resolve(name))) {
                // This implementation ensures, that we never write more than CHUNK_SIZE bytes:
                @Override
                public void write(byte[] b, int offset, int length) throws IOException {
                    while (length > 0) {
                        final int chunk = Math.min(length, CHUNK_SIZE);
                        out.write(b, offset, chunk);
                        length -= chunk;
                        offset += chunk;
                    }
                }
            }, CHUNK_SIZE);
            this.name = name;
        }

        @Override
        public void close() throws IOException {
            try {
                onIndexOutputClosed(name);
            } finally {
                super.close();
            }
        }
    }

    protected void fsync(String name) throws IOException {
        PathIOUtils4.fsync(directory.resolve(name), false);
    }
}
