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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.blocktree;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.FieldsProducer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.PostingsReaderBase;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexOptions;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.PrefixQuery;  // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.TermRangeQuery;  // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Accountables;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.fst.ByteSequenceOutputs;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.fst.Outputs;

public final class BlockTreeTermsReader extends FieldsProducer {

  static final Outputs<BytesRef> FST_OUTPUTS = ByteSequenceOutputs.getSingleton();
  
  static final BytesRef NO_OUTPUT = FST_OUTPUTS.getNoOutput();

  static final int OUTPUT_FLAGS_NUM_BITS = 2;
  static final int OUTPUT_FLAGS_MASK = 0x3;
  static final int OUTPUT_FLAG_IS_FLOOR = 0x1;
  static final int OUTPUT_FLAG_HAS_TERMS = 0x2;

  static final String TERMS_EXTENSION = "tim";
  final static String TERMS_CODEC_NAME = "BlockTreeTermsDict";

  public static final int VERSION_START = 0;

  public static final int VERSION_AUTO_PREFIX_TERMS = 1;

  public static final int VERSION_AUTO_PREFIX_TERMS_COND = 2;

  public static final int VERSION_CURRENT = VERSION_AUTO_PREFIX_TERMS_COND;

  static final String TERMS_INDEX_EXTENSION = "tip";
  final static String TERMS_INDEX_CODEC_NAME = "BlockTreeTermsIndex";

  // Open input to the main terms dict file (_X.tib)
  final IndexInput termsIn;

  //private static final boolean DEBUG = BlockTreeTermsWriter.DEBUG;

  // Reads the terms dict entries, to gather state to
  // produce DocsEnum on demand
  final PostingsReaderBase postingsReader;

  private final TreeMap<String,FieldReader> fields = new TreeMap<>();

  private long dirOffset;

  private long indexDirOffset;

  final String segment;
  
  final int version;

  final boolean anyAutoPrefixTerms;

  public BlockTreeTermsReader(PostingsReaderBase postingsReader, SegmentReadState state) throws IOException {
    boolean success = false;
    IndexInput indexIn = null;
    
    this.postingsReader = postingsReader;
    this.segment = state.segmentInfo.name;
    
    String termsName = IndexFileNames.segmentFileName(segment, state.segmentSuffix, TERMS_EXTENSION);
    try {
      termsIn = state.directory.openInput(termsName, state.context);
      version = CodecUtil.checkIndexHeader(termsIn, TERMS_CODEC_NAME, VERSION_START, VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix);

      if (version < VERSION_AUTO_PREFIX_TERMS) {
        // Old (pre-5.2.0) index, no auto-prefix terms:
        this.anyAutoPrefixTerms = false;
      } else if (version == VERSION_AUTO_PREFIX_TERMS) {
        // 5.2.x index, might have auto-prefix terms:
        this.anyAutoPrefixTerms = true;
      } else {
        // 5.3.x index, we record up front if we may have written any auto-prefix terms:
        assert version >= VERSION_AUTO_PREFIX_TERMS_COND;
        byte b = termsIn.readByte();
        if (b == 0) {
          this.anyAutoPrefixTerms = false;
        } else if (b == 1) {
          this.anyAutoPrefixTerms = true;
        } else {
          throw new CorruptIndexException("invalid anyAutoPrefixTerms: expected 0 or 1 but got " + b, termsIn);
        }
      }

      String indexName = IndexFileNames.segmentFileName(segment, state.segmentSuffix, TERMS_INDEX_EXTENSION);
      indexIn = state.directory.openInput(indexName, state.context);
      CodecUtil.checkIndexHeader(indexIn, TERMS_INDEX_CODEC_NAME, version, version, state.segmentInfo.getId(), state.segmentSuffix);
      CodecUtil.checksumEntireFile(indexIn);

      // Have PostingsReader init itself
      postingsReader.init(termsIn, state);
      
      // NOTE: data file is too costly to verify checksum against all the bytes on open,
      // but for now we at least verify proper structure of the checksum footer: which looks
      // for FOOTER_MAGIC + algorithmID. This is cheap and can detect some forms of corruption
      // such as file truncation.
      CodecUtil.retrieveChecksum(termsIn);

      // Read per-field details
      seekDir(termsIn, dirOffset);
      seekDir(indexIn, indexDirOffset);

      final int numFields = termsIn.readVInt();
      if (numFields < 0) {
        throw new CorruptIndexException("invalid numFields: " + numFields, termsIn);
      }

      for (int i = 0; i < numFields; ++i) {
        final int field = termsIn.readVInt();
        final long numTerms = termsIn.readVLong();
        if (numTerms <= 0) {
          throw new CorruptIndexException("Illegal numTerms for field number: " + field, termsIn);
        }
        final int numBytes = termsIn.readVInt();
        if (numBytes < 0) {
          throw new CorruptIndexException("invalid rootCode for field number: " + field + ", numBytes=" + numBytes, termsIn);
        }
        final BytesRef rootCode = new BytesRef(new byte[numBytes]);
        termsIn.readBytes(rootCode.bytes, 0, numBytes);
        rootCode.length = numBytes;
        final FieldInfo fieldInfo = state.fieldInfos.fieldInfo(field);
        if (fieldInfo == null) {
          throw new CorruptIndexException("invalid field number: " + field, termsIn);
        }
        final long sumTotalTermFreq = fieldInfo.getIndexOptions() == IndexOptions.DOCS ? -1 : termsIn.readVLong();
        final long sumDocFreq = termsIn.readVLong();
        final int docCount = termsIn.readVInt();
        final int longsSize = termsIn.readVInt();
        if (longsSize < 0) {
          throw new CorruptIndexException("invalid longsSize for field: " + fieldInfo.name + ", longsSize=" + longsSize, termsIn);
        }
        BytesRef minTerm = readBytesRef(termsIn);
        BytesRef maxTerm = readBytesRef(termsIn);
        if (docCount < 0 || docCount > state.segmentInfo.maxDoc()) { // #docs with field must be <= #docs
          throw new CorruptIndexException("invalid docCount: " + docCount + " maxDoc: " + state.segmentInfo.maxDoc(), termsIn);
        }
        if (sumDocFreq < docCount) {  // #postings must be >= #docs with field
          throw new CorruptIndexException("invalid sumDocFreq: " + sumDocFreq + " docCount: " + docCount, termsIn);
        }
        if (sumTotalTermFreq != -1 && sumTotalTermFreq < sumDocFreq) { // #positions must be >= #postings
          throw new CorruptIndexException("invalid sumTotalTermFreq: " + sumTotalTermFreq + " sumDocFreq: " + sumDocFreq, termsIn);
        }
        final long indexStartFP = indexIn.readVLong();
        FieldReader previous = fields.put(fieldInfo.name,       
                                          new FieldReader(this, fieldInfo, numTerms, rootCode, sumTotalTermFreq, sumDocFreq, docCount,
                                                          indexStartFP, longsSize, indexIn, minTerm, maxTerm));
        if (previous != null) {
          throw new CorruptIndexException("duplicate field: " + fieldInfo.name, termsIn);
        }
      }
      
      indexIn.close();
      success = true;
    } finally {
      if (!success) {
        // this.close() will close in:
        IOUtils.closeWhileHandlingException(indexIn, this);
      }
    }
  }

  private static BytesRef readBytesRef(IndexInput in) throws IOException {
    BytesRef bytes = new BytesRef();
    bytes.length = in.readVInt();
    bytes.bytes = new byte[bytes.length];
    in.readBytes(bytes.bytes, 0, bytes.length);
    return bytes;
  }

  private void seekDir(IndexInput input, long dirOffset)
      throws IOException {
    input.seek(input.length() - CodecUtil.footerLength() - 8);
    dirOffset = input.readLong();
    input.seek(dirOffset);
  }

  // for debugging
  // private static String toHex(int v) {
  //   return "0x" + Integer.toHexString(v);
  // }

  @Override
  public void close() throws IOException {
    try {
      IOUtils.close(termsIn, postingsReader);
    } finally { 
      // Clear so refs to terms index is GCable even if
      // app hangs onto us:
      fields.clear();
    }
  }

  @Override
  public Iterator<String> iterator() {
    return Collections.unmodifiableSet(fields.keySet()).iterator();
  }

  @Override
  public Terms terms(String field) throws IOException {
    assert field != null;
    return fields.get(field);
  }

  @Override
  public int size() {
    return fields.size();
  }

  // for debugging
  String brToString(BytesRef b) {
    if (b == null) {
      return "null";
    } else {
      try {
        return b.utf8ToString() + " " + b;
      } catch (Throwable t) {
        // If BytesRef isn't actually UTF8, or it's eg a
        // prefix of UTF8 that ends mid-unicode-char, we
        // fallback to hex:
        return b.toString();
      }
    }
  }

  @Override
  public long ramBytesUsed() {
    long sizeInBytes = postingsReader.ramBytesUsed();
    for(FieldReader reader : fields.values()) {
      sizeInBytes += reader.ramBytesUsed();
    }
    return sizeInBytes;
  }

  @Override
  public Collection<Accountable> getChildResources() {
    List<Accountable> resources = new ArrayList<>();
    resources.addAll(Accountables.namedAccountables("field", fields));
    resources.add(Accountables.namedAccountable("delegate", postingsReader));
    return Collections.unmodifiableList(resources);
  }

  @Override
  public void checkIntegrity() throws IOException { 
    // term dictionary
    CodecUtil.checksumEntireFile(termsIn);
      
    // postings
    postingsReader.checkIntegrity();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(fields=" + fields.size() + ",delegate=" + postingsReader + ")";
  }
}
