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


import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene3x.Lucene3xSegmentInfoFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.TrackingDirectoryWrapper;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Version;

public final class SegmentInfo {
  
  // TODO: remove these from this class, for now this is the representation
  public static final int NO = -1;          // e.g. no norms; no deletes;

  public static final int YES = 1;          // e.g. have norms; have deletes;

  public final String name;

  private int docCount;         // number of docs in seg

  public final Directory dir;

  private boolean isCompoundFile;

  private Codec codec;

  private Map<String,String> diagnostics;
  
  @Deprecated
  private Map<String,String> attributes;

  // Tracks the Lucene version this segment was created with, since 3.1. Null
  // indicates an older than 3.0 index, and it's used to detect a too old index.
  // The format expected is "x.y" - "2.x" for pre-3.0 indexes (or null), and
  // specific versions afterwards ("3.0.0", "3.1.0" etc.).
  // see o.a.l.util.Version.
  private Version version;

  void setDiagnostics(Map<String, String> diagnostics) {
    this.diagnostics = diagnostics;
  }

  public Map<String, String> getDiagnostics() {
    return diagnostics;
  }
  
  public SegmentInfo(Directory dir, Version version, String name, int docCount,
      boolean isCompoundFile, Codec codec, Map<String,String> diagnostics) {
    this(dir, version, name, docCount, isCompoundFile, codec, diagnostics, null);
  }

  public SegmentInfo(Directory dir, Version version, String name, int docCount,
                     boolean isCompoundFile, Codec codec, Map<String,String> diagnostics, Map<String,String> attributes) {
    assert !(dir instanceof TrackingDirectoryWrapper);
    this.dir = dir;
    this.version = version;
    this.name = name;
    this.docCount = docCount;
    this.isCompoundFile = isCompoundFile;
    this.codec = codec;
    this.diagnostics = diagnostics;
    this.attributes = attributes;
  }

  @Deprecated
  boolean hasSeparateNorms() {
    return getAttribute(Lucene3xSegmentInfoFormat.NORMGEN_KEY) != null;
  }

  void setUseCompoundFile(boolean isCompoundFile) {
    this.isCompoundFile = isCompoundFile;
  }
  
  public boolean getUseCompoundFile() {
    return isCompoundFile;
  }

  public void setCodec(Codec codec) {
    assert this.codec == null;
    if (codec == null) {
      throw new IllegalArgumentException("codec must be non-null");
    }
    this.codec = codec;
  }

  public Codec getCodec() {
    return codec;
  }

  public int getDocCount() {
    if (this.docCount == -1) {
      throw new IllegalStateException("docCount isn't set yet");
    }
    return docCount;
  }

  // NOTE: leave package private
  void setDocCount(int docCount) {
    if (this.docCount != -1) {
      throw new IllegalStateException("docCount was already set");
    }
    this.docCount = docCount;
  }

  public Set<String> files() {
    if (setFiles == null) {
      throw new IllegalStateException("files were not computed yet");
    }
    return Collections.unmodifiableSet(setFiles);
  }

  @Override
  public String toString() {
    return toString(dir, 0);
  }


  public String toString(Directory dir, int delCount) {
    StringBuilder s = new StringBuilder();
    s.append(name).append('(').append(version == null ? "?" : version).append(')').append(':');
    char cfs = getUseCompoundFile() ? 'c' : 'C';
    s.append(cfs);

    if (this.dir != dir) {
      s.append('x');
    }
    s.append(docCount);

    if (delCount != 0) {
      s.append('/').append(delCount);
    }

    // TODO: we could append toString of attributes() here?

    return s.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj instanceof SegmentInfo) {
      final SegmentInfo other = (SegmentInfo) obj;
      return other.dir == dir && other.name.equals(name);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return dir.hashCode() + name.hashCode();
  }

  public void setVersion(Version version) {
    this.version = version;
  }

  public Version getVersion() {
    return version;
  }

  private Set<String> setFiles;

  public void setFiles(Set<String> files) {
    checkFileNames(files);
    setFiles = files;
  }

  public void addFiles(Collection<String> files) {
    checkFileNames(files);
    setFiles.addAll(files);
  }

  public void addFile(String file) {
    checkFileNames(Collections.singleton(file));
    setFiles.add(file);
  }
  
  private void checkFileNames(Collection<String> files) {
    Matcher m = IndexFileNames.CODEC_FILE_PATTERN.matcher("");
    for (String file : files) {
      m.reset(file);
      if (!m.matches()) {
        throw new IllegalArgumentException("invalid codec filename '" + file + "', must match: " + IndexFileNames.CODEC_FILE_PATTERN.pattern());
      }
    }
  }
    
  @Deprecated
  public String getAttribute(String key) {
    if (attributes == null) {
      return null;
    } else {
      return attributes.get(key);
    }
  }
  
  @Deprecated
  public String putAttribute(String key, String value) {
    if (attributes == null) {
      attributes = new HashMap<>();
    }
    return attributes.put(key, value);
  }
  
  @Deprecated
  public Map<String,String> attributes() {
    return attributes;
  }

  private static Map<String,String> cloneMap(Map<String,String> map) {
    if (map != null) {
      return new HashMap<String,String>(map);
    } else {
      return null;
    }
  }

  @Override
  public SegmentInfo clone() {
    SegmentInfo other = new SegmentInfo(dir, version, name, docCount, isCompoundFile, codec, cloneMap(diagnostics), cloneMap(attributes));
    if (setFiles != null) {
      other.setFiles(new HashSet<>(setFiles));
    }
    return other;
  }
}
