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
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.TermVectors;

/**
 * Codec API for reading term vectors:
 *
 * @lucene.experimental
 */
public abstract class TermVectorsReader extends TermVectors implements Cloneable, Closeable {

  /** Sole constructor. (For invocation by subclass constructors, typically implicit.) */
  protected TermVectorsReader() {}

  /**
   * Checks consistency of this reader.
   *
   * <p>Note that this may be costly in terms of I/O, e.g. may involve computing a checksum value
   * against large data files.
   *
   * @lucene.internal
   */
  public abstract void checkIntegrity() throws IOException;

  /** Create a clone that one caller at a time may use to read term vectors. */
  @Override
  public abstract TermVectorsReader clone();

  /**
   * Returns an instance optimized for merging. This instance may only be consumed in the thread
   * that called {@link #getMergeInstance()}.
   *
   * <p>The default implementation returns {@code this}
   */
  public TermVectorsReader getMergeInstance() {
    return this;
  }
}
