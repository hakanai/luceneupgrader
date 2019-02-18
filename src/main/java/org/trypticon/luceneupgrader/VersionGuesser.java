package org.trypticon.luceneupgrader;

import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Tries to guess the version of a Lucene text index with minimal effort.
 */
public class VersionGuesser {
    public LuceneVersion guess(Path path) throws IOException {
        try (Directory directory = FSDirectory.open(path)) {
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

                // This string and the int version are read by checkHeaderNoMagic.
                // Read the next string containing the codec name (discard it?)
                segments.readString();

                // Read the next int containing the actual format version.
                int actualVersion = segments.readInt();
                // - If the value is 0..3, then it's Lucene 4.x
                // - If the value is >= 4, then it's Lucene 5.x
                if (actualVersion >= 0 && actualVersion <= 3) {             // VERSION_40 thru VERSION_49
                    return LuceneVersion.VERSION_4;
                } else if (actualVersion >= 4 && actualVersion < 6) {       // VERSION_50 thru VERSION_52
                    return LuceneVersion.VERSION_5;
                } else if (actualVersion == 6) {                            // VERSION_53
                    // Have to dig further into the file to disambiguate...

                    // Skip over 16-byte ID.
                    segments.skipBytes(16);

                    // Skip over "index header suffix"
                    int suffixLength = segments.readByte() & 255;
                    segments.skipBytes(suffixLength);

                    int majorVersion = segments.readVInt();
                    switch (majorVersion) {
                        case 5:
                            return LuceneVersion.VERSION_5;
                        case 6:
                            return LuceneVersion.VERSION_6;
                        default:
                            throw new UnknownFormatException("Appears to be like version 5-6 but major version " +
                                    "is unrecognised: " + majorVersion);
                    }

                    /* ---- */
                } else if (actualVersion >= 7 && actualVersion <= 9) {
                    return LuceneVersion.VERSION_7;
                } else {
                    throw new UnknownFormatException("Appears to be like version 4+ but actual version " +
                            "is unrecognised: " + actualVersion);
                }

            } else if (format < 0) {

                // Negative versioning used by v2-3
                //- If it's >= -8, then it's Lucene 2.x
                //- If it's == -9 (FORMAT_DIAGNOSTICS), then it's Lucene 2.9 or 3.0.
                //  The docs aren't clear on how to distinguish the two, so we have to treat 3.0 as if it's version 2.
                //- If it's <= -10, then it must be a later 3.x.
                if (format >= -9) {                     // FORMAT_USER_DATA, last format of 2.x, I think.
                    return LuceneVersion.VERSION_2;
                } else if (format >= -11) {             // FORMAT_3_1, last format of 3.x
                    return LuceneVersion.VERSION_3;
                } else {
                    throw new UnknownFormatException("Appears to be like version 2-3 but format " +
                            "is unrecognised: " + format);
                }

            } else {
                //- when it's some other positive number, it's an even older format. I guess we just hope that
                //  the magic number 0x3fd76c17 never occurs?

                // The value we already read is the counter.
                // Next is the SegmentInfo array.
                for (int i = segments.readInt(); i > 0; i--) {
                    segments.readString();  // segment filename
                    segments.readInt();     // segment doc count
                }

                // 1.2 and 1.3 had slightly different formats - 1.3 includes an additional version number at the end.
                // But 1.3 can read 1.2 indices, so we'll consider them the same.
                if (segments.getFilePointer() == segments.length() ||
                        segments.getFilePointer() == segments.length() - 8) {
                    return LuceneVersion.VERSION_1;
                }

                throw new UnknownFormatException("Appears to be like version 1 but file length is unusual");
            }
        }
    }

    private String genToSegmentsFileName(long gen) {
        if (gen <= 0) {
            return "segments";
        } else {
            return "segments_" + Long.toString(gen, 36);
        }
    }
}
