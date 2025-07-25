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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs;

import java.io.Closeable;
import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.BinaryDocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.DocValuesType;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SortedDocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SortedNumericDocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SortedSetDocValues;

/**
 * Abstract API that produces numeric, binary, sorted, sortedset, and sortednumeric docvalues.
 *
 * @lucene.experimental
 */
public abstract class DocValuesProducer implements Closeable {

  /** Sole constructor. (For invocation by subclass constructors, typically implicit.) */
  protected DocValuesProducer() {}

  /**
   * Returns {@link NumericDocValues} for this field. The returned instance need not be thread-safe:
   * it will only be used by a single thread. The behavior is undefined if the doc values type of
   * the given field is not {@link DocValuesType#NUMERIC}. The return value is never {@code null}.
   */
  public abstract NumericDocValues getNumeric(FieldInfo field) throws IOException;

  /**
   * Returns {@link BinaryDocValues} for this field. The returned instance need not be thread-safe:
   * it will only be used by a single thread. The behavior is undefined if the doc values type of
   * the given field is not {@link DocValuesType#BINARY}. The return value is never {@code null}.
   */
  public abstract BinaryDocValues getBinary(FieldInfo field) throws IOException;

  /**
   * Returns {@link SortedDocValues} for this field. The returned instance need not be thread-safe:
   * it will only be used by a single thread. The behavior is undefined if the doc values type of
   * the given field is not {@link DocValuesType#SORTED}. The return value is never {@code null}.
   */
  public abstract SortedDocValues getSorted(FieldInfo field) throws IOException;

  /**
   * Returns {@link SortedNumericDocValues} for this field. The returned instance need not be
   * thread-safe: it will only be used by a single thread. The behavior is undefined if the doc
   * values type of the given field is not {@link DocValuesType#SORTED_NUMERIC}. The return value is
   * never {@code null}.
   */
  public abstract SortedNumericDocValues getSortedNumeric(FieldInfo field) throws IOException;

  /**
   * Returns {@link SortedSetDocValues} for this field. The returned instance need not be
   * thread-safe: it will only be used by a single thread. The behavior is undefined if the doc
   * values type of the given field is not {@link DocValuesType#SORTED_SET}. The return value is
   * never {@code null}.
   */
  public abstract SortedSetDocValues getSortedSet(FieldInfo field) throws IOException;

  /**
   * Checks consistency of this producer
   *
   * <p>Note that this may be costly in terms of I/O, e.g. may involve computing a checksum value
   * against large data files.
   *
   * @lucene.internal
   */
  public abstract void checkIntegrity() throws IOException;

  /**
   * Returns an instance optimized for merging. This instance may only be consumed in the thread
   * that called {@link #getMergeInstance()}.
   *
   * <p>The default implementation returns {@code this}
   */
  public DocValuesProducer getMergeInstance() {
    return this;
  }
}
