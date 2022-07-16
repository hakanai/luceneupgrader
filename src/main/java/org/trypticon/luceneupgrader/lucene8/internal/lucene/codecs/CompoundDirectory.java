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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs;

import java.io.IOException;
import java.util.Collection;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.Lock;

public abstract class CompoundDirectory extends Directory {

  protected CompoundDirectory() {}

  public abstract void checkIntegrity() throws IOException;

  @Override
  public final void deleteFile(String name) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public final void rename(String from, String to) {
    throw new UnsupportedOperationException();
  }

  @Override
  public final void syncMetaData() {
  }

  @Override
  public final IndexOutput createOutput(String name, IOContext context) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public final IndexOutput createTempOutput(String prefix, String suffix, IOContext context) throws IOException {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public final void sync(Collection<String> names) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public final Lock obtainLock(String name) {
    throw new UnsupportedOperationException();
  }

}
