package org.trypticon.luceneupgrader.lucene5;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexUpgrader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.trypticon.luceneupgrader.InfoStream;
import org.trypticon.luceneupgrader.VersionUpgrader;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Upgrades an index to Lucene 5 format.
 */
public class VersionUpgrader5 implements VersionUpgrader {
    private final Path path;
    private final InfoStream infoStream;

    public VersionUpgrader5(Path path, InfoStream infoStream) {
        this.path = path;
        this.infoStream = infoStream;
    }

    @Override
    public void upgrade() throws IOException {
        try (Directory directory = FSDirectory.open(path)) {
            SegmentInfos infos = SegmentInfos.readLatestCommit(directory);
            int size = infos.size();
            if (size == 0) {

                //HACK: This is the trick Lucene are using to fix IndexUpgrader for v5.2.2.
                // I'll remove this once we can depend on the fixed version.
                try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new FailAnalyzer()))) {
                    writer.setCommitData(writer.getCommitData());
                    assert writer.hasUncommittedChanges();
                    writer.commit();
                }

            } else {
                AdaptedInfoStream adaptedInfoStream = infoStream == null ? null : new AdaptedInfoStream(infoStream);
                IndexUpgrader upgrader = new IndexUpgrader(directory, adaptedInfoStream, false);
                upgrader.upgrade();
            }
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
