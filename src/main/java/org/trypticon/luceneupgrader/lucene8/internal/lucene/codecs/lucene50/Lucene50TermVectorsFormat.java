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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.lucene50;


import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.TermVectorsFormat;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.compressing.FieldsIndexWriter;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.lucene87.Lucene87StoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.compressing.CompressingTermVectorsFormat;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.compressing.CompressionMode;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.packed.BlockPackedWriter;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.packed.PackedInts;

public final class Lucene50TermVectorsFormat extends CompressingTermVectorsFormat {

  public Lucene50TermVectorsFormat() {
    super("Lucene50TermVectorsData", "", CompressionMode.FAST, 1 << 12, 128, 10);
  }

}
