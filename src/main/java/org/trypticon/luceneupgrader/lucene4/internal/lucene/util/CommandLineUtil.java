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

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.FSDirectory;

public final class CommandLineUtil {
  
  private CommandLineUtil() {
    
  }
  
  public static FSDirectory newFSDirectory(String clazzName, File file) {
    try {
      final Class<? extends FSDirectory> clazz = loadFSDirectoryClass(clazzName);
      return newFSDirectory(clazz, file);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(FSDirectory.class.getSimpleName()
          + " implementation not found: " + clazzName, e);
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(clazzName + " is not a " + FSDirectory.class.getSimpleName()
          + " implementation", e);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(clazzName + " constructor with "
          + File.class.getSimpleName() + " as parameter not found", e);
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
  
  public static FSDirectory newFSDirectory(Class<? extends FSDirectory> clazz, File file)
      throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
    // Assuming every FSDirectory has a ctor(File):
    Constructor<? extends FSDirectory> ctor = clazz.getConstructor(File.class);
    return ctor.newInstance(file);
  }
  
}
