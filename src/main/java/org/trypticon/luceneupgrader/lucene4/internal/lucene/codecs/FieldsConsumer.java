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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs;

import java.io.Closeable;
import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Fields;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentWriteState; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Terms;

public abstract class FieldsConsumer implements Closeable {

  protected FieldsConsumer() {
  }

  public abstract TermsConsumer addField(FieldInfo field) throws IOException;
  
  @Override
  public abstract void close() throws IOException;


  public void merge(MergeState mergeState, Fields fields) throws IOException {
    for (String field : fields) {
      FieldInfo info = mergeState.fieldInfos.fieldInfo(field);
      assert info != null : "FieldInfo for field is null: "+ field;
      Terms terms = fields.terms(field);
      if (terms != null) {
        final TermsConsumer termsConsumer = addField(info);
        termsConsumer.merge(mergeState, info.getIndexOptions(), terms.iterator(null));
      }
    }
  }
}
