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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search;


import java.io.IOException;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.*;

final class PhrasePositions {
  int position;         // position in doc
  int count;            // remaining pos in this doc
  int offset;           // position in phrase
  final int ord;                                  // unique across all PhrasePositions instances
  final PostingsEnum postings;            // stream of docs & positions
  PhrasePositions next;                           // used to make lists
  int rptGroup = -1; // >=0 indicates that this is a repeating PP
  int rptInd; // index in the rptGroup
  final Term[] terms; // for repetitions initialization 

  PhrasePositions(PostingsEnum postings, int o, int ord, Term[] terms) {
    this.postings = postings;
    offset = o;
    this.ord = ord;
    this.terms = terms;
  }

  final void firstPosition() throws IOException {
    count = postings.freq();  // read first pos
    nextPosition();
  }

  final boolean nextPosition() throws IOException {
    if (count-- > 0) {  // read subsequent pos's
      position = postings.nextPosition() - offset;
      return true;
    } else
      return false;
  }
  
  @Override
  public String toString() {
    String s = "o:"+offset+" p:"+position+" c:"+count;
    if (rptGroup >=0 ) {
      s += " rpt:"+rptGroup+",i"+rptInd;
    }
    return s;
  }
}
