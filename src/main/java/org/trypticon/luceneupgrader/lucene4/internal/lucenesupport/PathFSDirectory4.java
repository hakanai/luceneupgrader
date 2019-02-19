package org.trypticon.luceneupgrader.lucene4.internal.lucenesupport;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.*;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.util.Collections.synchronizedSet;

public abstract class PathFSDirectory4 extends BaseDirectory {
    @Deprecated
    public static final int DEFAULT_READ_CHUNK_SIZE = 8192;

    protected final Path directory; // The underlying filesystem directory
    protected final Set<String> staleFiles = synchronizedSet(new HashSet<String>()); // Files written, but not yet sync'ed
    private int chunkSize = DEFAULT_READ_CHUNK_SIZE;

    protected PathFSDirectory4(Path path, LockFactory lockFactory) throws IOException {
        // new ctors use always PathNativeFSLockFactory4 as default:
        if (lockFactory == null) {
            lockFactory = new PathNativeFSLockFactory4();
        }
        directory = path.toRealPath();

        if (Files.exists(directory) && !Files.isDirectory(directory))
            throw new NoSuchDirectoryException("file '" + directory + "' exists but is not a directory");

        setLockFactory(lockFactory);

    }


    public static PathFSDirectory4 open(Path path) throws IOException {
        return open(path, null);
    }

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
    public IndexOutput createOutput(String name, IOContext context) throws IOException {
        ensureOpen();

        ensureCanWrite(name);
        return new FSIndexOutput(name);
    }

    protected void ensureCanWrite(String name) throws IOException {
        Files.deleteIfExists(directory.resolve(name)); // delete existing, if any
    }

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
        return this.getClass().getSimpleName() + "@" + directory + " lockFactory=" + getLockFactory();
    }

    @Deprecated
    public final void setReadChunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        this.chunkSize = chunkSize;
    }

    @Deprecated
    public final int getReadChunkSize() {
        return chunkSize;
    }

    final class FSIndexOutput extends OutputStreamIndexOutput {
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
