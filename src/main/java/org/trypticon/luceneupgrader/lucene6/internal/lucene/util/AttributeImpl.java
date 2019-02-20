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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.util;


public abstract class AttributeImpl implements Cloneable, Attribute {
  public abstract void clear();
  
  public void end() {
    clear();
  }
  
  public final String reflectAsString(final boolean prependAttClass) {
    final StringBuilder buffer = new StringBuilder();
    reflectWith((attClass, key, value) -> {
      if (buffer.length() > 0) {
        buffer.append(',');
      }
      if (prependAttClass) {
        buffer.append(attClass.getName()).append('#');
      }
      buffer.append(key).append('=').append((value == null) ? "null" : value);
    });
    return buffer.toString();
  }
  
  public abstract void reflectWith(AttributeReflector reflector);
  
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
