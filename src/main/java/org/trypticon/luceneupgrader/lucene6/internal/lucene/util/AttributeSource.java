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


import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.analysis.TokenStream; // for javadocs

public class AttributeSource {
  
  public static final class State implements Cloneable {
    AttributeImpl attribute;
    State next;
    
    @Override
    public State clone() {
      State clone = new State();
      clone.attribute = attribute.clone();
      
      if (next != null) {
        clone.next = next.clone();
      }
      
      return clone;
    }
  }
    
  // These two maps must always be in sync!!!
  // So they are private, final and read-only from the outside (read-only iterators)
  private final Map<Class<? extends Attribute>, AttributeImpl> attributes;
  private final Map<Class<? extends AttributeImpl>, AttributeImpl> attributeImpls;
  private final State[] currentState;

  private final AttributeFactory factory;
  
  public AttributeSource() {
    this(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY);
  }
  
  public AttributeSource(AttributeSource input) {
    Objects.requireNonNull(input, "input AttributeSource must not be null");
    this.attributes = input.attributes;
    this.attributeImpls = input.attributeImpls;
    this.currentState = input.currentState;
    this.factory = input.factory;
  }
  
  public AttributeSource(AttributeFactory factory) {
    this.attributes = new LinkedHashMap<>();
    this.attributeImpls = new LinkedHashMap<>();
    this.currentState = new State[1];
    this.factory = Objects.requireNonNull(factory, "AttributeFactory must not be null");
  }
  
  public final AttributeFactory getAttributeFactory() {
    return this.factory;
  }
  

  public final Iterator<Class<? extends Attribute>> getAttributeClassesIterator() {
    return Collections.unmodifiableSet(attributes.keySet()).iterator();
  }
  

  public final Iterator<AttributeImpl> getAttributeImplsIterator() {
    final State initState = getCurrentState();
    if (initState != null) {
      return new Iterator<AttributeImpl>() {
        private State state = initState;
      
        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
        
        @Override
        public AttributeImpl next() {
          if (state == null)
            throw new NoSuchElementException();
          final AttributeImpl att = state.attribute;
          state = state.next;
          return att;
        }
        
        @Override
        public boolean hasNext() {
          return state != null;
        }
      };
    } else {
      return Collections.<AttributeImpl>emptySet().iterator();
    }
  }
  
  private static final ClassValue<Class<? extends Attribute>[]> implInterfaces = new ClassValue<Class<? extends Attribute>[]>() {
    @Override
    protected Class<? extends Attribute>[] computeValue(Class<?> clazz) {
      final Set<Class<? extends Attribute>> intfSet = new LinkedHashSet<>();
      // find all interfaces that this attribute instance implements
      // and that extend the Attribute interface
      do {
        for (Class<?> curInterface : clazz.getInterfaces()) {
          if (curInterface != Attribute.class && Attribute.class.isAssignableFrom(curInterface)) {
            intfSet.add(curInterface.asSubclass(Attribute.class));
          }
        }
        clazz = clazz.getSuperclass();
      } while (clazz != null);
      @SuppressWarnings({"unchecked", "rawtypes"}) final Class<? extends Attribute>[] a =
          intfSet.toArray(new Class[intfSet.size()]);
      return a;
    }
  };
  
  static Class<? extends Attribute>[] getAttributeInterfaces(final Class<? extends AttributeImpl> clazz) {
    return implInterfaces.get(clazz);
  }
  

  public final void addAttributeImpl(final AttributeImpl att) {
    final Class<? extends AttributeImpl> clazz = att.getClass();
    if (attributeImpls.containsKey(clazz)) return;
    
    // add all interfaces of this AttributeImpl to the maps
    for (final Class<? extends Attribute> curInterface : getAttributeInterfaces(clazz)) {
      // Attribute is a superclass of this interface
      if (!attributes.containsKey(curInterface)) {
        // invalidate state to force recomputation in captureState()
        this.currentState[0] = null;
        attributes.put(curInterface, att);
        attributeImpls.put(clazz, att);
      }
    }
  }
  
  public final <T extends Attribute> T addAttribute(Class<T> attClass) {
    AttributeImpl attImpl = attributes.get(attClass);
    if (attImpl == null) {
      if (!(attClass.isInterface() && Attribute.class.isAssignableFrom(attClass))) {
        throw new IllegalArgumentException(
          "addAttribute() only accepts an interface that extends Attribute, but " +
          attClass.getName() + " does not fulfil this contract."
        );
      }
      addAttributeImpl(attImpl = this.factory.createAttributeInstance(attClass));
    }
    return attClass.cast(attImpl);
  }
  
  public final boolean hasAttributes() {
    return !this.attributes.isEmpty();
  }

  public final boolean hasAttribute(Class<? extends Attribute> attClass) {
    return this.attributes.containsKey(attClass);
  }

  public final <T extends Attribute> T getAttribute(Class<T> attClass) {
    return attClass.cast(attributes.get(attClass));
  }
    
  private State getCurrentState() {
    State s  = currentState[0];
    if (s != null || !hasAttributes()) {
      return s;
    }
    State c = s = currentState[0] = new State();
    final Iterator<AttributeImpl> it = attributeImpls.values().iterator();
    c.attribute = it.next();
    while (it.hasNext()) {
      c.next = new State();
      c = c.next;
      c.attribute = it.next();
    }
    return s;
  }
  
  public final void clearAttributes() {
    for (State state = getCurrentState(); state != null; state = state.next) {
      state.attribute.clear();
    }
  }
  
  public final void endAttributes() {
    for (State state = getCurrentState(); state != null; state = state.next) {
      state.attribute.end();
    }
  }

  public final void removeAllAttributes() {
    attributes.clear();
    attributeImpls.clear();
  }

  public final State captureState() {
    final State state = this.getCurrentState();
    return (state == null) ? null : state.clone();
  }
  
  public final void restoreState(State state) {
    if (state == null)  return;
    
    do {
      AttributeImpl targetImpl = attributeImpls.get(state.attribute.getClass());
      if (targetImpl == null) {
        throw new IllegalArgumentException("State contains AttributeImpl of type " +
          state.attribute.getClass().getName() + " that is not in in this AttributeSource");
      }
      state.attribute.copyTo(targetImpl);
      state = state.next;
    } while (state != null);
  }

  @Override
  public int hashCode() {
    int code = 0;
    for (State state = getCurrentState(); state != null; state = state.next) {
      code = code * 31 + state.attribute.hashCode();
    }
    return code;
  }
  
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof AttributeSource) {
      AttributeSource other = (AttributeSource) obj;  
    
      if (hasAttributes()) {
        if (!other.hasAttributes()) {
          return false;
        }
        
        if (this.attributeImpls.size() != other.attributeImpls.size()) {
          return false;
        }
  
        // it is only equal if all attribute impls are the same in the same order
        State thisState = this.getCurrentState();
        State otherState = other.getCurrentState();
        while (thisState != null && otherState != null) {
          if (otherState.attribute.getClass() != thisState.attribute.getClass() || !otherState.attribute.equals(thisState.attribute)) {
            return false;
          }
          thisState = thisState.next;
          otherState = otherState.next;
        }
        return true;
      } else {
        return !other.hasAttributes();
      }
    } else
      return false;
  }
  
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
  
  public final void reflectWith(AttributeReflector reflector) {
    for (State state = getCurrentState(); state != null; state = state.next) {
      state.attribute.reflectWith(reflector);
    }
  }

  public final AttributeSource cloneAttributes() {
    final AttributeSource clone = new AttributeSource(this.factory);
    
    if (hasAttributes()) {
      // first clone the impls
      for (State state = getCurrentState(); state != null; state = state.next) {
        clone.attributeImpls.put(state.attribute.getClass(), state.attribute.clone());
      }
      
      // now the interfaces
      for (Entry<Class<? extends Attribute>, AttributeImpl> entry : this.attributes.entrySet()) {
        clone.attributes.put(entry.getKey(), clone.attributeImpls.get(entry.getValue().getClass()));
      }
    }
    
    return clone;
  }
  
  public final void copyTo(AttributeSource target) {
    for (State state = getCurrentState(); state != null; state = state.next) {
      final AttributeImpl targetImpl = target.attributeImpls.get(state.attribute.getClass());
      if (targetImpl == null) {
        throw new IllegalArgumentException("This AttributeSource contains AttributeImpl of type " +
          state.attribute.getClass().getName() + " that is not in the target");
      }
      state.attribute.copyTo(targetImpl);
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '@' + Integer.toHexString(System.identityHashCode(this)) + " " + reflectAsString(false);
  }
}
