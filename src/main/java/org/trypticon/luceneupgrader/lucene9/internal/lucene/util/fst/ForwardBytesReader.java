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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.util.fst;

// TODO: can we use just ByteArrayDataInput...?  need to
// add a .skipBytes to DataInput.. hmm and .setPosition

/** Reads from a single byte[]. */
final class ForwardBytesReader extends FST.BytesReader {
  private final byte[] bytes;
  private int pos;

  public ForwardBytesReader(byte[] bytes) {
    this.bytes = bytes;
  }

  @Override
  public byte readByte() {
    return bytes[pos++];
  }

  @Override
  public void readBytes(byte[] b, int offset, int len) {
    System.arraycopy(bytes, pos, b, offset, len);
    pos += len;
  }

  @Override
  public void skipBytes(long count) {
    pos += count;
  }

  @Override
  public long getPosition() {
    return pos;
  }

  @Override
  public void setPosition(long pos) {
    this.pos = (int) pos;
  }
}
