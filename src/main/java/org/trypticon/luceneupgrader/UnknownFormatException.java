package org.trypticon.luceneupgrader;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.CorruptIndexException;

/**
 * Specific exception thrown when the upgrade tool figure the index is too old to migrate.
 */
public class UnknownFormatException extends CorruptIndexException {
    public UnknownFormatException(String message) {
        super(message);
    }
}
