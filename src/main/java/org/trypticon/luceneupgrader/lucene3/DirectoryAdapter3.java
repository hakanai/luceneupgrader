package org.trypticon.luceneupgrader.lucene3;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexOutput;

import java.io.IOException;

/**
 * Directory adapter to Lucene 3.
 * Won't make a lot of sense at the moment because I have only done up to 3.
 */
public class DirectoryAdapter3 extends Directory {
    private final org.apache.lucene.store.Directory delegate;

    public DirectoryAdapter3(org.apache.lucene.store.Directory delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] listAll() throws IOException {
        return delegate.listAll();
    }

    @Override
    public boolean fileExists(String name) throws IOException {
        return delegate.fileExists(name);
    }

    @Override
    public void deleteFile(String name) throws IOException {
        delegate.deleteFile(name);
    }

    @Override
    public long fileLength(String name) throws IOException {
        return delegate.fileLength(name);
    }

    @Override
    public IndexOutput createOutput(String name) throws IOException {
        return new IndexOutputAdapter3(delegate.createOutput(name));
    }

    @Override
    public IndexInput openInput(String name) throws IOException {
        return new IndexInputAdapter3(delegate.openInput(name));
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
