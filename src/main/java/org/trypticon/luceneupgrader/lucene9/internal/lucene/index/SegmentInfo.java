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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.index;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Sort;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.TrackingDirectoryWrapper;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.StringHelper;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Version;

/**
 * Information about a segment such as its name, directory, and files related to the segment.
 *
 * @lucene.experimental
 */
public final class SegmentInfo {

  // TODO: remove these from this class, for now this is the representation
  /** Used by some member fields to mean not present (e.g., norms, deletions). */
  public static final int NO = -1; // e.g. no norms; no deletes;

  /** Used by some member fields to mean present (e.g., norms, deletions). */
  public static final int YES = 1; // e.g. have norms; have deletes;

  /** Unique segment name in the directory. */
  public final String name;

  private int maxDoc; // number of docs in seg

  /** Where this segment resides. */
  public final Directory dir;

  private boolean isCompoundFile;

  /** Id that uniquely identifies this segment. */
  private final byte[] id;

  private Codec codec;

  private Map<String, String> diagnostics;

  private Map<String, String> attributes;

  private final Sort indexSort;

  // Tracks the Lucene version this segment was created with, since 3.1. Null
  // indicates an older than 3.0 index, and it's used to detect a too old index.
  // The format expected is "x.y" - "2.x" for pre-3.0 indexes (or null), and
  // specific versions afterwards ("3.0.0", "3.1.0" etc.).
  // see o.a.l.util.Version.
  private final Version version;

  // Tracks the minimum version that contributed documents to a segment. For
  // flush segments, that is the version that wrote it. For merged segments,
  // this is the minimum minVersion of all the segments that have been merged
  // into this segment
  Version minVersion;

  private boolean hasBlocks;

  void setDiagnostics(Map<String, String> diagnostics) {
    this.diagnostics = Map.copyOf(Objects.requireNonNull(diagnostics));
  }

  /**
   * Adds or modifies this segment's diagnostics.
   *
   * <p>Entries in the given map whose keys are not present in the current diagnostics are added.
   * Otherwise, existing entries are modified with the given map's value.
   *
   * @param diagnostics the additional diagnostics
   */
  public void addDiagnostics(Map<String, String> diagnostics) {
    Objects.requireNonNull(diagnostics);
    Map<String, String> copy = new HashMap<>(this.diagnostics);
    copy.putAll(diagnostics);
    setDiagnostics(copy);
  }

  /** Returns diagnostics saved into the segment when it was written. The map is immutable. */
  public Map<String, String> getDiagnostics() {
    return diagnostics;
  }

  /**
   * Construct a new complete SegmentInfo instance from input.
   *
   * <p>Note: this is public only to allow access from the codecs package.
   */
  public SegmentInfo(
      Directory dir,
      Version version,
      Version minVersion,
      String name,
      int maxDoc,
      boolean isCompoundFile,
      boolean hasBlocks,
      Codec codec,
      Map<String, String> diagnostics,
      byte[] id,
      Map<String, String> attributes,
      Sort indexSort) {
    assert !(dir instanceof TrackingDirectoryWrapper);
    this.dir = Objects.requireNonNull(dir);
    this.version = Objects.requireNonNull(version);
    this.minVersion = minVersion;
    this.name = Objects.requireNonNull(name);
    this.maxDoc = maxDoc;
    this.isCompoundFile = isCompoundFile;
    this.hasBlocks = hasBlocks;
    this.codec = codec;
    this.diagnostics = Map.copyOf(Objects.requireNonNull(diagnostics));
    this.id = id;
    if (id.length != StringHelper.ID_LENGTH) {
      throw new IllegalArgumentException("invalid id: " + Arrays.toString(id));
    }
    this.attributes = Map.copyOf(Objects.requireNonNull(attributes));
    this.indexSort = indexSort;
  }

  /**
   * Mark whether this segment is stored as a compound file.
   *
   * @param isCompoundFile true if this is a compound file; else, false
   */
  void setUseCompoundFile(boolean isCompoundFile) {
    this.isCompoundFile = isCompoundFile;
  }

  /** Returns true if this segment is stored as a compound file; else, false. */
  public boolean getUseCompoundFile() {
    return isCompoundFile;
  }

  /**
   * Returns true if this segment contains documents written as blocks.
   *
   * @see LeafMetaData#hasBlocks()
   */
  public boolean getHasBlocks() {
    return hasBlocks;
  }

  /** Sets the hasBlocks property to true. This setting is viral and can't be unset. */
  void setHasBlocks() {
    hasBlocks = true;
  }

  /** Can only be called once. */
  public void setCodec(Codec codec) {
    assert this.codec == null;
    if (codec == null) {
      throw new IllegalArgumentException("codec must be non-null");
    }
    this.codec = codec;
  }

  /** Return {@link Codec} that wrote this segment. */
  public Codec getCodec() {
    return codec;
  }

  /** Returns number of documents in this segment (deletions are not taken into account). */
  public int maxDoc() {
    if (this.maxDoc == -1) {
      throw new IllegalStateException("maxDoc isn't set yet");
    }
    return maxDoc;
  }

  // NOTE: leave package private
  void setMaxDoc(int maxDoc) {
    if (this.maxDoc != -1) {
      throw new IllegalStateException(
          "maxDoc was already set: this.maxDoc=" + this.maxDoc + " vs maxDoc=" + maxDoc);
    }
    this.maxDoc = maxDoc;
  }

  /** Return all files referenced by this SegmentInfo. */
  public Set<String> files() {
    if (setFiles == null) {
      throw new IllegalStateException(
          "files were not computed yet; segment=" + name + " maxDoc=" + maxDoc);
    }
    return Collections.unmodifiableSet(setFiles);
  }

  @Override
  public String toString() {
    return toString(0);
  }

  /**
   * Used for debugging. Format may suddenly change.
   *
   * <p>Current format looks like <code>_a(3.1):c45/4:[sorter=&lt;long: "timestamp"&gt;!]</code>,
   * which means the segment's name is <code>_a</code>; it was created with Lucene 3.1 (or '?' if
   * it's unknown); it's using compound file format (would be <code>C</code> if not compound); it
   * has 45 documents; it has 4 deletions (this part is left off when there are no deletions); it is
   * sorted by the timestamp field in descending order (this part is omitted for unsorted segments).
   */
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

    if (!diagnostics.isEmpty()) {
      s.append(":[diagnostics=");
      s.append(diagnostics.toString());
      s.append(']');
    }

    Map<String, String> attributes = getAttributes();
    if (!attributes.isEmpty()) {
      s.append(":[attributes=");
      s.append(attributes.toString());
      s.append(']');
    }

    return s.toString();
  }

  /** We consider another SegmentInfo instance equal if it has the same dir and same name. */
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

  /** Returns the version of the code which wrote the segment. */
  public Version getVersion() {
    return version;
  }

  /**
   * Return the minimum Lucene version that contributed documents to this segment, or {@code null}
   * if it is unknown.
   */
  public Version getMinVersion() {
    return minVersion;
  }

  /** Return the id that uniquely identifies this segment. */
  public byte[] getId() {
    return id.clone();
  }

  private Set<String> setFiles;

  /** Sets the files written for this segment. */
  public void setFiles(Collection<String> files) {
    setFiles = new HashSet<>();
    addFiles(files);
  }

  /** Add these files to the set of files written for this segment. */
  public void addFiles(Collection<String> files) {
    checkFileNames(files);
    for (String f : files) {
      setFiles.add(namedForThisSegment(f));
    }
  }

  /** Add this file to the set of files written for this segment. */
  public void addFile(String file) {
    checkFileNames(Collections.singleton(file));
    setFiles.add(namedForThisSegment(file));
  }

  private void checkFileNames(Collection<String> files) {
    Matcher m = IndexFileNames.CODEC_FILE_PATTERN.matcher("");
    for (String file : files) {
      m.reset(file);
      if (!m.matches()) {
        throw new IllegalArgumentException(
            "invalid codec filename '"
                + file
                + "', must match: "
                + IndexFileNames.CODEC_FILE_PATTERN.pattern());
      }
      if (file.toLowerCase(Locale.ROOT).endsWith(".tmp")) {
        throw new IllegalArgumentException(
            "invalid codec filename '" + file + "', cannot end with .tmp extension");
      }
    }
  }

  /**
   * strips any segment name from the file, naming it with this segment this is because "segment
   * names" can change, e.g. by addIndexes(Dir)
   */
  String namedForThisSegment(String file) {
    return name + IndexFileNames.stripSegmentName(file);
  }

  /** Get a codec attribute value, or null if it does not exist */
  public synchronized String getAttribute(String key) {
    return attributes.get(key);
  }

  /**
   * Puts a codec attribute value.
   *
   * <p>This is a key-value mapping for the field that the codec can use to store additional
   * metadata, and will be available to the codec when reading the segment via {@link
   * #getAttribute(String)}
   *
   * <p>If a value already exists for the field, it will be replaced with the new value. This method
   * make a copy on write for every attribute change.
   */
  public synchronized String putAttribute(String key, String value) {
    HashMap<String, String> newMap = new HashMap<>(attributes);
    String oldValue = newMap.put(key, value);
    // This needs to be thread-safe because multiple threads may be updating (different) attributes
    // at the same time due to concurrent merging, plus some threads may be calling toString() on
    // segment info while other threads are updating attributes.
    attributes = Collections.unmodifiableMap(newMap);
    return oldValue;
  }

  /**
   * Returns the internal codec attributes map.
   *
   * @return internal codec attributes map.
   */
  public synchronized Map<String, String> getAttributes() {
    return attributes;
  }

  /** Return the sort order of this segment, or null if the index has no sort. */
  public Sort getIndexSort() {
    return indexSort;
  }
}
