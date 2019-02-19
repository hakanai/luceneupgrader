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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene40;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.FieldsConsumer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.FieldsProducer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.PostingsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.PostingsReaderBase;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.PostingsWriterBase;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.blocktree.BlockTreeTermsReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.blocktree.BlockTreeTermsWriter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsEnum; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.IndexOptions; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.DataOutput; // javadocs




// TODO: this class could be created by wrapping
// BlockTreeTermsDict around Lucene40PostingsBaseFormat; ie
// we should not duplicate the code from that class here:
@Deprecated
public class Lucene40PostingsFormat extends PostingsFormat {

  protected final int minBlockSize;
  protected final int maxBlockSize;

  public Lucene40PostingsFormat() {
    this(BlockTreeTermsWriter.DEFAULT_MIN_BLOCK_SIZE, BlockTreeTermsWriter.DEFAULT_MAX_BLOCK_SIZE);
  }


  private Lucene40PostingsFormat(int minBlockSize, int maxBlockSize) {
    super("Lucene40");
    this.minBlockSize = minBlockSize;
    assert minBlockSize > 1;
    this.maxBlockSize = maxBlockSize;
  }

  @Override
  public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    throw new UnsupportedOperationException("this codec can only be used for reading");
  }

  @Override
  public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
    PostingsReaderBase postings = new Lucene40PostingsReader(state.directory, state.fieldInfos, state.segmentInfo, state.context, state.segmentSuffix);

    boolean success = false;
    try {
      FieldsProducer ret = new BlockTreeTermsReader(
                                                    state.directory,
                                                    state.fieldInfos,
                                                    state.segmentInfo,
                                                    postings,
                                                    state.context,
                                                    state.segmentSuffix,
                                                    state.termsIndexDivisor);
      success = true;
      return ret;
    } finally {
      if (!success) {
        postings.close();
      }
    }
  }

  static final String FREQ_EXTENSION = "frq";

  static final String PROX_EXTENSION = "prx";

  @Override
  public String toString() {
    return getName() + "(minBlockSize=" + minBlockSize + " maxBlockSize=" + maxBlockSize + ")";
  }
}
