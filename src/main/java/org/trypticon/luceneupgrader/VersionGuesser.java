package org.trypticon.luceneupgrader;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentInfos;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucenesupport.PathFSDirectory4;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Tries to guess the version of a Lucene text index with minimal effort.
 */
public class VersionGuesser {
    public LuceneVersion guess(Path path) throws IOException {
        //TODO: Change this to FSDirectory once lucene 5 is in here.
        try (Directory directory = PathFSDirectory4.open(path)) {
            return guess(directory);
        }
    }

    public LuceneVersion guess(Directory directory) throws IOException {
        IOContext context = IOContext.DEFAULT;

        // Encapsulates logic roughly like:
        // - If segments.gen is missing, panic? (or list all segments_N files to double-check? or assume it's older than 2.0?)
        // - Read segments.gen to figure out which segments_N to read
        // I am calling it because there is a lot of code in there and I'm optimistic that they won't break things
        // horribly, but if they do, I guess we can just inline it.
        long gen = SegmentInfos.getLastCommitGeneration(directory);

        try (IndexInput segments = directory.openInput(genToSegmentsFileName(gen), context)) {
            // Read segments_N, first 4 bytes contain the format as an int
            int format = segments.readInt();
            if (format == 0x3fd76c17) { // == CodecUtil.CODEC_MAGIC

                // Read the next string containing the codec name (discard it?)
                segments.readString();

                // Read the next int containing the actual format version.
                int actualVersion = segments.readInt();
                // - If the value is 0..3, then it's Lucene 4.x
                // - If the value is >= 4, then it's Lucene 5.x
                if (actualVersion >= 0 || actualVersion <= 3) {     // VERSION_40 thru VERSION_49
                    return LuceneVersion.VERSION_4;
                } else if (actualVersion == 4) {                    // VERSION_50
                    return LuceneVersion.VERSION_5;
                } else {
                    throw new UnknownFormatException("Appears to be like version 4-5 but actual version " +
                            "is unrecognised: " + actualVersion);
                }

            } else if (format < 0) {

                // Negative versioning used by v2-3
                //- If it's >= -8, then it's Lucene 2.x
                //- If it's <= -9 (FORMAT_DIAGNOSTICS), then it's Lucene 3.x
                if (format >= -8) {                     // FORMAT_USER_DATA, last format of 2.x, I think.
                    return LuceneVersion.VERSION_2;
                } else if (format >= -11) {             // FORMAT_3_1, last format of 3.x
                    return LuceneVersion.VERSION_3;
                } else {
                    throw new UnknownFormatException("Appears to be like version 2-3 but format " +
                            "is unrecognised: " + format);
                }

            } else {

                //- when it's some other positive number, panic? Supposedly positive numbers were an older format,
                //  but 0x3fd76c17 is also a positive number.
                throw new UnknownFormatException("Too old for me to figure out, first int value was " + format);
            }
        }
    }

    private String genToSegmentsFileName(long gen) {
        if (gen == 0) {
            return "segments";
        } else {
            return "segments_" + gen;
        }
    }
}
