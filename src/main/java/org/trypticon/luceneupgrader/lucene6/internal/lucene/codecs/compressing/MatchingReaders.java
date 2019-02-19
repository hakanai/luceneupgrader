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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.compressing;


import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SegmentReader;

class MatchingReaders {
  

  final boolean[] matchingReaders;

  final int count;
  
  MatchingReaders(MergeState mergeState) {
    // If the i'th reader is a SegmentReader and has
    // identical fieldName -> number mapping, then this
    // array will be non-null at position i:
    int numReaders = mergeState.maxDocs.length;
    int matchedCount = 0;
    matchingReaders = new boolean[numReaders];

    // If this reader is a SegmentReader, and all of its
    // field name -> number mappings match the "merged"
    // FieldInfos, then we can do a bulk copy of the
    // stored fields:

    nextReader:
    for (int i = 0; i < numReaders; i++) {
      for (FieldInfo fi : mergeState.fieldInfos[i]) {
        FieldInfo other = mergeState.mergeFieldInfos.fieldInfo(fi.number);
        if (other == null || !other.name.equals(fi.name)) {
          continue nextReader;
        }
      }
      matchingReaders[i] = true;
      matchedCount++;
    }
    
    this.count = matchedCount;

    if (mergeState.infoStream.isEnabled("SM")) {
      mergeState.infoStream.message("SM", "merge store matchedCount=" + count + " vs " + numReaders);
      if (count != numReaders) {
        mergeState.infoStream.message("SM", "" + (numReaders - count) + " non-bulk merges");
      }
    }
  }
}
