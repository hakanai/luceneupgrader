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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.backward_codecs.store;

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.ChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IndexOutput;

/**
 * Utility class to wrap open files
 *
 * @lucene.internal
 */
public final class EndiannessReverserUtil {

  private EndiannessReverserUtil() {
    // no instances
  }

  /** Open an index input */
  public static IndexInput openInput(Directory directory, String name, IOContext context)
      throws IOException {
    return new EndiannessReverserIndexInput(directory.openInput(name, context));
  }

  /** Open a checksum index input */
  public static ChecksumIndexInput openChecksumInput(
      Directory directory, String name, IOContext context) throws IOException {
    return new EndiannessReverserChecksumIndexInput(directory.openChecksumInput(name, context));
  }

  /** Open an index output */
  public static IndexOutput createOutput(Directory directory, String name, IOContext context)
      throws IOException {
    return new EndiannessReverserIndexOutput(directory.createOutput(name, context));
  }

  /** Open a temp index output */
  public static IndexOutput createTempOutput(
      Directory directory, String prefix, String suffix, IOContext context) throws IOException {
    return new EndiannessReverserIndexOutput(directory.createTempOutput(prefix, suffix, context));
  }

  /** wraps a data output */
  public static DataOutput wrapDataOutput(DataOutput dataOutput) {
    if (dataOutput instanceof EndiannessReverserDataOutput) {
      return ((EndiannessReverserDataOutput) dataOutput).out;
    }
    if (dataOutput instanceof EndiannessReverserIndexOutput) {
      return ((EndiannessReverserIndexOutput) dataOutput).getDelegate();
    }
    return new EndiannessReverserDataOutput(dataOutput);
  }

  /** wraps a data input */
  public static DataInput wrapDataInput(DataInput dataInput) {
    if (dataInput instanceof EndiannessReverserDataInput) {
      return ((EndiannessReverserDataInput) dataInput).in;
    }
    if (dataInput instanceof EndiannessReverserIndexInput) {
      return ((EndiannessReverserIndexInput) dataInput).getDelegate();
    }
    return new EndiannessReverserDataInput(dataInput);
  }
}
