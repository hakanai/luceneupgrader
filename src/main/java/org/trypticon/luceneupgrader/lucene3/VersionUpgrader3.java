package org.trypticon.luceneupgrader.lucene3;

import org.trypticon.luceneupgrader.InfoStream;
import org.trypticon.luceneupgrader.VersionUpgrader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexUpgrader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexWriterConfig;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.LogByteSizeMergePolicy;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;
import org.trypticon.luceneupgrader.lucene3.internal.lucenesupport.PathFSDirectory3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Upgrades an index to Lucene 3 format.
 */
public class VersionUpgrader3 implements VersionUpgrader {
    private final Path path;
    private final InfoStream infoStream;

    public VersionUpgrader3(Path path, InfoStream infoStream) {
        this.path = path;
        this.infoStream = infoStream;
    }

    @Override
    public void upgrade() throws IOException {
        try (Directory directory = PathFSDirectory3.open(path)) {
            PrintStream printStream = infoStream == null ? null : new PrintStream(new InfoStreamOutputStream(infoStream));
            IndexWriterConfig indexWriterConfig = new IndexWriterConfig(Version.LUCENE_36, null);
            indexWriterConfig.setMergePolicy(new LogByteSizeMergePolicy());
            IndexUpgrader upgrader = new IndexUpgrader(directory, indexWriterConfig, printStream, false);
            upgrader.upgrade();
        }
    }

    /**
     * Poor adapter to redirect a stream to the info stream.
     */
    private static class InfoStreamOutputStream extends OutputStream {
        private final InfoStream infoStream;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private InfoStreamOutputStream(InfoStream infoStream) {
            this.infoStream = infoStream;
        }

        @Override
        public void write(int b) throws IOException {
            if (b == '\n') {
                infoStream.message("-", buffer.toString(StandardCharsets.UTF_8.name()));
                buffer.reset();
            } else {
                buffer.write(b);
            }
        }
    }
}
