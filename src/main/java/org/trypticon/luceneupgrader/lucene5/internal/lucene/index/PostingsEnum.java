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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.index;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.AttributeSource;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;


public abstract class PostingsEnum extends DocIdSetIterator {
  
  public static final short NONE = 0;

  public static final short FREQS = 1 << 3;

  public static final short POSITIONS = FREQS | 1 << 4;
  
  public static final short OFFSETS = POSITIONS | 1 << 5;

  public static final short PAYLOADS = POSITIONS | 1 << 6;

  public static final short ALL = OFFSETS | PAYLOADS;

  public static boolean featureRequested(int flags, short feature) {
    return (flags & feature) == feature;
  }

  private AttributeSource atts = null;

  protected PostingsEnum() {
  }

  public abstract int freq() throws IOException;
  
  public AttributeSource attributes() {
    if (atts == null) atts = new AttributeSource();
    return atts;
  }

  public abstract int nextPosition() throws IOException;

  public abstract int startOffset() throws IOException;

  public abstract int endOffset() throws IOException;


  public abstract BytesRef getPayload() throws IOException;

}
