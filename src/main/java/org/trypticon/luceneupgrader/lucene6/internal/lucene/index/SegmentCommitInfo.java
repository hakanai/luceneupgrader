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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.index;


import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


public class SegmentCommitInfo {
  
  public final SegmentInfo info;

  // How many deleted docs in the segment:
  private int delCount;

  // Generation number of the live docs file (-1 if there
  // are no deletes yet):
  private long delGen;

  // Normally 1+delGen, unless an exception was hit on last
  // attempt to write:
  private long nextWriteDelGen;

  // Generation number of the FieldInfos (-1 if there are no updates)
  private long fieldInfosGen;
  
  // Normally 1+fieldInfosGen, unless an exception was hit on last attempt to
  // write
  private long nextWriteFieldInfosGen;
  
  // Generation number of the DocValues (-1 if there are no updates)
  private long docValuesGen;
  
  // Normally 1+dvGen, unless an exception was hit on last attempt to
  // write
  private long nextWriteDocValuesGen;

  // Track the per-field DocValues update files
  private final Map<Integer,Set<String>> dvUpdatesFiles = new HashMap<>();
  
  // TODO should we add .files() to FieldInfosFormat, like we have on
  // LiveDocsFormat?
  // track the fieldInfos update files
  private final Set<String> fieldInfosFiles = new HashSet<>();
  
  private volatile long sizeInBytes = -1;

  public SegmentCommitInfo(SegmentInfo info, int delCount, long delGen, long fieldInfosGen, long docValuesGen) {
    this.info = info;
    this.delCount = delCount;
    this.delGen = delGen;
    this.nextWriteDelGen = delGen == -1 ? 1 : delGen + 1;
    this.fieldInfosGen = fieldInfosGen;
    this.nextWriteFieldInfosGen = fieldInfosGen == -1 ? 1 : fieldInfosGen + 1;
    this.docValuesGen = docValuesGen;
    this.nextWriteDocValuesGen = docValuesGen == -1 ? 1 : docValuesGen + 1;
  }
  
  public Map<Integer,Set<String>> getDocValuesUpdatesFiles() {
    return Collections.unmodifiableMap(dvUpdatesFiles);
  }
  
  public void setDocValuesUpdatesFiles(Map<Integer,Set<String>> dvUpdatesFiles) {
    this.dvUpdatesFiles.clear();
    for (Map.Entry<Integer,Set<String>> kv : dvUpdatesFiles.entrySet()) {
      // rename the set
      Set<String> set = new HashSet<>();
      for (String file : kv.getValue()) {
        set.add(info.namedForThisSegment(file));
      }
      this.dvUpdatesFiles.put(kv.getKey(), set);
    }
  }
  
  public Set<String> getFieldInfosFiles() {
    return Collections.unmodifiableSet(fieldInfosFiles);
  }
  
  public void setFieldInfosFiles(Set<String> fieldInfosFiles) {
    this.fieldInfosFiles.clear();
    for (String file : fieldInfosFiles) {
      this.fieldInfosFiles.add(info.namedForThisSegment(file));
    }
  }

  void advanceDelGen() {
    delGen = nextWriteDelGen;
    nextWriteDelGen = delGen+1;
    sizeInBytes = -1;
  }


  void advanceNextWriteDelGen() {
    nextWriteDelGen++;
  }
  
  long getNextWriteDelGen() {
    return nextWriteDelGen;
  }
  
  void setNextWriteDelGen(long v) {
    nextWriteDelGen = v;
  }
  
  void advanceFieldInfosGen() {
    fieldInfosGen = nextWriteFieldInfosGen;
    nextWriteFieldInfosGen = fieldInfosGen + 1;
    sizeInBytes = -1;
  }
  
  void advanceNextWriteFieldInfosGen() {
    nextWriteFieldInfosGen++;
  }
  
  long getNextWriteFieldInfosGen() {
    return nextWriteFieldInfosGen;
  }
  
  void setNextWriteFieldInfosGen(long v) {
    nextWriteFieldInfosGen = v;
  }

  void advanceDocValuesGen() {
    docValuesGen = nextWriteDocValuesGen;
    nextWriteDocValuesGen = docValuesGen + 1;
    sizeInBytes = -1;
  }
  
  void advanceNextWriteDocValuesGen() {
    nextWriteDocValuesGen++;
  }

  long getNextWriteDocValuesGen() {
    return nextWriteDocValuesGen;
  }
  
  void setNextWriteDocValuesGen(long v) {
    nextWriteDocValuesGen = v;
  }
  
  public long sizeInBytes() throws IOException {
    if (sizeInBytes == -1) {
      long sum = 0;
      for (final String fileName : files()) {
        sum += info.dir.fileLength(fileName);
      }
      sizeInBytes = sum;
    }

    return sizeInBytes;
  }

  public Collection<String> files() throws IOException {
    // Start from the wrapped info's files:
    Collection<String> files = new HashSet<>(info.files());

    // TODO we could rely on TrackingDir.getCreatedFiles() (like we do for
    // updates) and then maybe even be able to remove LiveDocsFormat.files().
    
    // Must separately add any live docs files:
    info.getCodec().liveDocsFormat().files(this, files);
    
    // must separately add any field updates files
    for (Set<String> updatefiles : dvUpdatesFiles.values()) {
      files.addAll(updatefiles);
    }
    
    // must separately add fieldInfos files
    files.addAll(fieldInfosFiles);
    
    return files;
  }

  // NOTE: only used in-RAM by IW to track buffered deletes;
  // this is never written to/read from the Directory
  private long bufferedDeletesGen;
  
  long getBufferedDeletesGen() {
    return bufferedDeletesGen;
  }

  void setBufferedDeletesGen(long v) {
    bufferedDeletesGen = v;
    sizeInBytes =  -1;
  }
  
  public boolean hasDeletions() {
    return delGen != -1;
  }

  public boolean hasFieldUpdates() {
    return fieldInfosGen != -1;
  }
  
  public long getNextFieldInfosGen() {
    return nextWriteFieldInfosGen;
  }
  
  public long getFieldInfosGen() {
    return fieldInfosGen;
  }
  
  public long getNextDocValuesGen() {
    return nextWriteDocValuesGen;
  }
  
  public long getDocValuesGen() {
    return docValuesGen;
  }
  
  public long getNextDelGen() {
    return nextWriteDelGen;
  }

  public long getDelGen() {
    return delGen;
  }
  
  public int getDelCount() {
    return delCount;
  }

  void setDelCount(int delCount) {
    if (delCount < 0 || delCount > info.maxDoc()) {
      throw new IllegalArgumentException("invalid delCount=" + delCount + " (maxDoc=" + info.maxDoc() + ")");
    }
    this.delCount = delCount;
  }

  public String toString(int pendingDelCount) {
    String s = info.toString(delCount + pendingDelCount);
    if (delGen != -1) {
      s += ":delGen=" + delGen;
    }
    if (fieldInfosGen != -1) {
      s += ":fieldInfosGen=" + fieldInfosGen;
    }
    if (docValuesGen != -1) {
      s += ":dvGen=" + docValuesGen;
    }
    return s;
  }

  @Override
  public String toString() {
    return toString(0);
  }

  @Override
  public SegmentCommitInfo clone() {
    SegmentCommitInfo other = new SegmentCommitInfo(info, delCount, delGen, fieldInfosGen, docValuesGen);
    // Not clear that we need to carry over nextWriteDelGen
    // (i.e. do we ever clone after a failed write and
    // before the next successful write?), but just do it to
    // be safe:
    other.nextWriteDelGen = nextWriteDelGen;
    other.nextWriteFieldInfosGen = nextWriteFieldInfosGen;
    other.nextWriteDocValuesGen = nextWriteDocValuesGen;
    
    // deep clone
    for (Entry<Integer,Set<String>> e : dvUpdatesFiles.entrySet()) {
      other.dvUpdatesFiles.put(e.getKey(), new HashSet<>(e.getValue()));
    }
    
    other.fieldInfosFiles.addAll(fieldInfosFiles);
    
    return other;
  }
}
