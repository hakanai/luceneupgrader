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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.index;


import java.util.List;

public abstract class IndexReaderContext {
  public final CompositeReaderContext parent;
  public final boolean isTopLevel;
  public final int docBaseInParent;
  public final int ordInParent;

  // An object that uniquely identifies this context without referencing
  // segments. The goal is to make it fine to have references to this
  // identity object, even after the index reader has been closed
  final Object identity = new Object();

  IndexReaderContext(CompositeReaderContext parent, int ordInParent, int docBaseInParent) {
    if (!(this instanceof CompositeReaderContext || this instanceof LeafReaderContext))
      throw new Error("This class should never be extended by custom code!");
    this.parent = parent;
    this.docBaseInParent = docBaseInParent;
    this.ordInParent = ordInParent;
    this.isTopLevel = parent==null;
  }

  public Object id() {
    return identity;
  }

  public abstract IndexReader reader();
  
  public abstract List<LeafReaderContext> leaves() throws UnsupportedOperationException;
  
  public abstract List<IndexReaderContext> children();
}