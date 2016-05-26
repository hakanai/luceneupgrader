package org.trypticon.luceneupgrader.lucene4;

import org.trypticon.luceneupgrader.InfoStream;
import org.trypticon.luceneupgrader.VersionUpgrader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexUpgrader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexWriterConfig;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.LogByteSizeMergePolicy;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Version;
import org.trypticon.luceneupgrader.lucene4.internal.lucenesupport.PathFSDirectory4;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Upgrades an index to Lucene 4 format.
 */
public class VersionUpgrader4 implements VersionUpgrader {
    private final Path path;
    private final InfoStream infoStream;

    public VersionUpgrader4(Path path, InfoStream infoStream) {
        this.path = path;
        this.infoStream = infoStream;
    }

    @Override
    public void upgrade() throws IOException {
        try (Directory directory = PathFSDirectory4.open(path)) {
            org.trypticon.luceneupgrader.lucene4.internal.lucene.util.InfoStream adaptedInfoStream =
                infoStream == null ? org.trypticon.luceneupgrader.lucene4.internal.lucene.util.InfoStream.NO_OUTPUT
                                   : new AdaptedInfoStream(infoStream);
            IndexWriterConfig indexWriterConfig = new IndexWriterConfig(Version.LUCENE_4_10_4, null);
            indexWriterConfig.setMergePolicy(new LogByteSizeMergePolicy());
            indexWriterConfig.setInfoStream(adaptedInfoStream);
            IndexUpgrader upgrader = new IndexUpgrader(directory, indexWriterConfig, false);
            upgrader.upgrade();
        }
    }

    /**
     * Adapts Lucene's info stream to pass messages to ours.
     */
    private static class AdaptedInfoStream extends org.trypticon.luceneupgrader.lucene4.internal.lucene.util.InfoStream {
        private final InfoStream infoStream;

        private AdaptedInfoStream(InfoStream infoStream) {
            this.infoStream = infoStream;
        }

        @Override
        public void message(String component, String message) {
            infoStream.message(component, message);
        }

        @Override
        public boolean isEnabled(String component) {
            return infoStream.isEnabled(component);
        }

        @Override
        public void close() throws IOException {
            //
        }
    }
}
