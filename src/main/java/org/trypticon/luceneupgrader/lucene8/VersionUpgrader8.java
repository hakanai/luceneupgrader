package org.trypticon.luceneupgrader.lucene8;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.*;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.FSDirectory;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.Version;
import org.trypticon.luceneupgrader.FileUtils;
import org.trypticon.luceneupgrader.InfoStream;
import org.trypticon.luceneupgrader.VersionUpgrader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Upgrades an index to Lucene 8 format.
 */
public class VersionUpgrader8 implements VersionUpgrader {

    @Nonnull
    private final Path path;

    @Nonnull
    private final InfoStream infoStream;

    public VersionUpgrader8(@Nonnull Path path, @Nonnull InfoStream infoStream) {
        this.path = path;
        this.infoStream = infoStream;
    }

    @Override
    public void upgrade() throws IOException {
        Path oldPath = path.resolveSibling(path.getFileName() + ".old");
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");

        FileUtils.insecureRecursiveDelete(tempPath);
        Files.createDirectory(tempPath);

        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(new FailAnalyzer());
        indexWriterConfig.setMergePolicy(new LogByteSizeMergePolicy());
        indexWriterConfig.setMergeScheduler(new SerialMergeScheduler());
        indexWriterConfig.setInfoStream(new AdaptedInfoStream(infoStream));
        indexWriterConfig.setIndexCreatedVersionMajor(8);

        try (Directory sourceDirectory = FSDirectory.open(path);
             Directory destinationDirectory = FSDirectory.open(tempPath);
             IndexReader reader = DirectoryReader.open(sourceDirectory);
             IndexWriter writer = new IndexWriter(destinationDirectory, indexWriterConfig)) {

            CodecReader[] codecReaders = reader.leaves().stream()
                .map(context -> new VersionOverridingCodecReader((CodecReader) context.reader()))
                .toArray(CodecReader[]::new);

            writer.addIndexes(codecReaders);
            writer.commit();
        }

        Files.move(path, oldPath);
        Files.move(tempPath, path);
        FileUtils.insecureRecursiveDelete(oldPath);
    }

    /**
     * Adapts Lucene's info stream to pass messages to ours.
     */
    private static class AdaptedInfoStream
            extends org.trypticon.luceneupgrader.lucene8.internal.lucene.util.InfoStream {
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

    private static class VersionOverridingCodecReader extends FilterCodecReader {
        private final LeafMetaData metadata;

        private VersionOverridingCodecReader(CodecReader in) {
            super(in);

            LeafMetaData superMetadata = super.getMetaData();
            metadata = new LeafMetaData(8, Version.LUCENE_8_0_0, superMetadata.getSort());
        }

        @Override
        public LeafMetaData getMetaData() {
            return metadata;
        }

        @Override
        public CacheHelper getReaderCacheHelper() {
            return in.getReaderCacheHelper();
        }

        @Override
        public CacheHelper getCoreCacheHelper() {
            return in.getCoreCacheHelper();
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
