/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trypticon.luceneupgrader.lucene7.internal.lucene.index;


import java.io.IOException;
import java.util.List;

public abstract class FilterDirectoryReader extends DirectoryReader {

  public static DirectoryReader unwrap(DirectoryReader reader) {
    while (reader instanceof FilterDirectoryReader) {
      reader = ((FilterDirectoryReader) reader).getDelegate();
    }
    return reader;
  }

  public static abstract class SubReaderWrapper {

    protected LeafReader[] wrap(List<? extends LeafReader> readers) {
      LeafReader[] wrapped = new LeafReader[readers.size()];
      int i = 0;
      for (LeafReader reader : readers) {
        LeafReader wrap = wrap(reader);
        assert wrap != null;
        wrapped[i++] = wrap;
      }
      return wrapped;
    }

    public SubReaderWrapper() {}

    public abstract LeafReader wrap(LeafReader reader);

  }

  protected final DirectoryReader in;

  public FilterDirectoryReader(DirectoryReader in, SubReaderWrapper wrapper) throws IOException {
    super(in.directory(), wrapper.wrap(in.getSequentialSubReaders()));
    this.in = in;
  }

  protected abstract DirectoryReader doWrapDirectoryReader(DirectoryReader in) throws IOException;

  private final DirectoryReader wrapDirectoryReader(DirectoryReader in) throws IOException {
    return in == null ? null : doWrapDirectoryReader(in);
  }

  @Override
  protected final DirectoryReader doOpenIfChanged() throws IOException {
    return wrapDirectoryReader(in.doOpenIfChanged());
  }

  @Override
  protected final DirectoryReader doOpenIfChanged(IndexCommit commit) throws IOException {
    return wrapDirectoryReader(in.doOpenIfChanged(commit));
  }

  @Override
  protected final DirectoryReader doOpenIfChanged(IndexWriter writer, boolean applyAllDeletes) throws IOException {
    return wrapDirectoryReader(in.doOpenIfChanged(writer, applyAllDeletes));
  }

  @Override
  public long getVersion() {
    return in.getVersion();
  }

  @Override
  public boolean isCurrent() throws IOException {
    return in.isCurrent();
  }

  @Override
  public IndexCommit getIndexCommit() throws IOException {
    return in.getIndexCommit();
  }

  @Override
  protected void doClose() throws IOException {
    in.close();
  }

  public DirectoryReader getDelegate() {
    return in;
  }
}
