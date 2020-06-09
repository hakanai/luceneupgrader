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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs;


import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.Fields;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.MappedMultiFields;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.MultiFields;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.ReaderSlice;


public abstract class FieldsConsumer implements Closeable {

  protected FieldsConsumer() {
  }

  // TODO: can we somehow compute stats for you...?

  // TODO: maybe we should factor out "limited" (only
  // iterables, no counts/stats) base classes from
  // Fields/Terms/Docs/AndPositions?

  public abstract void write(Fields fields) throws IOException;
  
  public void merge(MergeState mergeState) throws IOException {
    final List<Fields> fields = new ArrayList<>();
    final List<ReaderSlice> slices = new ArrayList<>();

    int docBase = 0;

    for(int readerIndex=0;readerIndex<mergeState.fieldsProducers.length;readerIndex++) {
      final FieldsProducer f = mergeState.fieldsProducers[readerIndex];

      final int maxDoc = mergeState.maxDocs[readerIndex];
      f.checkIntegrity();
      slices.add(new ReaderSlice(docBase, maxDoc, readerIndex));
      fields.add(f);
      docBase += maxDoc;
    }

    Fields mergedFields = new MappedMultiFields(mergeState, 
                                                new MultiFields(fields.toArray(Fields.EMPTY_ARRAY),
                                                                slices.toArray(ReaderSlice.EMPTY_ARRAY)));
    write(mergedFields);
  }

  // NOTE: strange but necessary so javadocs linting is happy:
  @Override
  public abstract void close() throws IOException;
}
