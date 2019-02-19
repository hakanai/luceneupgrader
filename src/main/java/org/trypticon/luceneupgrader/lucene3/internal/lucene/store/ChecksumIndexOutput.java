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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.store;

import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class ChecksumIndexOutput extends IndexOutput {
  IndexOutput main;
  Checksum digest;

  public ChecksumIndexOutput(IndexOutput main) {
    this.main = main;
    digest = new CRC32();
  }

  @Override
  public void writeByte(byte b) throws IOException {
    digest.update(b);
    main.writeByte(b);
  }

  @Override
  public void writeBytes(byte[] b, int offset, int length) throws IOException {
    digest.update(b, offset, length);
    main.writeBytes(b, offset, length);
  }

  public long getChecksum() {
    return digest.getValue();
  }

  @Override
  public void flush() throws IOException {
    main.flush();
  }

  @Override
  public void close() throws IOException {
    main.close();
  }

  @Override
  public long getFilePointer() {
    return main.getFilePointer();
  }

  @Override
  public void seek(long pos) {
    throw new UnsupportedOperationException();    
  }

  public void prepareCommit() throws IOException {
    final long checksum = getChecksum();
    // Intentionally write a mismatched checksum.  This is
    // because we want to 1) test, as best we can, that we
    // are able to write a long to the file, but 2) not
    // actually "commit" the file yet.  This (prepare
    // commit) is phase 1 of a two-phase commit.
    final long pos = main.getFilePointer();
    main.writeLong(checksum-1);
    main.flush();
    main.seek(pos);
  }

  public void finishCommit() throws IOException {
    main.writeLong(getChecksum());
  }

  @Override
  public long length() throws IOException {
    return main.length();
  }
}
