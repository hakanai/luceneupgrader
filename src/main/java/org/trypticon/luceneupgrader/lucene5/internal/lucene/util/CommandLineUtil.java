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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.util;


import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.FSDirectory;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.FSLockFactory;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.LockFactory;

public final class CommandLineUtil {
  
  private CommandLineUtil() {
    
  }
  
  public static FSDirectory newFSDirectory(String clazzName, Path path) {
    return newFSDirectory(clazzName, path, FSLockFactory.getDefault());
  }
  
  public static FSDirectory newFSDirectory(String clazzName, Path path, LockFactory lf) {
    try {
      final Class<? extends FSDirectory> clazz = loadFSDirectoryClass(clazzName);
      return newFSDirectory(clazz, path, lf);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(FSDirectory.class.getSimpleName()
          + " implementation not found: " + clazzName, e);
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(clazzName + " is not a " + FSDirectory.class.getSimpleName()
          + " implementation", e);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(clazzName + " constructor with "
          + Path.class.getSimpleName() + " as parameter not found", e);
    } catch (Exception e) {
      throw new IllegalArgumentException("Error creating " + clazzName + " instance", e);
    }
  }
  
  public static Class<? extends Directory> loadDirectoryClass(String clazzName)
      throws ClassNotFoundException {
    return Class.forName(adjustDirectoryClassName(clazzName)).asSubclass(Directory.class);
  }
  
  public static Class<? extends FSDirectory> loadFSDirectoryClass(String clazzName)
      throws ClassNotFoundException {
    return Class.forName(adjustDirectoryClassName(clazzName)).asSubclass(FSDirectory.class);
  }
  
  private static String adjustDirectoryClassName(String clazzName) {
    if (clazzName == null || clazzName.trim().length() == 0) {
      throw new IllegalArgumentException("The " + FSDirectory.class.getSimpleName()
          + " implementation cannot be null or empty");
    }
    
    if (clazzName.indexOf(".") == -1) {// if not fully qualified, assume .store
      clazzName = Directory.class.getPackage().getName() + "." + clazzName;
    }
    return clazzName;
  }
  
  public static FSDirectory newFSDirectory(Class<? extends FSDirectory> clazz, Path path)
      throws ReflectiveOperationException {
    return newFSDirectory(clazz, path, FSLockFactory.getDefault());
  }
  
  public static FSDirectory newFSDirectory(Class<? extends FSDirectory> clazz, Path path, LockFactory lf)
      throws ReflectiveOperationException {
    // Assuming every FSDirectory has a ctor(Path):
    Constructor<? extends FSDirectory> ctor = clazz.getConstructor(Path.class, LockFactory.class);
    return ctor.newInstance(path, lf);
  }
  
}
