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


import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.Sort;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.TrackingDirectoryWrapper;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.StringHelper;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Version;

public final class SegmentInfo {
  
  // TODO: remove these from this class, for now this is the representation
  public static final int NO = -1;          // e.g. no norms; no deletes;

  public static final int YES = 1;          // e.g. have norms; have deletes;

  public final String name;

  private int maxDoc;         // number of docs in seg

  public final Directory dir;

  private boolean isCompoundFile;

  private final byte[] id;

  private Codec codec;

  private Map<String,String> diagnostics;
  
  private final Map<String,String> attributes;

  private final Sort indexSort;

  // Tracks the Lucene version this segment was created with, since 3.1. Null
  // indicates an older than 3.0 index, and it's used to detect a too old index.
  // The format expected is "x.y" - "2.x" for pre-3.0 indexes (or null), and
  // specific versions afterwards ("3.0.0", "3.1.0" etc.).
  // see o.a.l.util.Version.
  private Version version;

  void setDiagnostics(Map<String, String> diagnostics) {
    this.diagnostics = Objects.requireNonNull(diagnostics);
  }

  public Map<String, String> getDiagnostics() {
    return diagnostics;
  }

  public SegmentInfo(Directory dir, Version version, String name, int maxDoc,
                     boolean isCompoundFile, Codec codec, Map<String,String> diagnostics,
                     byte[] id, Map<String,String> attributes, Sort indexSort) {
    assert !(dir instanceof TrackingDirectoryWrapper);
    this.dir = Objects.requireNonNull(dir);
    this.version = Objects.requireNonNull(version);
    this.name = Objects.requireNonNull(name);
    this.maxDoc = maxDoc;
    this.isCompoundFile = isCompoundFile;
    this.codec = codec;
    this.diagnostics = Objects.requireNonNull(diagnostics);
    this.id = id;
    if (id.length != StringHelper.ID_LENGTH) {
      throw new IllegalArgumentException("invalid id: " + Arrays.toString(id));
    }
    this.attributes = Objects.requireNonNull(attributes);
    this.indexSort = indexSort;
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

  public int maxDoc() {
    if (this.maxDoc == -1) {
      throw new IllegalStateException("maxDoc isn't set yet");
    }
    return maxDoc;
  }

  // NOTE: leave package private
  void setMaxDoc(int maxDoc) {
    if (this.maxDoc != -1) {
      throw new IllegalStateException("maxDoc was already set: this.maxDoc=" + this.maxDoc + " vs maxDoc=" + maxDoc);
    }
    this.maxDoc = maxDoc;
  }

  public Set<String> files() {
    if (setFiles == null) {
      throw new IllegalStateException("files were not computed yet");
    }
    return Collections.unmodifiableSet(setFiles);
  }

  @Override
  public String toString() {
    return toString(0);
  }


  public String toString(int delCount) {
    StringBuilder s = new StringBuilder();
    s.append(name).append('(').append(version == null ? "?" : version).append(')').append(':');
    char cfs = getUseCompoundFile() ? 'c' : 'C';
    s.append(cfs);

    s.append(maxDoc);

    if (delCount != 0) {
      s.append('/').append(delCount);
    }

    if (indexSort != null) {
      s.append(":[indexSort=");
      s.append(indexSort);
      s.append(']');
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

  public Version getVersion() {
    return version;
  }

  public byte[] getId() {
    return id.clone();
  }

  private Set<String> setFiles;

  public void setFiles(Collection<String> files) {
    setFiles = new HashSet<>();
    addFiles(files);
  }

  public void addFiles(Collection<String> files) {
    checkFileNames(files);
    for (String f : files) {
      setFiles.add(namedForThisSegment(f));
    }
  }

  public void addFile(String file) {
    checkFileNames(Collections.singleton(file));
    setFiles.add(namedForThisSegment(file));
  }
  
  private void checkFileNames(Collection<String> files) {
    Matcher m = IndexFileNames.CODEC_FILE_PATTERN.matcher("");
    for (String file : files) {
      m.reset(file);
      if (!m.matches()) {
        throw new IllegalArgumentException("invalid codec filename '" + file + "', must match: " + IndexFileNames.CODEC_FILE_PATTERN.pattern());
      }
      if (file.toLowerCase(Locale.ROOT).endsWith(".tmp")) {
        throw new IllegalArgumentException("invalid codec filename '" + file + "', cannot end with .tmp extension");
      }
    }
  }
  

  String namedForThisSegment(String file) {
    return name + IndexFileNames.stripSegmentName(file);
  }
  
  public String getAttribute(String key) {
    return attributes.get(key);
  }
  
  public String putAttribute(String key, String value) {
    return attributes.put(key, value);
  }
  
  public Map<String,String> getAttributes() {
    return attributes;
  }

  public Sort getIndexSort() {
    return indexSort;
  }
}

