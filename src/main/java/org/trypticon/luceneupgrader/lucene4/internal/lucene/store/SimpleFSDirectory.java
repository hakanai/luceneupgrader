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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;


public class SimpleFSDirectory extends FSDirectory {
    

  public SimpleFSDirectory(File path, LockFactory lockFactory) throws IOException {
    super(path, lockFactory);
  }
  

  public SimpleFSDirectory(File path) throws IOException {
    super(path, null);
  }

  @Override
  public IndexInput openInput(String name, IOContext context) throws IOException {
    ensureOpen();
    final File path = new File(directory, name);
    RandomAccessFile raf = new RandomAccessFile(path, "r");
    return new SimpleFSIndexInput("SimpleFSIndexInput(path=\"" + path.getPath() + "\")", raf, context);
  }

  static final class SimpleFSIndexInput extends BufferedIndexInput {
    private static final int CHUNK_SIZE = 8192;
    
    protected final RandomAccessFile file;
    boolean isClone = false;
    protected final long off;
    protected final long end;
    
    public SimpleFSIndexInput(String resourceDesc, RandomAccessFile file, IOContext context) throws IOException {
      super(resourceDesc, context);
      this.file = file; 
      this.off = 0L;
      this.end = file.length();
    }
    
    public SimpleFSIndexInput(String resourceDesc, RandomAccessFile file, long off, long length, int bufferSize) {
      super(resourceDesc, bufferSize);
      this.file = file;
      this.off = off;
      this.end = off + length;
      this.isClone = true;
    }
    
    @Override
    public void close() throws IOException {
      if (!isClone) {
        file.close();
      }
    }
    
    @Override
    public SimpleFSIndexInput clone() {
      SimpleFSIndexInput clone = (SimpleFSIndexInput)super.clone();
      clone.isClone = true;
      return clone;
    }
    
    @Override
    public IndexInput slice(String sliceDescription, long offset, long length) throws IOException {
      if (offset < 0 || length < 0 || offset + length > this.length()) {
        throw new IllegalArgumentException("slice() " + sliceDescription + " out of bounds: "  + this);
      }
      return new SimpleFSIndexInput(sliceDescription, file, off + offset, length, getBufferSize());
    }

    @Override
    public final long length() {
      return end - off;
    }
  
    @Override
    protected void readInternal(byte[] b, int offset, int len)
         throws IOException {
      synchronized (file) {
        long position = off + getFilePointer();
        file.seek(position);
        int total = 0;

        if (position + len > end) {
          throw new EOFException("read past EOF: " + this);
        }

        try {
          while (total < len) {
            final int toRead = Math.min(CHUNK_SIZE, len - total);
            final int i = file.read(b, offset + total, toRead);
            if (i < 0) { // be defensive here, even though we checked before hand, something could have changed
             throw new EOFException("read past EOF: " + this + " off: " + offset + " len: " + len + " total: " + total + " chunkLen: " + toRead + " end: " + end);
            }
            assert i > 0 : "RandomAccessFile.read with non zero-length toRead must always read at least one byte";
            total += i;
          }
          assert total == len;
        } catch (IOException ioe) {
          throw new IOException(ioe.getMessage() + ": " + this, ioe);
        }
      }
    }
  
    @Override
    protected void seekInternal(long position) {
    }
    
    boolean isFDValid() throws IOException {
      return file.getFD().valid();
    }
  }
}
