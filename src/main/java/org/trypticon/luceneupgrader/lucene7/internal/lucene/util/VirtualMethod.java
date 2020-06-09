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


import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class VirtualMethod<C> {

  private static final Set<Method> singletonSet = Collections.synchronizedSet(new HashSet<Method>());

  private final Class<C> baseClass;
  private final String method;
  private final Class<?>[] parameters;
  private final ClassValue<Integer> distanceOfClass = new ClassValue<Integer>() {
    @Override
    protected Integer computeValue(Class<?> subclazz) {
      return Integer.valueOf(reflectImplementationDistance(subclazz));
    }
  };

  public VirtualMethod(Class<C> baseClass, String method, Class<?>... parameters) {
    this.baseClass = baseClass;
    this.method = method;
    this.parameters = parameters;
    try {
      if (!singletonSet.add(baseClass.getDeclaredMethod(method, parameters)))
        throw new UnsupportedOperationException(
          "VirtualMethod instances must be singletons and therefore " +
          "assigned to static final members in the same class, they use as baseClass ctor param."
        );
    } catch (NoSuchMethodException nsme) {
      throw new IllegalArgumentException(baseClass.getName() + " has no such method: "+nsme.getMessage());
    }
  }
  
  public int getImplementationDistance(final Class<? extends C> subclazz) {
    return distanceOfClass.get(subclazz).intValue();
  }
  
  public boolean isOverriddenAsOf(final Class<? extends C> subclazz) {
    return getImplementationDistance(subclazz) > 0;
  }
  
  int reflectImplementationDistance(final Class<?> subclazz) {
    if (!baseClass.isAssignableFrom(subclazz))
      throw new IllegalArgumentException(subclazz.getName() + " is not a subclass of " + baseClass.getName());
    boolean overridden = false;
    int distance = 0;
    for (Class<?> clazz = subclazz; clazz != baseClass && clazz != null; clazz = clazz.getSuperclass()) {
      // lookup method, if success mark as overridden
      if (!overridden) {
        try {
          clazz.getDeclaredMethod(method, parameters);
          overridden = true;
        } catch (NoSuchMethodException nsme) {
        }
      }
      
      // increment distance if overridden
      if (overridden) distance++;
    }
    return distance;
  }
  
  public static <C> int compareImplementationDistance(final Class<? extends C> clazz,
    final VirtualMethod<C> m1, final VirtualMethod<C> m2)
  {
    return Integer.compare(m1.getImplementationDistance(clazz), m2.getImplementationDistance(clazz));
  }
  
}
