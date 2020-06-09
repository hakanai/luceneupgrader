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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.lucene50;



import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.BlockTermState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.FieldsConsumer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.FieldsProducer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.MultiLevelSkipListWriter;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.PostingsFormat;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.PostingsReaderBase;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.PostingsWriterBase;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.blocktree.BlockTreeTermsReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.blocktree.BlockTreeTermsWriter;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexOptions;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.TermState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.packed.PackedInts;


public final class Lucene50PostingsFormat extends PostingsFormat {
  public static final String DOC_EXTENSION = "doc";

  public static final String POS_EXTENSION = "pos";

  public static final String PAY_EXTENSION = "pay";
  
  static final int MAX_SKIP_LEVELS = 10;

  final static String TERMS_CODEC = "Lucene50PostingsWriterTerms";
  final static String DOC_CODEC = "Lucene50PostingsWriterDoc";
  final static String POS_CODEC = "Lucene50PostingsWriterPos";
  final static String PAY_CODEC = "Lucene50PostingsWriterPay";

  // Increment version to change it
  final static int VERSION_START = 0;
  final static int VERSION_CURRENT = VERSION_START;

  private final int minTermBlockSize;
  private final int maxTermBlockSize;

  // NOTE: must be multiple of 64 because of PackedInts long-aligned encoding/decoding
  public final static int BLOCK_SIZE = 128;

  public Lucene50PostingsFormat() {
    this(BlockTreeTermsWriter.DEFAULT_MIN_BLOCK_SIZE, BlockTreeTermsWriter.DEFAULT_MAX_BLOCK_SIZE);
  }

  public Lucene50PostingsFormat(int minTermBlockSize, int maxTermBlockSize) {
    super("Lucene50");
    BlockTreeTermsWriter.validateSettings(minTermBlockSize, maxTermBlockSize);
    this.minTermBlockSize = minTermBlockSize;
    this.maxTermBlockSize = maxTermBlockSize;
  }

  @Override
  public String toString() {
    return getName() + "(blocksize=" + BLOCK_SIZE + ")";
  }

  @Override
  public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    PostingsWriterBase postingsWriter = new Lucene50PostingsWriter(state);

    boolean success = false;
    try {
      FieldsConsumer ret = new BlockTreeTermsWriter(state, 
                                                    postingsWriter,
                                                    minTermBlockSize, 
                                                    maxTermBlockSize);
      success = true;
      return ret;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(postingsWriter);
      }
    }
  }

  @Override
  public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
    PostingsReaderBase postingsReader = new Lucene50PostingsReader(state);
    boolean success = false;
    try {
      FieldsProducer ret = new BlockTreeTermsReader(postingsReader, state);
      success = true;
      return ret;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(postingsReader);
      }
    }
  }
  
  final static class IntBlockTermState extends BlockTermState {
    long docStartFP = 0;
    long posStartFP = 0;
    long payStartFP = 0;
    long skipOffset = -1;
    long lastPosBlockOffset = -1;
    // docid when there is a single pulsed posting, otherwise -1
    // freq is always implicitly totalTermFreq in this case.
    int singletonDocID = -1;

    @Override
    public IntBlockTermState clone() {
      IntBlockTermState other = new IntBlockTermState();
      other.copyFrom(this);
      return other;
    }

    @Override
    public void copyFrom(TermState _other) {
      super.copyFrom(_other);
      IntBlockTermState other = (IntBlockTermState) _other;
      docStartFP = other.docStartFP;
      posStartFP = other.posStartFP;
      payStartFP = other.payStartFP;
      lastPosBlockOffset = other.lastPosBlockOffset;
      skipOffset = other.skipOffset;
      singletonDocID = other.singletonDocID;
    }

    @Override
    public String toString() {
      return super.toString() + " docStartFP=" + docStartFP + " posStartFP=" + posStartFP + " payStartFP=" + payStartFP + " lastPosBlockOffset=" + lastPosBlockOffset + " singletonDocID=" + singletonDocID;
    }
  }
}
