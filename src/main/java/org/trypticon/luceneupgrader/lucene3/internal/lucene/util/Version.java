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


// remove me when java 5 is no longer supported
// this is a workaround for a JDK bug that wrongly emits a warning.
@SuppressWarnings("dep-ann")
public enum Version {


  @Deprecated
  LUCENE_20,


  @Deprecated
  LUCENE_21,


  @Deprecated
  LUCENE_22,


  @Deprecated
  LUCENE_23,


  @Deprecated
  LUCENE_24,


  @Deprecated
  LUCENE_29,

  LUCENE_30,

  LUCENE_31,
  
  LUCENE_32,
  
  LUCENE_33,
  
  LUCENE_34,

  LUCENE_35,
  
  LUCENE_36,
  
  /* Add new constants for later versions **here** to respect order! */

  @Deprecated
  LUCENE_CURRENT;

  public boolean onOrAfter(Version other) {
    return compareTo(other) >= 0;
  }
}
