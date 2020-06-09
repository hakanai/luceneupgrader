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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.store;

 
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.methodType;

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
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Future;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.ByteBufferGuard.BufferCleaner;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.SuppressForbidden;

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
  
  public void setUseUnmap(final boolean useUnmapHack) {
    if (useUnmapHack && !UNMAP_SUPPORTED) {
      throw new IllegalArgumentException(UNMAP_NOT_SUPPORTED_REASON);
    }
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
    ensureCanRead(name);
    Path path = directory.resolve(name);
    try (FileChannel c = FileChannel.open(path, StandardOpenOption.READ)) {
      final String resourceDescription = "MMapIndexInput(path=\"" + path.toString() + "\")";
      final boolean useUnmap = getUseUnmap();
      return ByteBufferIndexInput.newInstance(resourceDescription,
          map(resourceDescription, c, 0, c.size()), 
          c.size(), chunkSizePower, new ByteBufferGuard(resourceDescription, useUnmap ? CLEANER : null));
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
  
  public static final boolean UNMAP_SUPPORTED;
  
  public static final String UNMAP_NOT_SUPPORTED_REASON;
  
  private static final BufferCleaner CLEANER;
  
  static {
    final Object hack = AccessController.doPrivileged((PrivilegedAction<Object>) MMapDirectory::unmapHackImpl);
    if (hack instanceof BufferCleaner) {
      CLEANER = (BufferCleaner) hack;
      UNMAP_SUPPORTED = true;
      UNMAP_NOT_SUPPORTED_REASON = null;
    } else {
      CLEANER = null;
      UNMAP_SUPPORTED = false;
      UNMAP_NOT_SUPPORTED_REASON = hack.toString();
    }
  }
  
  @SuppressForbidden(reason = "Needs access to private APIs in DirectBuffer, sun.misc.Cleaner, and sun.misc.Unsafe to enable hack")
  private static Object unmapHackImpl() {
    final Lookup lookup = lookup();
    try {
      try {
        // *** sun.misc.Unsafe unmapping (Java 9+) ***
        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        // first check if Unsafe has the right method, otherwise we can give up
        // without doing any security critical stuff:
        final MethodHandle unmapper = lookup.findVirtual(unsafeClass, "invokeCleaner",
            methodType(void.class, ByteBuffer.class));
        // fetch the unsafe instance and bind it to the virtual MH:
        final Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        final Object theUnsafe = f.get(null);
        return newBufferCleaner(ByteBuffer.class, unmapper.bindTo(theUnsafe));
      } catch (SecurityException se) {
        // rethrow to report errors correctly (we need to catch it here, as we also catch RuntimeException below!):
        throw se;
      } catch (ReflectiveOperationException | RuntimeException e) {
        // *** sun.misc.Cleaner unmapping (Java 8) ***
        final Class<?> directBufferClass = Class.forName("java.nio.DirectByteBuffer");
        
        final Method m = directBufferClass.getMethod("cleaner");
        m.setAccessible(true);
        final MethodHandle directBufferCleanerMethod = lookup.unreflect(m);
        final Class<?> cleanerClass = directBufferCleanerMethod.type().returnType();
        
        /* "Compile" a MH that basically is equivalent to the following code:
         * void unmapper(ByteBuffer byteBuffer) {
         *   sun.misc.Cleaner cleaner = ((java.nio.DirectByteBuffer) byteBuffer).cleaner();
         *   if (Objects.nonNull(cleaner)) {
         *     cleaner.clean();
         *   } else {
         *     noop(cleaner); // the noop is needed because MethodHandles#guardWithTest always needs ELSE
         *   }
         * }
         */
        final MethodHandle cleanMethod = lookup.findVirtual(cleanerClass, "clean", methodType(void.class));
        final MethodHandle nonNullTest = lookup.findStatic(Objects.class, "nonNull", methodType(boolean.class, Object.class))
            .asType(methodType(boolean.class, cleanerClass));
        final MethodHandle noop = dropArguments(constant(Void.class, null).asType(methodType(void.class)), 0, cleanerClass);
        final MethodHandle unmapper = filterReturnValue(directBufferCleanerMethod, guardWithTest(nonNullTest, cleanMethod, noop))
            .asType(methodType(void.class, ByteBuffer.class));
        return newBufferCleaner(directBufferClass, unmapper);
      }
    } catch (SecurityException se) {
      return "Unmapping is not supported, because not all required permissions are given to the Lucene JAR file: " + se +
          " [Please grant at least the following permissions: RuntimePermission(\"accessClassInPackage.sun.misc\") " +
          " and ReflectPermission(\"suppressAccessChecks\")]";
    } catch (ReflectiveOperationException | RuntimeException e) {
      return "Unmapping is not supported on this platform, because internal Java APIs are not compatible with this Lucene version: " + e; 
    }
  }
  
  private static BufferCleaner newBufferCleaner(final Class<?> unmappableBufferClass, final MethodHandle unmapper) {
    assert Objects.equals(methodType(void.class, ByteBuffer.class), unmapper.type());
    return (String resourceDescription, ByteBuffer buffer) -> {
      if (!buffer.isDirect()) {
        throw new IllegalArgumentException("unmapping only works with direct buffers");
      }
      if (!unmappableBufferClass.isInstance(buffer)) {
        throw new IllegalArgumentException("buffer is not an instance of " + unmappableBufferClass.getName());
      }
      final Throwable error = AccessController.doPrivileged((PrivilegedAction<Throwable>) () -> {
        try {
          unmapper.invokeExact(buffer);
          return null;
        } catch (Throwable t) {
          return t;
        }
      });
      if (error != null) {
        throw new IOException("Unable to unmap the mapped buffer: " + resourceDescription, error);
      }
    };
  }
  
}
