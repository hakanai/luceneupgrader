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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.util;


import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.ServiceConfigurationError;

public final class SPIClassIterator<S> implements Iterator<Class<? extends S>> {
  private static final String META_INF_SERVICES = "META-INF/services/";

  private final Class<S> clazz;
  private final ClassLoader loader;
  private final Enumeration<URL> profilesEnum;
  private Iterator<String> linesIterator;
  
  public static <S> SPIClassIterator<S> get(Class<S> clazz) {
    return new SPIClassIterator<>(clazz,
        Objects.requireNonNull(clazz.getClassLoader(), () -> clazz + " has no classloader."));
  }
  
  public static <S> SPIClassIterator<S> get(Class<S> clazz, ClassLoader loader) {
    return new SPIClassIterator<>(clazz, loader);
  }
  
  public static boolean isParentClassLoader(final ClassLoader parent, final ClassLoader child) {
    try {
      ClassLoader cl = child;
      while (cl != null) {
        if (cl == parent) {
          return true;
        }
        cl = cl.getParent();
      }
      return false;
    } catch (SecurityException se) {
      return false;
    }
  }
  
  private SPIClassIterator(Class<S> clazz, ClassLoader loader) {
    this.clazz = Objects.requireNonNull(clazz, "clazz");
    this.loader = Objects.requireNonNull(loader, "loader");
    try {
      final String fullName = META_INF_SERVICES + clazz.getName();
      this.profilesEnum = loader.getResources(fullName);
    } catch (IOException ioe) {
      throw new ServiceConfigurationError("Error loading SPI profiles for type " + clazz.getName() + " from classpath", ioe);
    }
    this.linesIterator = Collections.<String>emptySet().iterator();
  }
  
  private boolean loadNextProfile() {
    ArrayList<String> lines = null;
    while (profilesEnum.hasMoreElements()) {
      if (lines != null) {
        lines.clear();
      } else {
        lines = new ArrayList<>();
      }
      final URL url = profilesEnum.nextElement();
      try {
        final InputStream in = url.openStream();
        boolean success = false;
        try {
          final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
          String line;
          while ((line = reader.readLine()) != null) {
            final int pos = line.indexOf('#');
            if (pos >= 0) {
              line = line.substring(0, pos);
            }
            line = line.trim();
            if (line.length() > 0) {
              lines.add(line);
            }
          }
          success = true;
        } finally {
          if (success) {
            IOUtils.close(in);
          } else {
            IOUtils.closeWhileHandlingException(in);
          }
        }
      } catch (IOException ioe) {
        throw new ServiceConfigurationError("Error loading SPI class list from URL: " + url, ioe);
      }
      if (!lines.isEmpty()) {
        this.linesIterator = lines.iterator();
        return true;
      }
    }
    return false;
  }
  
  @Override
  public boolean hasNext() {
    return linesIterator.hasNext() || loadNextProfile();
  }
  
  @Override
  public Class<? extends S> next() {
    // hasNext() implicitely loads the next profile, so it is essential to call this here!
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    assert linesIterator.hasNext();
    final String c = linesIterator.next();
    try {
      // don't initialize the class (pass false as 2nd parameter):
      return Class.forName(c, false, loader).asSubclass(clazz);
    } catch (ClassNotFoundException cnfe) {
      throw new ServiceConfigurationError(String.format(Locale.ROOT, "An SPI class of type %s with classname %s does not exist, "+
        "please fix the file '%s%1$s' in your classpath.", clazz.getName(), c, META_INF_SERVICES));
    }
  }
  
  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
  
}
