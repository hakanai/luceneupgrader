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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

public class OutputStreamIndexOutput extends IndexOutput {

  private final CRC32 crc = new CRC32();
  private final BufferedOutputStream os;
  
  private long bytesWritten = 0L;

  public OutputStreamIndexOutput(OutputStream out, int bufferSize) {
    this.os = new BufferedOutputStream(new CheckedOutputStream(out, crc), bufferSize);
  }

  @Override
  public final void writeByte(byte b) throws IOException {
    os.write(b);
    bytesWritten++;
  }
  
  @Override
  public final void writeBytes(byte[] b, int offset, int length) throws IOException {
    os.write(b, offset, length);
    bytesWritten += length;
  }

  @Override
  public void close() throws IOException {
    try (final OutputStream o = os) {
      // We want to make sure that os.flush() was running before close:
      // BufferedOutputStream may ignore IOExceptions while flushing on close().
      // TODO: this is no longer an issue in Java 8:
      // http://hg.openjdk.java.net/jdk8/tl/jdk/rev/759aa847dcaf
      o.flush();
    }
  }
  
  @Override
  public final long getFilePointer() {
    return bytesWritten;
  }

  @Override
  public final long getChecksum() throws IOException {
    os.flush();
    return crc.getValue();
  }

  @Override
  public void flush() throws IOException {
    os.flush();
  }
}
