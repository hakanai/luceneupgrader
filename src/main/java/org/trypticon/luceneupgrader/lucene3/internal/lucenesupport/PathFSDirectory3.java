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
import java.nio.file.attribute.FileTime;
import java.util.*;

import static java.util.Collections.synchronizedSet;

public abstract class PathFSDirectory3 extends Directory {
    public static final int DEFAULT_READ_CHUNK_SIZE = Constants.JRE_IS_64BIT ? Integer.MAX_VALUE : 100 * 1024 * 1024;

    protected final Path directory; // The underlying filesystem directory
    protected final Set<String> staleFiles = synchronizedSet(new HashSet<String>()); // Files written, but not yet sync'ed
    private int chunkSize = DEFAULT_READ_CHUNK_SIZE; // LUCENE-1566

    // returns the canonical version of the directory, creating it if it doesn't exist.
    private static Path getCanonicalPath(Path file) throws IOException {
        return file.toRealPath();
    }

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


    public static PathFSDirectory3 open(Path path) throws IOException {
        return open(path, null);
    }

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


    @Override
    public String[] listAll() throws IOException {
        ensureOpen();
        return listAll(directory);
    }

    @Override
    public boolean fileExists(String name) {
        ensureOpen();
        Path file = directory.resolve(name);
        return Files.exists(file);
    }

    @Override
    public long fileModified(String name) {
        ensureOpen();
        Path file = directory.resolve(name);
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L; // emulating broken behaviour of File.lastModified
        }
    }

    public static long fileModified(File directory, String name) {
        File file = new File(directory, name);
        return file.lastModified();
    }


    @Override
    @Deprecated
    public void touchFile(String name) {
        ensureOpen();
        Path file = directory.resolve(name);
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException e) {
            // ignore it, comment says it isn't used anyway.
        }
    }

    @Override
    public long fileLength(String name) throws IOException {
        ensureOpen();
        return Files.size(directory.resolve(name));
    }

    @Override
    public void deleteFile(String name) throws IOException {
        ensureOpen();
        Files.delete(directory.resolve(name));
    }

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

    @Override
    public synchronized void close() {
        isOpen = false;
    }

    public Path getDirectory() {
        ensureOpen();
        return directory;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "@" + directory + " lockFactory=" + getLockFactory();
    }

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
