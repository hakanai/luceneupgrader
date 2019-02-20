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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.store;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException; // javadoc @link
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future; // javadoc

public class NIOFSDirectory extends FSDirectory {


  public NIOFSDirectory(Path path, LockFactory lockFactory) throws IOException {
    super(path, lockFactory);
  }


  public NIOFSDirectory(Path path) throws IOException {
    this(path, FSLockFactory.getDefault());
  }

  @Override
  public IndexInput openInput(String name, IOContext context) throws IOException {
    ensureOpen();
    ensureCanRead(name);
    Path path = getDirectory().resolve(name);
    FileChannel fc = FileChannel.open(path, StandardOpenOption.READ);
    return new NIOFSIndexInput("NIOFSIndexInput(path=\"" + path + "\")", fc, context);
  }
  
  static final class NIOFSIndexInput extends BufferedIndexInput {
    private static final int CHUNK_SIZE = 16384;
    
    protected final FileChannel channel;
    boolean isClone = false;
    protected final long off;
    protected final long end;
    
    private ByteBuffer byteBuf; // wraps the buffer for NIO

    public NIOFSIndexInput(String resourceDesc, FileChannel fc, IOContext context) throws IOException {
      super(resourceDesc, context);
      this.channel = fc; 
      this.off = 0L;
      this.end = fc.size();
    }
    
    public NIOFSIndexInput(String resourceDesc, FileChannel fc, long off, long length, int bufferSize) {
      super(resourceDesc, bufferSize);
      this.channel = fc;
      this.off = off;
      this.end = off + length;
      this.isClone = true;
    }
    
    @Override
    public void close() throws IOException {
      if (!isClone) {
        channel.close();
      }
    }
    
    @Override
    public NIOFSIndexInput clone() {
      NIOFSIndexInput clone = (NIOFSIndexInput)super.clone();
      clone.isClone = true;
      return clone;
    }
    
    @Override
    public IndexInput slice(String sliceDescription, long offset, long length) throws IOException {
      if (offset < 0 || length < 0 || offset + length > this.length()) {
        throw new IllegalArgumentException("slice() " + sliceDescription + " out of bounds: offset=" + offset + ",length=" + length + ",fileLength="  + this.length() + ": "  + this);
      }
      return new NIOFSIndexInput(getFullSliceDescription(sliceDescription), channel, off + offset, length, getBufferSize());
    }

    @Override
    public final long length() {
      return end - off;
    }

    @Override
    protected void newBuffer(byte[] newBuffer) {
      super.newBuffer(newBuffer);
      byteBuf = ByteBuffer.wrap(newBuffer);
    }

    @Override
    protected void readInternal(byte[] b, int offset, int len) throws IOException {
      final ByteBuffer bb;

      // Determine the ByteBuffer we should use
      if (b == buffer) {
        // Use our own pre-wrapped byteBuf:
        assert byteBuf != null;
        bb = byteBuf;
        byteBuf.clear().position(offset);
      } else {
        bb = ByteBuffer.wrap(b, offset, len);
      }

      long pos = getFilePointer() + off;
      
      if (pos + len > end) {
        throw new EOFException("read past EOF: " + this);
      }

      try {
        int readLength = len;
        while (readLength > 0) {
          final int toRead = Math.min(CHUNK_SIZE, readLength);
          bb.limit(bb.position() + toRead);
          assert bb.remaining() == toRead;
          final int i = channel.read(bb, pos);
          if (i < 0) { // be defensive here, even though we checked before hand, something could have changed
            throw new EOFException("read past EOF: " + this + " off: " + offset + " len: " + len + " pos: " + pos + " chunkLen: " + toRead + " end: " + end);
          }
          assert i > 0 : "FileChannel.read with non zero-length bb.remaining() must always read at least one byte (FileChannel is in blocking mode, see spec of ReadableByteChannel)";
          pos += i;
          readLength -= i;
        }
        assert readLength == 0;
      } catch (IOException ioe) {
        throw new IOException(ioe.getMessage() + ": " + this, ioe);
      }
    }

    @Override
    protected void seekInternal(long pos) throws IOException {
      if (pos > length()) {
        throw new EOFException("read past EOF: pos=" + pos + " vs length=" + length() + ": " + this);
      }
    }
  }
}
