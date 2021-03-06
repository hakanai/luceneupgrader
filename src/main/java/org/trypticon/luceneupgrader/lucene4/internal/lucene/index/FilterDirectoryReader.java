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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import java.io.IOException;
import java.util.List;

public abstract class FilterDirectoryReader extends DirectoryReader {

  public static abstract class SubReaderWrapper {

    private AtomicReader[] wrap(List<? extends AtomicReader> readers) {
      AtomicReader[] wrapped = new AtomicReader[readers.size()];
      for (int i = 0; i < readers.size(); i++) {
        wrapped[i] = wrap(readers.get(i));
      }
      return wrapped;
    }

    public SubReaderWrapper() {}

    public abstract AtomicReader wrap(AtomicReader reader);

  }

  public static class StandardReaderWrapper extends SubReaderWrapper {

    public StandardReaderWrapper() {}

    @Override
    public AtomicReader wrap(AtomicReader reader) {
      return reader;
    }
  }

  protected final DirectoryReader in;

  public FilterDirectoryReader(DirectoryReader in) {
    this(in, new StandardReaderWrapper());
  }

  public FilterDirectoryReader(DirectoryReader in, SubReaderWrapper wrapper) {
    super(in.directory(), wrapper.wrap(in.getSequentialSubReaders()));
    this.in = in;
  }

  protected abstract DirectoryReader doWrapDirectoryReader(DirectoryReader in);

  private final DirectoryReader wrapDirectoryReader(DirectoryReader in) {
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
    in.doClose();
  }

}
