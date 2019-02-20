package org.trypticon.luceneupgrader.lucene4.internal.lucenesupport;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ThreadInterruptedException;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PathIOUtils4 {
    public static void fsync(Path fileToSync, boolean isDir) throws IOException {
        IOException exc = null;

        // If the file is a directory we have to open read-only, for regular files we must open r/w for the fsync to have an effect.
        // See http://blog.httrack.com/blog/2013/11/15/everything-you-always-wanted-to-know-about-fsync/
        try (final FileChannel file = FileChannel.open(fileToSync, isDir ? StandardOpenOption.READ : StandardOpenOption.WRITE)) {
            for (int retry = 0; retry < 5; retry++) {
                try {
                    file.force(true);
                    return;
                } catch (IOException ioe) {
                    if (exc == null) {
                        exc = ioe;
                    }
                    try {
                        // Pause 5 msec
                        Thread.sleep(5L);
                    } catch (InterruptedException ie) {
                        ThreadInterruptedException ex = new ThreadInterruptedException(ie);
                        ex.addSuppressed(exc);
                        throw ex;
                    }
                }
            }
        } catch (IOException ioe) {
            if (exc == null) {
                exc = ioe;
            }
        }

        if (isDir) {
            // TODO: LUCENE-6169 - Fix this assert once Java 9 problems are solved!
            assert (Constants.LINUX || Constants.MAC_OS_X) == false || Constants.JRE_IS_MINIMUM_JAVA9 :
                    "On Linux and MacOSX fsyncing a directory should not throw IOException, "+
                            "we just don't want to rely on that in production (undocumented). Got: " + exc;
            // Ignore exception if it is a directory
            return;
        }

        // Throw original exception
        throw exc;
    }
}
