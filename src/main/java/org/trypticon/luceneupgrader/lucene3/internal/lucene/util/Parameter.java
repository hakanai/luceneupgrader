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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.HashMap;
import java.util.Map;

@Deprecated
@SuppressWarnings("serial")
public abstract class Parameter implements Serializable {
  static Map<String,Parameter> allParameters = new HashMap<String,Parameter>();
  
  private String name;
  
  protected Parameter(String name) {
    // typesafe enum pattern, no public constructor
    this.name = name;
    String key = makeKey(name);
    
    if(allParameters.containsKey(key))
      throw new IllegalArgumentException("Parameter name " + key + " already used!");
    
    allParameters.put(key, this);
  }
  
  private String makeKey(String name){
    return getClass() + " " + name;
  }
  
  @Override
  public String toString() {
    return name;
  }
  
  protected Object readResolve() throws ObjectStreamException {
    Object par = allParameters.get(makeKey(name));
    
    if(par == null)
      throw new StreamCorruptedException("Unknown parameter value: " + name);
      
    return par;
  }
  
 }
