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


import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;

public abstract class AttributeImpl implements Cloneable, Attribute {
  public abstract void clear();
  
  public final String reflectAsString(final boolean prependAttClass) {
    final StringBuilder buffer = new StringBuilder();
    reflectWith(new AttributeReflector() {
      @Override
      public void reflect(Class<? extends Attribute> attClass, String key, Object value) {
        if (buffer.length() > 0) {
          buffer.append(',');
        }
        if (prependAttClass) {
          buffer.append(attClass.getName()).append('#');
        }
        buffer.append(key).append('=').append((value == null) ? "null" : value);
      }
    });
    return buffer.toString();
  }
  
  public void reflectWith(AttributeReflector reflector) {
    final Class<? extends AttributeImpl> clazz = this.getClass();
    final Class<? extends Attribute>[] interfaces = AttributeSource.getAttributeInterfaces(clazz);
    if (interfaces.length != 1) {
      throw new UnsupportedOperationException(clazz.getName() +
        " implements more than one Attribute interface, the default reflectWith() implementation cannot handle this.");
    }
    final Class<? extends Attribute> interf = interfaces[0];
    final Field[] fields = clazz.getDeclaredFields();
    for (final Field f : fields) {
      if (Modifier.isStatic(f.getModifiers())) continue;
      reflector.reflect(interf, f.getName(), AccessController.doPrivileged(new PrivilegedAction<Object>() {
        @Override
        @SuppressForbidden(reason = "This methods needs to access private attribute fields. Method will be abstract in 6.x")
        public Object run() {
          try {
            f.setAccessible(true);
            return f.get(AttributeImpl.this);
          } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access private fields.", e);
          }
        }
      }));
    }
  }
  
  public abstract void copyTo(AttributeImpl target);

  @Override
  public AttributeImpl clone() {
    AttributeImpl clone = null;
    try {
      clone = (AttributeImpl)super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);  // shouldn't happen
    }
    return clone;
  }
}
