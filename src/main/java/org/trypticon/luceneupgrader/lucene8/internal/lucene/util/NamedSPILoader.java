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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.util;


import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.ServiceConfigurationError;

public final class NamedSPILoader<S extends NamedSPILoader.NamedSPI> implements Iterable<S> {

  private volatile Map<String,S> services = Collections.emptyMap();
  private final Class<S> clazz;

  public NamedSPILoader(Class<S> clazz) {
    this(clazz, null);
  }
  
  public NamedSPILoader(Class<S> clazz, ClassLoader classloader) {
    this.clazz = clazz;
    // if clazz' classloader is not a parent of the given one, we scan clazz's classloader, too:
    final ClassLoader clazzClassloader = clazz.getClassLoader();
    if (classloader == null) {
      classloader = clazzClassloader;
    }
    if (clazzClassloader != null && !SPIClassIterator.isParentClassLoader(clazzClassloader, classloader)) {
      reload(clazzClassloader);
    }
    reload(classloader);
  }
  
  public void reload(ClassLoader classloader) {
    Objects.requireNonNull(classloader, "classloader");
    final LinkedHashMap<String,S> services = new LinkedHashMap<>(this.services);
    final SPIClassIterator<S> loader = SPIClassIterator.get(clazz, classloader);
    while (loader.hasNext()) {
      final Class<? extends S> c = loader.next();
      try {
        final S service = c.newInstance();
        final String name = service.getName();
        // only add the first one for each name, later services will be ignored
        // this allows to place services before others in classpath to make 
        // them used instead of others
        if (!services.containsKey(name)) {
          checkServiceName(name);
          services.put(name, service);
        }
      } catch (Exception e) {
        throw new ServiceConfigurationError("Cannot instantiate SPI class: " + c.getName(), e);
      }
    }
    this.services = Collections.unmodifiableMap(services);
  }
  
  public static void checkServiceName(String name) {
    // based on harmony charset.java
    if (name.length() >= 128) {
      throw new IllegalArgumentException("Illegal service name: '" + name + "' is too long (must be < 128 chars).");
    }
    for (int i = 0, len = name.length(); i < len; i++) {
      char c = name.charAt(i);
      if (!isLetterOrDigit(c)) {
        throw new IllegalArgumentException("Illegal service name: '" + name + "' must be simple ascii alphanumeric.");
      }
    }
  }
  
  private static boolean isLetterOrDigit(char c) {
    return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9');
  }
  
  public S lookup(String name) {
    final S service = services.get(name);
    if (service != null) return service;
    throw new IllegalArgumentException("An SPI class of type "+clazz.getName()+" with name '"+name+"' does not exist."+
     "  You need to add the corresponding JAR file supporting this SPI to your classpath."+
     "  The current classpath supports the following names: "+availableServices());
  }

  public Set<String> availableServices() {
    return services.keySet();
  }
  
  @Override
  public Iterator<S> iterator() {
    return services.values().iterator();
  }
  
  public static interface NamedSPI {
    String getName();
  }
  
}
