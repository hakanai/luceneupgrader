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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Simple {@link ResourceLoader} that uses {@link ClassLoader#getResourceAsStream(String)} and
 * {@link Class#forName(String,boolean,ClassLoader)} to open resources and classes, respectively.
 *
 * <p>To use this class with the Java Module System, you must open all modules on classpath that
 * contain resources to the {@code org.apache.lucene.core} module, otherwise resources can't be
 * looked up. It is recommended to use {@link ModuleResourceLoader} for such use cases as this would
 * limit to certain modules.
 *
 * @see ModuleResourceLoader
 */
public final class ClasspathResourceLoader implements ResourceLoader {
  private final Class<?> clazz;
  private final ClassLoader loader;

  /**
   * Creates an instance using the given classloader to load Resources and classes. Resource paths
   * must be absolute.
   */
  public ClasspathResourceLoader(ClassLoader loader) {
    this(null, loader);
  }

  /**
   * Creates an instance using the context classloader to load Resources and classes Resources are
   * resolved relative to the given class, if path is not absolute.
   */
  public ClasspathResourceLoader(Class<?> clazz) {
    this(clazz, clazz.getClassLoader());
  }

  private ClasspathResourceLoader(Class<?> clazz, ClassLoader loader) {
    this.clazz = clazz;
    this.loader = loader;
  }

  @Override
  public InputStream openResource(String resource) throws IOException {
    final InputStream stream =
        (clazz != null)
            ? clazz.getResourceAsStream(resource)
            : loader.getResourceAsStream(resource);
    if (stream == null) {
      throw new IOException(
          "Resource not found (if you use Java Module System, make sure to open "
              + "module and package containing resources to 'org.apache.lucene.core' module): "
              + resource);
    }
    return stream;
  }

  @Override
  public <T> Class<? extends T> findClass(String cname, Class<T> expectedType) {
    try {
      return Class.forName(cname, true, loader).asSubclass(expectedType);
    } catch (Exception e) {
      throw new RuntimeException("Cannot load class: " + cname, e);
    }
  }
}
