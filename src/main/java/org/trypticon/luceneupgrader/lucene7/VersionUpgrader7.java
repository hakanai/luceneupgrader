package org.trypticon.luceneupgrader.lucene7;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.trypticon.luceneupgrader.InfoStream;
import org.trypticon.luceneupgrader.VersionUpgrader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Upgrades an index to Lucene 7 format.
 */
public class VersionUpgrader7 implements VersionUpgrader {

    @Nonnull
    private final Path path;

    @Nonnull
    private final InfoStream infoStream;

    public VersionUpgrader7(@Nonnull Path path, @Nonnull InfoStream infoStream) {
        this.path = path;
        this.infoStream = infoStream;
    }

    @Override
    public void upgrade() throws IOException {
        try (Directory directory = FSDirectory.open(path)) {
            org.apache.lucene.util.InfoStream adaptedInfoStream = new AdaptedInfoStream(infoStream);
            IndexWriterConfig indexWriterConfig = new IndexWriterConfig(new FailAnalyzer());
            indexWriterConfig.setMergePolicy(new LogByteSizeMergePolicy());
            indexWriterConfig.setMergeScheduler(new SerialMergeScheduler());
            indexWriterConfig.setInfoStream(adaptedInfoStream);
            IndexUpgrader upgrader = new IndexUpgrader(directory, indexWriterConfig, true);
            upgrader.upgrade();
        }
    }

    /**
     * Adapts Lucene's info stream to pass messages to ours.
     */
    private static class AdaptedInfoStream extends org.apache.lucene.util.InfoStream {
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

    /**
     * An analyser which deliberately fails, because we don't want to be analysing text at all.
     */
    private static class FailAnalyzer extends Analyzer {
        @Override
        protected TokenStreamComponents createComponents(String s) {
            throw new UnsupportedOperationException("This analyser isn't supported for indexing");
        }
    }
}
