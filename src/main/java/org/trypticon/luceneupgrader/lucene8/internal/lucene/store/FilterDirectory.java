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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.store;


import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public abstract class FilterDirectory extends Directory {

  public static Directory unwrap(Directory dir) {
    while (dir instanceof FilterDirectory) {
      dir = ((FilterDirectory) dir).in;
    }
    return dir;
  }

  protected final Directory in;

  protected FilterDirectory(Directory in) {
    this.in = in;
  }

  public final Directory getDelegate() {
    return in;
  }

  @Override
  public String[] listAll() throws IOException {
    return in.listAll();
  }

  @Override
  public void deleteFile(String name) throws IOException {
    in.deleteFile(name);
  }

  @Override
  public long fileLength(String name) throws IOException {
    return in.fileLength(name);
  }

  @Override
  public IndexOutput createOutput(String name, IOContext context)
      throws IOException {
    return in.createOutput(name, context);
  }

  @Override
  public IndexOutput createTempOutput(String prefix, String suffix, IOContext context) throws IOException {
    return in.createTempOutput(prefix, suffix, context);
  }

  @Override
  public void sync(Collection<String> names) throws IOException {
    in.sync(names);
  }

  @Override
  public void rename(String source, String dest) throws IOException {
    in.rename(source, dest);
  }

  @Override
  public void syncMetaData() throws IOException {
    in.syncMetaData();
  }

  @Override
  public IndexInput openInput(String name, IOContext context)
      throws IOException {
    return in.openInput(name, context);
  }

  @Override
  public Lock obtainLock(String name) throws IOException {
    return in.obtainLock(name);
  }

  @Override
  public void close() throws IOException {
    in.close();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + in.toString() + ")";
  }

  @Override
  public Set<String> getPendingDeletions() throws IOException {
    return in.getPendingDeletions();
  }
}
