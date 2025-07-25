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

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SegmentInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IOContext;

/**
 * Encodes/decodes compound files
 *
 * @lucene.experimental
 */
public abstract class CompoundFormat {
  /** Sole constructor. (For invocation by subclass constructors, typically implicit.) */
  // Explicitly declared so that we have non-empty javadoc
  protected CompoundFormat() {}

  // TODO: this is very minimal. If we need more methods,
  // we can add 'producer' classes.

  /** Returns a Directory view (read-only) for the compound files in this segment */
  public abstract CompoundDirectory getCompoundReader(
      Directory dir, SegmentInfo si, IOContext context) throws IOException;

  /**
   * Packs the provided segment's files into a compound format. All files referenced by the provided
   * {@link SegmentInfo} must have {@link CodecUtil#writeIndexHeader} and {@link
   * CodecUtil#writeFooter}.
   */
  public abstract void write(Directory dir, SegmentInfo si, IOContext context) throws IOException;
}
