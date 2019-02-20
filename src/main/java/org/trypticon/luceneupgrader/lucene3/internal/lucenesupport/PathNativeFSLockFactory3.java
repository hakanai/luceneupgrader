package org.trypticon.luceneupgrader.lucene3.internal.lucenesupport;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Lock;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.NativeFSLockFactory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PathNativeFSLockFactory3 extends PathFSLockFactory3 {

    public PathNativeFSLockFactory3() throws IOException {
        this(null);
    }

    public PathNativeFSLockFactory3(Path lockDir) {
        setLockDir(lockDir);
    }

    @Override
    public synchronized Lock makeLock(String lockName) {
        if (lockPrefix != null)
            lockName = lockPrefix + "-" + lockName;
        return new NativeFSLock(lockDir, lockName);
    }

    @Override
    public void clearLock(String lockName) throws IOException {
        // Note that this isn't strictly required anymore
        // because the existence of these files does not mean
        // they are locked, but, still do this in case people
        // really want to see the files go away:
        if (Files.exists(lockDir)) {

            // Try to release the lock first - if it's held by another process, this
            // method should not silently fail.
            // NOTE: makeLock fixes the lock name by prefixing it w/ lockPrefix.
            // Therefore it should be called before the code block next which prefixes
            // the given name.
            makeLock(lockName).release();

            if (lockPrefix != null) {
                lockName = lockPrefix + "-" + lockName;
            }

            try {
                Files.delete(lockDir.resolve(lockName));
            } catch (IOException e) {
                // As mentioned above, we don't care if the deletion of the file failed.
            }
        }
    }

    static class NativeFSLock extends Lock {

        private FileChannel channel;
        private FileLock lock;
        private Path path;
        private Path lockDir;
        private static final Set<String> LOCK_HELD = Collections.synchronizedSet(new HashSet<String>());


        public NativeFSLock(Path lockDir, String lockFileName) {
            this.lockDir = lockDir;
            path = lockDir.resolve(lockFileName);
        }

        private synchronized boolean lockExists() {
            return lock != null;
        }


        @Override
        public synchronized boolean obtain() throws IOException {

            if (lock != null) {
                // Our instance is already locked:
                return false;
            }

            // Ensure that lockDir exists and is a directory.
            Files.createDirectories(lockDir);
            try {
                Files.createFile(path);
            } catch (IOException ignore) {
                // we must create the file to have a truly canonical path.
                // if it's already created, we don't care. if it cant be created, it will fail below.
            }
            final Path canonicalPath = path.toRealPath();
            // Make sure nobody else in-process has this lock held
            // already, and, mark it held if not:
            // This is a pretty crazy workaround for some documented
            // but yet awkward JVM behavior:
            //
            //   On some systems, closing a channel releases all locks held by the Java virtual machine on the underlying file
            //   regardless of whether the locks were acquired via that channel or via another channel open on the same file.
            //   It is strongly recommended that, within a program, a unique channel be used to acquire all locks on any given
            //   file.
            //
            // This essentially means if we close "A" channel for a given file all locks might be released... the odd part
            // is that we can't re-obtain the lock in the same JVM but from a different process if that happens. Nevertheless
            // this is super trappy. See LUCENE-5738
            boolean obtained = false;
            if (LOCK_HELD.add(canonicalPath.toString())) {
                try {
                    channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    try {
                        lock = channel.tryLock();
                        obtained = lock != null;
                    } catch (IOException | OverlappingFileLockException e) {
                        // At least on OS X, we will sometimes get an
                        // intermittent "Permission Denied" IOException,
                        // which seems to simply mean "you failed to get
                        // the lock".  But other IOExceptions could be
                        // "permanent" (eg, locking is not supported via
                        // the filesystem).  So, we record the failure
                        // reason here; the timeout obtain (usually the
                        // one calling us) will use this as "root cause"
                        // if it fails to get the lock.
                        failureReason = e;
                    }
                } finally {
                    if (!obtained) { // not successful - clear up and move out
                        clearLockHeld(path);
                        final FileChannel toClose = channel;
                        channel = null;
                        IOUtils.closeWhileHandlingException(toClose);
                    }
                }
            }
            return obtained;
        }

        @Override
        public void release() throws IOException {
            try {
                if (lock != null) {
                    try {
                        lock.release();
                        lock = null;
                    } finally {
                        clearLockHeld(path);
                    }
                }
            } finally {
                IOUtils.close(channel);
                channel = null;
            }
        }

        private static void clearLockHeld(Path path) throws IOException {
            path = path.toRealPath();
            boolean remove = LOCK_HELD.remove(path.toString());
            assert remove : "Lock was cleared but never marked as held";
        }

        @Override
        public synchronized boolean isLocked() {
            // The test for is isLocked is not directly possible with native file locks:

            // First a shortcut, if a lock reference in this instance is available
            if (lockExists()) return true;

            // Look if lock file is present; if not, there can definitely be no lock!
            if (!Files.exists(path)) return false;

            // Try to obtain and release (if was locked) the lock
            try {
                boolean obtained = obtain();
                if (obtained) release();
                return !obtained;
            } catch (IOException ioe) {
                return false;
            }
        }

        @Override
        public String toString() {
            return "NativeFSLock@" + path;
        }
    }
}
