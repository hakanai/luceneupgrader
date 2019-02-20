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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;

public abstract class SortedSetDocValues {
  
  protected SortedSetDocValues() {}


  public static final long NO_MORE_ORDS = -1;


  public abstract long nextOrd();
  

  public abstract void setDocument(int docID);


  public abstract BytesRef lookupOrd(long ord);

  public abstract long getValueCount();


  public long lookupTerm(BytesRef key) {
    long low = 0;
    long high = getValueCount()-1;

    while (low <= high) {
      long mid = (low + high) >>> 1;
      final BytesRef term = lookupOrd(mid);
      int cmp = term.compareTo(key);

      if (cmp < 0) {
        low = mid + 1;
      } else if (cmp > 0) {
        high = mid - 1;
      } else {
        return mid; // key found
      }
    }

    return -(low + 1);  // key not found.
  }
  

  public TermsEnum termsEnum() {
    return new SortedSetDocValuesTermsEnum(this);
  }
}
