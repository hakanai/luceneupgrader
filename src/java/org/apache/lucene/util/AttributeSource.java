package org.apache.lucene.util;

/**
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

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * An AttributeSource contains a list of different {@code AttributeImpl}s,
 * and methods to add and get them. There can only be a single instance
 * of an attribute in the same AttributeSource instance. This is ensured
 * by passing in the actual type of the Attribute (Class&lt;Attribute&gt;) to 
 * the {@code #addAttribute(Class)}, which then checks if an instance of
 * that type is already present. If yes, it returns the instance, otherwise
 * it creates a new instance and returns it.
 */
public class AttributeSource {
  /**
   * An AttributeFactory creates instances of {@code AttributeImpl}s.
   */
  public static abstract class AttributeFactory {
    /**
     * returns an {@code AttributeImpl} for the supplied {@code Attribute} interface class.
     */
    public abstract AttributeImpl createAttributeInstance(Class<? extends Attribute> attClass);

  }
      
  /**
   * This class holds the state of an AttributeSource.
   *
   *
   */
  public static final class State implements Cloneable {
    AttributeImpl attribute;
    State next;
    
    @SuppressWarnings("CloneDoesntCallSuperClone")
    @Override
    public Object clone() {
      State clone = new State();
      clone.attribute = (AttributeImpl) attribute.clone();
      
      if (next != null) {
        clone.next = (State) next.clone();
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

  /**
   * An AttributeSource using the supplied {@code AttributeFactory} for creating new {@code Attribute} instances.
   */
  public AttributeSource(AttributeFactory factory) {
    this.attributes = new LinkedHashMap<Class<? extends Attribute>, AttributeImpl>();
    this.attributeImpls = new LinkedHashMap<Class<? extends AttributeImpl>, AttributeImpl>();
    this.currentState = new State[1];
    this.factory = factory;
  }

  /** a cache that stores all interfaces for known implementation classes for performance (slow reflection) */
  private static final WeakIdentityMap<Class<? extends AttributeImpl>,LinkedList<WeakReference<Class<? extends Attribute>>>> knownImplClasses =
    WeakIdentityMap.newConcurrentHashMap();
  
  static LinkedList<WeakReference<Class<? extends Attribute>>> getAttributeInterfaces(final Class<? extends AttributeImpl> clazz) {
    LinkedList<WeakReference<Class<? extends Attribute>>> foundInterfaces = knownImplClasses.get(clazz);
    if (foundInterfaces == null) {
      // we have the slight chance that another thread may do the same, but who cares?
      foundInterfaces = new LinkedList<WeakReference<Class<? extends Attribute>>>();
      // find all interfaces that this attribute instance implements
      // and that extend the Attribute interface
      Class<?> actClazz = clazz;
      do {
        for (Class<?> curInterface : actClazz.getInterfaces()) {
          if (curInterface != Attribute.class && Attribute.class.isAssignableFrom(curInterface)) {
            foundInterfaces.add(new WeakReference<Class<? extends Attribute>>(curInterface.asSubclass(Attribute.class)));
          }
        }
        actClazz = actClazz.getSuperclass();
      } while (actClazz != null);
      knownImplClasses.put(clazz, foundInterfaces);
    }
    return foundInterfaces;
  }
  
  /** <b>Expert:</b> Adds a custom AttributeImpl instance with one or more Attribute interfaces.
   * <p><font color="red"><b>Please note:</b> It is not guaranteed, that <code>att</code> is added to
   * the <code>AttributeSource</code>, because the provided attributes may already exist.
   * You should always retrieve the wanted attributes using {@code #getAttribute} after adding
   * with this method and cast to your class.
   * The recommended way to use custom implementations is using an {@code AttributeFactory}.
   * </font></p>
   */
  public void addAttributeImpl(final AttributeImpl att) {
    final Class<? extends AttributeImpl> clazz = att.getClass();
    if (attributeImpls.containsKey(clazz)) return;
    final LinkedList<WeakReference<Class<? extends Attribute>>> foundInterfaces =
      getAttributeInterfaces(clazz);
    
    // add all interfaces of this AttributeImpl to the maps
    for (WeakReference<Class<? extends Attribute>> curInterfaceRef : foundInterfaces) {
      final Class<? extends Attribute> curInterface = curInterfaceRef.get();
      assert (curInterface != null) :
        "We have a strong reference on the class holding the interfaces, so they should never get evicted";
      // Attribute is a superclass of this interface
      if (!attributes.containsKey(curInterface)) {
        // invalidate state to force recomputation in captureState()
        this.currentState[0] = null;
        attributes.put(curInterface, att);
        attributeImpls.put(clazz, att);
      }
    }
  }
  
  /**
   * The caller must pass in a Class&lt;? extends Attribute&gt; value.
   * This method first checks if an instance of that class is 
   * already in this AttributeSource and returns it. Otherwise a
   * new instance is created, added to this AttributeSource and returned. 
   */
  public <A extends Attribute> A addAttribute(Class<A> attClass) {
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
  
  /** Returns true, iff this AttributeSource has any attributes */
  public boolean hasAttributes() {
    return !this.attributes.isEmpty();
  }

  /**
   * The caller must pass in a Class&lt;? extends Attribute&gt; value. 
   * Returns true, iff this AttributeSource contains the passed-in Attribute.
   */
  public boolean hasAttribute(Class<? extends Attribute> attClass) {
    return this.attributes.containsKey(attClass);
  }

  /**
   * The caller must pass in a Class&lt;? extends Attribute&gt; value. 
   * Returns the instance of the passed in Attribute contained in this AttributeSource
   * 
   * @throws IllegalArgumentException if this AttributeSource does not contain the
   *         Attribute. It is recommended to always use {@code #addAttribute} even in consumers
   *         of TokenStreams, because you cannot know if a specific TokenStream really uses
   *         a specific Attribute. {@code #addAttribute} will automatically make the attribute
   *         available. If you want to only use the attribute, if it is available (to optimize
   *         consuming), use {@code #hasAttribute}.
   */
  public <A extends Attribute> A getAttribute(Class<A> attClass) {
    AttributeImpl attImpl = attributes.get(attClass);
    if (attImpl == null) {
      throw new IllegalArgumentException("This AttributeSource does not have the attribute '" + attClass.getName() + "'.");
    }
    return attClass.cast(attImpl);
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
  
  /**
   * Returns a string representation of the object. In general, the {@code toString} method
   * returns a string that &quot;textually represents&quot; this object.
   *
   * <p><b>WARNING:</b> For backwards compatibility this method is implemented as
   * in Lucene 2.9/3.0. In Lucene 4.0 this default implementation
   * will be removed.
   *
   * <p>It is recommeneded to use {@code #reflectAsString} or {@code #reflectWith}
   * to get a well-defined output of AttributeSource's internals.
   */
  // TODO: @deprecated remove this method in 4.0
  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder().append('(');
    if (hasAttributes()) {
      for (State state = getCurrentState(); state != null; state = state.next) {
        if (sb.length() > 1) sb.append(',');
        sb.append(state.attribute.toString());
      }
    }
    return sb.append(')').toString();
  }

}
