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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.store;

import java.io.IOException;
import java.util.Collection;


public class FilterDirectory extends Directory {

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
  public boolean fileExists(String name) throws IOException {
    return in.fileExists(name);
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
  public void sync(Collection<String> names) throws IOException {
    in.sync(names);
  }

  @Override
  public IndexInput openInput(String name, IOContext context)
      throws IOException {
    return in.openInput(name, context);
  }

  @Override
  public Lock makeLock(String name) {
    return in.makeLock(name);
  }

  @Override
  public void clearLock(String name) throws IOException {
    in.clearLock(name);
  }

  @Override
  public void close() throws IOException {
    in.close();
  }

  @Override
  public void setLockFactory(LockFactory lockFactory) throws IOException {
    in.setLockFactory(lockFactory);
  }

  @Override
  public String getLockID() {
    return in.getLockID();
  }
  
  @Override
  public LockFactory getLockFactory() {
    return in.getLockFactory();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + in.toString() + ")";
  }

}
