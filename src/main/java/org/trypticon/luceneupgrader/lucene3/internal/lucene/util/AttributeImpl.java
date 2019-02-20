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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.util;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;

public abstract class AttributeImpl implements Cloneable, Serializable, Attribute {
  public abstract void clear();
  
  // TODO: @deprecated remove this method in 4.0
  @Override
  public String toString() {
    return reflectAsString(false);
  }
  
  public final String reflectAsString(final boolean prependAttClass) {
    final StringBuilder buffer = new StringBuilder();
    reflectWith(new AttributeReflector() {
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
  
  @Deprecated
  private static final VirtualMethod<AttributeImpl> toStringMethod =
    new VirtualMethod<AttributeImpl>(AttributeImpl.class, "toString");
    
  @Deprecated
  protected boolean enableBackwards = true;

  @Deprecated
  private boolean assertExternalClass(Class<? extends AttributeImpl> clazz) {
    final String name = clazz.getName();
    return (!name.startsWith("org.trypticon.luceneupgrader.lucene3.internal.lucene.") && !name.startsWith("org.apache.solr."))
      || name.equals("org.trypticon.luceneupgrader.lucene3.internal.lucene.util.TestAttributeSource$TestAttributeImpl");
  }

  public void reflectWith(AttributeReflector reflector) {
    final Class<? extends AttributeImpl> clazz = this.getClass();
    final LinkedList<WeakReference<Class<? extends Attribute>>> interfaces = AttributeSource.getAttributeInterfaces(clazz);
    if (interfaces.size() != 1) {
      throw new UnsupportedOperationException(clazz.getName() +
        " implements more than one Attribute interface, the default reflectWith() implementation cannot handle this.");
    }
    final Class<? extends Attribute> interf = interfaces.getFirst().get();

    // TODO: @deprecated sophisticated(TM) backwards
    if (enableBackwards && toStringMethod.isOverriddenAsOf(clazz)) {
      assert assertExternalClass(clazz) : "no Lucene/Solr classes should fallback to toString() parsing";
      // this class overrides toString and for backwards compatibility we try to parse the string returned by this method:
      for (String part : toString().split(",")) {
        final int pos = part.indexOf('=');
        if (pos < 0) {
          throw new UnsupportedOperationException("The backwards compatibility layer to support reflectWith() " +
            "on old AtributeImpls expects the toString() implementation to return a correct format as specified for method reflectAsString(false)");
        }
        reflector.reflect(interf, part.substring(0, pos).trim(), part.substring(pos + 1));
      }
      return;
    }
    // end sophisticated(TM) backwards

    final Field[] fields = clazz.getDeclaredFields();
    try {
      for (int i = 0; i < fields.length; i++) {
        final Field f = fields[i];
        if (Modifier.isStatic(f.getModifiers())) continue;
        f.setAccessible(true);
        reflector.reflect(interf, f.getName(), f.get(this));
      }
    } catch (IllegalAccessException e) {
      // this should never happen, because we're just accessing fields
      // from 'this'
      throw new RuntimeException(e);
    }
  }
  
  public abstract void copyTo(AttributeImpl target);
    
  @Override
  public Object clone() {
    Object clone = null;
    try {
      clone = super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);  // shouldn't happen
    }
    return clone;
  }
}
