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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search;


import java.util.Objects;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.TermsEnum; // javadocs
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.BytesRef;
// TODO: actually add missing cross-checks to guarantee TermStatistics is in bounds of CollectionStatistics,
// otherwise many similarity functions will implode.
public class TermStatistics {
  private final BytesRef term;
  private final long docFreq;
  private final long totalTermFreq;
  
  public TermStatistics(BytesRef term, long docFreq, long totalTermFreq) {
    Objects.requireNonNull(term);
    if (docFreq <= 0) {
      throw new IllegalArgumentException("docFreq must be positive, docFreq: " + docFreq);
    }
    if (totalTermFreq <= 0) {
      throw new IllegalArgumentException("totalTermFreq must be positive, totalTermFreq: " + totalTermFreq);
    }
    if (totalTermFreq < docFreq) {
      throw new IllegalArgumentException("totalTermFreq must be at least docFreq, totalTermFreq: " + totalTermFreq + ", docFreq: " + docFreq);
    }
    this.term = term;
    this.docFreq = docFreq;
    this.totalTermFreq = totalTermFreq;
  }
  
  public final BytesRef term() {
    return term;
  }
  
  public final long docFreq() {
    return docFreq;
  }
  
  public final long totalTermFreq() {
    return totalTermFreq;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("term=");
    sb.append('"');
    sb.append(Term.toString(term()));
    sb.append('"');
    sb.append(",docFreq=");
    sb.append(docFreq());
    sb.append(",totalTermFreq=");
    sb.append(totalTermFreq());
    return sb.toString();
  }
}
