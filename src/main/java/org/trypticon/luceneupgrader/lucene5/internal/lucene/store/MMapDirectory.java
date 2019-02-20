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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.store;

 
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException; // javadoc @link
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Future;
import java.lang.reflect.Method;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.ByteBufferIndexInput.BufferCleaner;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.SuppressForbidden;

public class MMapDirectory extends FSDirectory {
  private boolean useUnmapHack = UNMAP_SUPPORTED;
  private boolean preload;


  public static final int DEFAULT_MAX_CHUNK_SIZE = Constants.JRE_IS_64BIT ? (1 << 30) : (1 << 28);
  final int chunkSizePower;


  public MMapDirectory(Path path, LockFactory lockFactory) throws IOException {
    this(path, lockFactory, DEFAULT_MAX_CHUNK_SIZE);
  }

  public MMapDirectory(Path path) throws IOException {
    this(path, FSLockFactory.getDefault());
  }
  
  public MMapDirectory(Path path, int maxChunkSize) throws IOException {
    this(path, FSLockFactory.getDefault(), maxChunkSize);
  }
  
  public MMapDirectory(Path path, LockFactory lockFactory, int maxChunkSize) throws IOException {
    super(path, lockFactory);
    if (maxChunkSize <= 0) {
      throw new IllegalArgumentException("Maximum chunk size for mmap must be >0");
    }
    this.chunkSizePower = 31 - Integer.numberOfLeadingZeros(maxChunkSize);
    assert this.chunkSizePower >= 0 && this.chunkSizePower <= 30;
  }
  
  public static final boolean UNMAP_SUPPORTED = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
    @Override
    @SuppressForbidden(reason = "Needs access to private APIs in DirectBuffer and sun.misc.Cleaner to enable hack")
    public Boolean run() {
      try {
        // inspect DirectByteBuffer:
        final Class<?> dbClazz = Class.forName("java.nio.DirectByteBuffer");
        final Method cleanerMethod = dbClazz.getMethod("cleaner");
        cleanerMethod.setAccessible(true);
        // try to lookup the clean method from sun.misc.Cleaner:
        final Class<?> cleanerClazz = Class.forName("sun.misc.Cleaner");
        cleanerClazz.getMethod("clean"); // no setAccessible needed as everything is public!
        // check return type fits cleaner class return our decision:
        return Objects.equals(cleanerMethod.getReturnType(), cleanerClazz);
      } catch (Exception e) {
        return false;
      }
    }
  });
  
  public void setUseUnmap(final boolean useUnmapHack) {
    if (useUnmapHack && !UNMAP_SUPPORTED)
      throw new IllegalArgumentException("Unmap hack not supported on this platform!");
    this.useUnmapHack=useUnmapHack;
  }
  
  public boolean getUseUnmap() {
    return useUnmapHack;
  }
  
  public void setPreload(boolean preload) {
    this.preload = preload;
  }
  
  public boolean getPreload() {
    return preload;
  }
  
  public final int getMaxChunkSize() {
    return 1 << chunkSizePower;
  }

  @Override
  public IndexInput openInput(String name, IOContext context) throws IOException {
    ensureOpen();
    Path path = directory.resolve(name);
    try (FileChannel c = FileChannel.open(path, StandardOpenOption.READ)) {
      final String resourceDescription = "MMapIndexInput(path=\"" + path.toString() + "\")";
      final boolean useUnmap = getUseUnmap();
      return ByteBufferIndexInput.newInstance(resourceDescription,
          map(resourceDescription, c, 0, c.size()), 
          c.size(), chunkSizePower, useUnmap ? CLEANER : null, useUnmap);
    }
  }

  final ByteBuffer[] map(String resourceDescription, FileChannel fc, long offset, long length) throws IOException {
    if ((length >>> chunkSizePower) >= Integer.MAX_VALUE)
      throw new IllegalArgumentException("RandomAccessFile too big for chunk size: " + resourceDescription);
    
    final long chunkSize = 1L << chunkSizePower;
    
    // we always allocate one more buffer, the last one may be a 0 byte one
    final int nrBuffers = (int) (length >>> chunkSizePower) + 1;
    
    ByteBuffer buffers[] = new ByteBuffer[nrBuffers];
    
    long bufferStart = 0L;
    for (int bufNr = 0; bufNr < nrBuffers; bufNr++) { 
      int bufSize = (int) ( (length > (bufferStart + chunkSize))
          ? chunkSize
              : (length - bufferStart)
          );
      MappedByteBuffer buffer;
      try {
        buffer = fc.map(MapMode.READ_ONLY, offset + bufferStart, bufSize);
      } catch (IOException ioe) {
        throw convertMapFailedIOException(ioe, resourceDescription, bufSize);
      }
      if (preload) {
        buffer.load();
      }
      buffers[bufNr] = buffer;
      bufferStart += bufSize;
    }
    
    return buffers;
  }
  
  private IOException convertMapFailedIOException(IOException ioe, String resourceDescription, int bufSize) {
    final String originalMessage;
    final Throwable originalCause;
    if (ioe.getCause() instanceof OutOfMemoryError) {
      // nested OOM confuses users, because it's "incorrect", just print a plain message:
      originalMessage = "Map failed";
      originalCause = null;
    } else {
      originalMessage = ioe.getMessage();
      originalCause = ioe.getCause();
    }
    final String moreInfo;
    if (!Constants.JRE_IS_64BIT) {
      moreInfo = "MMapDirectory should only be used on 64bit platforms, because the address space on 32bit operating systems is too small. ";
    } else if (Constants.WINDOWS) {
      moreInfo = "Windows is unfortunately very limited on virtual address space. If your index size is several hundred Gigabytes, consider changing to Linux. ";
    } else if (Constants.LINUX) {
      moreInfo = "Please review 'ulimit -v', 'ulimit -m' (both should return 'unlimited'), and 'sysctl vm.max_map_count'. ";
    } else {
      moreInfo = "Please review 'ulimit -v', 'ulimit -m' (both should return 'unlimited'). ";
    }
    final IOException newIoe = new IOException(String.format(Locale.ENGLISH,
        "%s: %s [this may be caused by lack of enough unfragmented virtual address space "+
        "or too restrictive virtual memory limits enforced by the operating system, "+
        "preventing us to map a chunk of %d bytes. %sMore information: "+
        "http://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html]",
        originalMessage, resourceDescription, bufSize, moreInfo), originalCause);
    newIoe.setStackTrace(ioe.getStackTrace());
    return newIoe;
  }
  
  private static final BufferCleaner CLEANER = new BufferCleaner() {
    @Override
    public void freeBuffer(final ByteBufferIndexInput parent, final ByteBuffer buffer) throws IOException {
      try {
        AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
          @Override
          @SuppressForbidden(reason = "Needs access to private APIs in DirectBuffer and sun.misc.Cleaner to enable hack")
          public Void run() throws Exception {
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
        throw new IOException("Unable to unmap the mapped buffer: " + parent.toString(), e.getCause());
      }
    }
  };
}
