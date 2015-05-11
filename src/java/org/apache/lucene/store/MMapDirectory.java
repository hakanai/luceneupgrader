package org.apache.lucene.store;

/**
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
 
import org.apache.lucene.util.Constants;
import org.apache.lucene.util.WeakIdentityMap;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;

/** File-based {@code Directory} implementation that uses
 *  mmap for reading, and {@code
 *  FSDirectory.FSIndexOutput} for writing.
 *
 * <p><b>NOTE</b>: memory mapping uses up a portion of the
 * virtual memory address space in your process equal to the
 * size of the file being mapped.  Before using this class,
 * be sure your have plenty of virtual address space, e.g. by
 * using a 64 bit JRE, or a 32 bit JRE with indexes that are
 * guaranteed to fit within the address space.
 * On 32 bit platforms also consult {@code #setMaxChunkSize}
 * if you have problems with mmap failing because of fragmented
 * address space. If you get an OutOfMemoryException, it is recommended
 * to reduce the chunk size, until it works.
 *
 * <p>Due to <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4724038">
 * this bug</a> in Sun's JRE, MMapDirectory's {@code IndexInput#close}
 * is unable to close the underlying OS file handle.  Only when GC
 * finally collects the underlying objects, which could be quite
 * some time later, will the file handle be closed.
 *
 * <p>This will consume additional transient disk usage: on Windows,
 * attempts to delete or overwrite the files will result in an
 * exception; on other platforms, which typically have a &quot;delete on
 * last close&quot; semantics, while such operations will succeed, the bytes
 * are still consuming space on disk.  For many applications this
 * limitation is not a problem (e.g. if you have plenty of disk space,
 * and you don't rely on overwriting files on Windows) but it's still
 * an important limitation to be aware of.
 *
 * <p>This class supplies the workaround mentioned in the bug report
 * (see {@code #setUseUnmap}), which may fail on
 * non-Sun JVMs. It forcefully unmaps the buffer on close by using
 * an undocumented internal cleanup functionality.
 * {@code #UNMAP_SUPPORTED} is <code>true</code>, if the workaround
 * can be enabled (with no guarantees).
 * <p>
 * <b>NOTE:</b> Accessing this class either directly or
 * indirectly from a thread while it's interrupted can close the
 * underlying channel immediately if at the same time the thread is
 * blocked on IO. The channel will remain closed and subsequent access
 * to {@code MMapDirectory} will throw a {@code ClosedChannelException}.
 * </p>
 */
public class MMapDirectory extends FSDirectory {
  /**
   * <code>true</code>, if this platform supports unmapping mmapped files.
   */
  public static final boolean UNMAP_SUPPORTED;
  static {
    boolean v;
    try {
      Class.forName("sun.misc.Cleaner");
      Class.forName("java.nio.DirectByteBuffer")
              .getMethod("cleaner");
      v = true;
    } catch (Exception e) {
      v = false;
    }
    UNMAP_SUPPORTED = v;
  }

  private boolean useUnmapHack = UNMAP_SUPPORTED;
  public static final int DEFAULT_MAX_BUFF = Constants.JRE_IS_64BIT ? (1 << 30) : (1 << 28);
  private int chunkSizePower;

  /** Create a new MMapDirectory for the named location.
   *
   * @param path the path of the directory
   * @param lockFactory the lock factory to use, or null for the default
   * ({@code NativeFSLockFactory});
   * @throws IOException
   */
  public MMapDirectory(File path, LockFactory lockFactory) throws IOException {
    super(path, lockFactory);
    setMaxChunkSize(DEFAULT_MAX_BUFF);
  }

  /**
   * Try to unmap the buffer, this method silently fails if no support
   * for that in the JVM. On Windows, this leads to the fact,
   * that mmapped files cannot be modified or deleted.
   */
  final void cleanMapping(final ByteBuffer buffer) throws IOException {
    if (useUnmapHack) {
      try {
        AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
          public Object run() throws Exception {
            final Method getCleanerMethod = buffer.getClass()
              .getMethod("cleaner");
            getCleanerMethod.setAccessible(true);
            final Object cleaner = getCleanerMethod.invoke(buffer);
            if (cleaner != null) {
              cleaner.getClass().getMethod("clean")
                .invoke(cleaner);
            }
            return null;
          }
        });
      } catch (PrivilegedActionException e) {
        final IOException ioe = new IOException("unable to unmap the mapped buffer");
        ioe.initCause(e.getCause());
        throw ioe;
      }
    }
  }
  
  /**
   * Sets the maximum chunk size (default is 1 GiBytes for
   * 64 bit JVMs and 256 MiBytes for 32 bit JVMs) used for memory mapping.
   * Especially on 32 bit platform, the address space can be very fragmented,
   * so large index files cannot be mapped.
   * Using a lower chunk size makes the directory implementation a little
   * bit slower (as the correct chunk may be resolved on lots of seeks)
   * but the chance is higher that mmap does not fail. On 64 bit
   * Java platforms, this parameter should always be {@code 1 << 30},
   * as the address space is big enough.
   * <b>Please note:</b> This method always rounds down the chunk size
   * to a power of 2.
   */
  public final void setMaxChunkSize(final int maxChunkSize) {
    if (maxChunkSize <= 0)
      throw new IllegalArgumentException("Maximum chunk size for mmap must be >0");
    //System.out.println("Requested chunk size: "+maxChunkSize);
    this.chunkSizePower = 31 - Integer.numberOfLeadingZeros(maxChunkSize);
    assert this.chunkSizePower >= 0 && this.chunkSizePower <= 30;
    //System.out.println("Got chunk size: "+getMaxChunkSize());
  }

  /** Creates an IndexInput for the file with the given name. */
  @Override
  public IndexInput openInput(String name, int bufferSize) throws IOException {
    ensureOpen();
    File f = new File(getDirectory(), name);
    RandomAccessFile raf = new RandomAccessFile(f, "r");
    try {
      return new MMapIndexInput("MMapIndexInput(path=\"" + f + "\")", raf, chunkSizePower);
    } finally {
      raf.close();
    }
  }

  // Because Java's ByteBuffer uses an int to address the
  // values, it's necessary to access a file >
  // Integer.MAX_VALUE in size using multiple byte buffers.
  private final class MMapIndexInput extends IndexInput {
  
    private ByteBuffer[] buffers;
  
    private final long length, chunkSizeMask, chunkSize;
    private final int chunkSizePower;
  
    private int curBufIndex;
  
    private ByteBuffer curBuf; // redundant for speed: buffers[curBufIndex]
  
    private boolean isClone = false;
    private final WeakIdentityMap<MMapIndexInput,Boolean> clones = WeakIdentityMap.newConcurrentHashMap();

    MMapIndexInput(String resourceDescription, RandomAccessFile raf, int chunkSizePower) throws IOException {
      super(resourceDescription);
      this.length = raf.length();
      this.chunkSizePower = chunkSizePower;
      this.chunkSize = 1L << chunkSizePower;
      this.chunkSizeMask = chunkSize - 1L;
      
      if (chunkSizePower < 0 || chunkSizePower > 30)
        throw new IllegalArgumentException("Invalid chunkSizePower used for ByteBuffer size: " + chunkSizePower);
      
      if ((length >>> chunkSizePower) >= Integer.MAX_VALUE)
        throw new IllegalArgumentException("RandomAccessFile too big for chunk size: " + raf.toString());
      
      // we always allocate one more buffer, the last one may be a 0 byte one
      final int nrBuffers = (int) (length >>> chunkSizePower) + 1;
      
      //System.out.println("length="+length+", chunkSizePower=" + chunkSizePower + ", chunkSizeMask=" + chunkSizeMask + ", nrBuffers=" + nrBuffers);
      
      this.buffers = new ByteBuffer[nrBuffers];
      
      long bufferStart = 0L;
      FileChannel rafc = raf.getChannel();
      for (int bufNr = 0; bufNr < nrBuffers; bufNr++) { 
        int bufSize = (int) ( (length > (bufferStart + chunkSize))
          ? chunkSize
          : (length - bufferStart)
        );
        this.buffers[bufNr] = rafc.map(MapMode.READ_ONLY, bufferStart, bufSize);
        bufferStart += bufSize;
      }
      seek(0L);
    }
  
    @Override
    public byte readByte() throws IOException {
      try {
        return curBuf.get();
      } catch (BufferUnderflowException e) {
        do {
          curBufIndex++;
          if (curBufIndex >= buffers.length) {
            throw new EOFException("read past EOF: " + this);
          }
          curBuf = buffers[curBufIndex];
          curBuf.position(0);
        } while (!curBuf.hasRemaining());
        return curBuf.get();
      } catch (NullPointerException npe) {
        throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
      }
    }
  
    @Override
    public void readBytes(byte[] b, int offset, int len) throws IOException {
      try {
        curBuf.get(b, offset, len);
      } catch (BufferUnderflowException e) {
        int curAvail = curBuf.remaining();
        while (len > curAvail) {
          curBuf.get(b, offset, curAvail);
          len -= curAvail;
          offset += curAvail;
          curBufIndex++;
          if (curBufIndex >= buffers.length) {
            throw new EOFException("read past EOF: " + this);
          }
          curBuf = buffers[curBufIndex];
          curBuf.position(0);
          curAvail = curBuf.remaining();
        }
        curBuf.get(b, offset, len);
      } catch (NullPointerException npe) {
        throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
      }
    }

    @Override
    public int readInt() throws IOException {
      try {
        return curBuf.getInt();
      } catch (BufferUnderflowException e) {
        return super.readInt();
      } catch (NullPointerException npe) {
        throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
      }
    }

    @Override
    public long readLong() throws IOException {
      try {
        return curBuf.getLong();
      } catch (BufferUnderflowException e) {
        return super.readLong();
      } catch (NullPointerException npe) {
        throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
      }
    }
    
    @Override
    public long getFilePointer() {
      try {
        return (((long) curBufIndex) << chunkSizePower) + curBuf.position();
      } catch (NullPointerException npe) {
        throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
      }
    }
  
    @Override
    public void seek(long pos) throws IOException {
      // we use >> here to preserve negative, so we will catch AIOOBE:
      final int bi = (int) (pos >> chunkSizePower);
      try {
        final ByteBuffer b = buffers[bi];
        b.position((int) (pos & chunkSizeMask));
        // write values, on exception all is unchanged
        this.curBufIndex = bi;
        this.curBuf = b;
      } catch (ArrayIndexOutOfBoundsException aioobe) {
        if (pos < 0L) {
          throw new IllegalArgumentException("Seeking to negative position: " + this);
        }
        throw new EOFException("seek past EOF: " + this);
      } catch (IllegalArgumentException iae) {
        if (pos < 0L) {
          throw new IllegalArgumentException("Seeking to negative position: " + this);
        }
        throw new EOFException("seek past EOF: " + this);
      } catch (NullPointerException npe) {
        throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
      }
    }
  
    @Override
    public long length() {
      return length;
    }
  
    @Override
    public Object clone() {
      if (buffers == null) {
        throw new AlreadyClosedException("MMapIndexInput already closed: " + this);
      }
      final MMapIndexInput clone = (MMapIndexInput)super.clone();
      clone.isClone = true;
      // we keep clone.clones, so it shares the same map with original and we have no additional cost on clones
      assert clone.clones == this.clones;
      clone.buffers = new ByteBuffer[buffers.length];
      for (int bufNr = 0; bufNr < buffers.length; bufNr++) {
        clone.buffers[bufNr] = buffers[bufNr].duplicate();
      }
      try {
        clone.seek(getFilePointer());
      } catch(IOException ioe) {
        throw new RuntimeException("Should never happen: " + this, ioe);
      }
      
      // register the new clone in our clone list to clean it up on closing:
      this.clones.put(clone, Boolean.TRUE);
      
      return clone;
    }
    
    private void unsetBuffers() {
      buffers = null;
      curBuf = null;
      curBufIndex = 0;
    }
  
    @Override
    public void close() throws IOException {
      try {
        if (isClone || buffers == null) return;
        
        // make local copy, then un-set early
        final ByteBuffer[] bufs = buffers;
        unsetBuffers();
        
        // for extra safety unset also all clones' buffers:
        for (Iterator<MMapIndexInput> it = this.clones.keyIterator(); it.hasNext();) {
          final MMapIndexInput clone = it.next();
          assert clone.isClone;
          clone.unsetBuffers();
        }
        this.clones.clear();
        
        for (final ByteBuffer b : bufs) {
          cleanMapping(b);
        }
      } finally {
        unsetBuffers();
      }
    }
  }
}
