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
import java.io.Closeable;

public abstract class IndexInput extends DataInput implements Cloneable,Closeable {

  @Deprecated
  public void skipChars(int length) throws IOException{
    for (int i = 0; i < length; i++) {
      byte b = readByte();
      if ((b & 0x80) == 0){
        //do nothing, we only need one byte
      } else if ((b & 0xE0) != 0xE0) {
        readByte();//read an additional byte
      } else {      
        //read two additional bytes.
        readByte();
        readByte();
      }
    }
  }

  private final String resourceDescription;

  @Deprecated
  protected IndexInput() {
    this("anonymous IndexInput");
  }


  protected IndexInput(String resourceDescription) {
    if (resourceDescription == null) {
      throw new IllegalArgumentException("resourceDescription must not be null");
    }
    this.resourceDescription = resourceDescription;
  }

  public abstract void close() throws IOException;


  public abstract long getFilePointer();


  public abstract void seek(long pos) throws IOException;

  public abstract long length();

  public void copyBytes(IndexOutput out, long numBytes) throws IOException {
    assert numBytes >= 0: "numBytes=" + numBytes;

    byte copyBuf[] = new byte[BufferedIndexInput.BUFFER_SIZE];

    while (numBytes > 0) {
      final int toCopy = (int) (numBytes > copyBuf.length ? copyBuf.length : numBytes);
      readBytes(copyBuf, 0, toCopy);
      out.writeBytes(copyBuf, 0, toCopy);
      numBytes -= toCopy;
    }
  }

  @Override
  public String toString() {
    return resourceDescription;
  }
}
