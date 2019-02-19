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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.util;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;


public final class IOUtils {
  
  @Deprecated
  public static final Charset CHARSET_UTF_8 = StandardCharsets.UTF_8;
  
  public static final String UTF_8 = StandardCharsets.UTF_8.name();
  
  private IOUtils() {} // no instance

  public static void close(Closeable... objects) throws IOException {
    Throwable th = null;

    for (Closeable object : objects) {
      try {
        if (object != null) {
          object.close();
        }
      } catch (Throwable t) {
        addSuppressed(th, t);
        if (th == null) {
          th = t;
        }
      }
    }

    reThrow(th);
  }
  
  public static void close(Iterable<? extends Closeable> objects) throws IOException {
    Throwable th = null;

    for (Closeable object : objects) {
      try {
        if (object != null) {
          object.close();
        }
      } catch (Throwable t) {
        addSuppressed(th, t);
        if (th == null) {
          th = t;
        }
      }
    }

    reThrow(th);
  }

  public static void closeWhileHandlingException(Closeable... objects) {
    for (Closeable object : objects) {
      try {
        if (object != null) {
          object.close();
        }
      } catch (Throwable t) {
      }
    }
  }
  
  public static void closeWhileHandlingException(Iterable<? extends Closeable> objects) {
    for (Closeable object : objects) {
      try {
        if (object != null) {
          object.close();
        }
      } catch (Throwable t) {
      }
    }
  }
  

  private static void addSuppressed(Throwable exception, Throwable suppressed) {
    if (exception != null && suppressed != null) {
      exception.addSuppressed(suppressed);
    }
  }
  
  public static Reader getDecodingReader(InputStream stream, Charset charSet) {
    final CharsetDecoder charSetDecoder = charSet.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    return new BufferedReader(new InputStreamReader(stream, charSetDecoder));
  }
  
  public static Reader getDecodingReader(File file, Charset charSet) throws IOException {
    FileInputStream stream = null;
    boolean success = false;
    try {
      stream = new FileInputStream(file);
      final Reader reader = getDecodingReader(stream, charSet);
      success = true;
      return reader;

    } finally {
      if (!success) {
        IOUtils.close(stream);
      }
    }
  }

  public static Reader getDecodingReader(Class<?> clazz, String resource, Charset charSet) throws IOException {
    InputStream stream = null;
    boolean success = false;
    try {
      stream = clazz
      .getResourceAsStream(resource);
      final Reader reader = getDecodingReader(stream, charSet);
      success = true;
      return reader;
    } finally {
      if (!success) {
        IOUtils.close(stream);
      }
    }
  }
  
  public static void deleteFilesIgnoringExceptions(Directory dir, String... files) {
    for (String name : files) {
      try {
        dir.deleteFile(name);
      } catch (Throwable ignored) {
        // ignore
      }
    }
  }

  public static void copy(File source, File target) throws IOException {
    FileInputStream fis = null;
    FileOutputStream fos = null;
    try {
      fis = new FileInputStream(source);
      fos = new FileOutputStream(target);
      
      final byte [] buffer = new byte [1024 * 8];
      int len;
      while ((len = fis.read(buffer)) > 0) {
        fos.write(buffer, 0, len);
      }
    } finally {
      close(fis, fos);
    }
  }

  public static void reThrow(Throwable th) throws IOException {
    if (th != null) {
      if (th instanceof IOException) {
        throw (IOException) th;
      }
      reThrowUnchecked(th);
    }
  }

  public static void reThrowUnchecked(Throwable th) {
    if (th != null) {
      if (th instanceof RuntimeException) {
        throw (RuntimeException) th;
      }
      if (th instanceof Error) {
        throw (Error) th;
      }
      throw new RuntimeException(th);
    }
  }

  public static void fsync(File fileToSync, boolean isDir) throws IOException {
    IOException exc = null;
    
    // If the file is a directory we have to open read-only, for regular files we must open r/w for the fsync to have an effect.
    // See http://blog.httrack.com/blog/2013/11/15/everything-you-always-wanted-to-know-about-fsync/
    try (final FileChannel file = FileChannel.open(fileToSync.toPath(), isDir ? StandardOpenOption.READ : StandardOpenOption.WRITE)) {
      for (int retry = 0; retry < 5; retry++) {
        try {
          file.force(true);
          return;
        } catch (IOException ioe) {
          if (exc == null) {
            exc = ioe;
          }
          try {
            // Pause 5 msec
            Thread.sleep(5L);
          } catch (InterruptedException ie) {
            ThreadInterruptedException ex = new ThreadInterruptedException(ie);
            ex.addSuppressed(exc);
            throw ex;
          }
        }
      }
    } catch (IOException ioe) {
      if (exc == null) {
        exc = ioe;
      }
    }
    
    if (isDir) {
      // TODO: LUCENE-6169 - Fix this assert once Java 9 problems are solved!
      assert (Constants.LINUX || Constants.MAC_OS_X) == false || Constants.JRE_IS_MINIMUM_JAVA9 :
        "On Linux and MacOSX fsyncing a directory should not throw IOException, "+
        "we just don't want to rely on that in production (undocumented). Got: " + exc;
      // Ignore exception if it is a directory
      return;
    }
    
    // Throw original exception
    throw exc;
  }
}
