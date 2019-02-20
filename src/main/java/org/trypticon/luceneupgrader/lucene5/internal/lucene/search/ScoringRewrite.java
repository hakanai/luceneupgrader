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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.search;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TermState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.MultiTermQuery.RewriteMethod;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ByteBlockPool;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRefHash.DirectBytesStartArray;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRefHash;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.RamUsageEstimator;


public abstract class ScoringRewrite<B> extends TermCollectingRewrite<B> {


  public final static ScoringRewrite<BooleanQuery.Builder> SCORING_BOOLEAN_REWRITE = new ScoringRewrite<BooleanQuery.Builder>() {
    @Override
    protected BooleanQuery.Builder getTopLevelBuilder() {
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      builder.setDisableCoord(true);
      return builder;
    }
    
    protected Query build(BooleanQuery.Builder builder) {
      return builder.build();
    }
    
    @Override
    protected void addClause(BooleanQuery.Builder topLevel, Term term, int docCount,
        float boost, TermContext states) {
      final TermQuery tq = new TermQuery(term, states);
      topLevel.add(new BoostQuery(tq, boost), BooleanClause.Occur.SHOULD);
    }
    
    @Override
    protected void checkMaxClauseCount(int count) {
      if (count > BooleanQuery.getMaxClauseCount())
        throw new BooleanQuery.TooManyClauses();
    }
  };
  

  public final static RewriteMethod CONSTANT_SCORE_BOOLEAN_REWRITE = new RewriteMethod() {
    @Override
    public Query rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
      final Query bq = SCORING_BOOLEAN_REWRITE.rewrite(reader, query);
      // strip the scores off
      return new ConstantScoreQuery(bq);
    }
  };

  protected abstract void checkMaxClauseCount(int count) throws IOException;
  
  @Override
  public final Query rewrite(final IndexReader reader, final MultiTermQuery query) throws IOException {
    final B builder = getTopLevelBuilder();
    final ParallelArraysTermCollector col = new ParallelArraysTermCollector();
    collectTerms(reader, query, col);
    
    final int size = col.terms.size();
    if (size > 0) {
      final int sort[] = col.terms.sort(BytesRef.getUTF8SortedAsUnicodeComparator());
      final float[] boost = col.array.boost;
      final TermContext[] termStates = col.array.termState;
      for (int i = 0; i < size; i++) {
        final int pos = sort[i];
        final Term term = new Term(query.getField(), col.terms.get(pos, new BytesRef()));
        assert termStates[pos].hasOnlyRealTerms() == false || reader.docFreq(term) == termStates[pos].docFreq();
        addClause(builder, term, termStates[pos].docFreq(), boost[pos], termStates[pos]);
      }
    }
    return build(builder);
  }

  final class ParallelArraysTermCollector extends TermCollector {
    final TermFreqBoostByteStart array = new TermFreqBoostByteStart(16);
    final BytesRefHash terms = new BytesRefHash(new ByteBlockPool(new ByteBlockPool.DirectAllocator()), 16, array);
    TermsEnum termsEnum;

    private BoostAttribute boostAtt;
    
    @Override
    public void setNextEnum(TermsEnum termsEnum) {
      this.termsEnum = termsEnum;
      this.boostAtt = termsEnum.attributes().addAttribute(BoostAttribute.class);
    }
  
    @Override
    public boolean collect(BytesRef bytes) throws IOException {
      final int e = terms.add(bytes);
      final TermState state = termsEnum.termState();
      assert state != null; 
      if (e < 0) {
        // duplicate term: update docFreq
        final int pos = (-e)-1;
        array.termState[pos].register(state, readerContext.ord, termsEnum.docFreq(), termsEnum.totalTermFreq());
        assert array.boost[pos] == boostAtt.getBoost() : "boost should be equal in all segment TermsEnums";
      } else {
        // new entry: we populate the entry initially
        array.boost[e] = boostAtt.getBoost();
        array.termState[e] = new TermContext(topReaderContext, state, readerContext.ord, termsEnum.docFreq(), termsEnum.totalTermFreq());
        ScoringRewrite.this.checkMaxClauseCount(terms.size());
      }
      return true;
    }
  }
  
  static final class TermFreqBoostByteStart extends DirectBytesStartArray  {
    float[] boost;
    TermContext[] termState;
    
    public TermFreqBoostByteStart(int initSize) {
      super(initSize);
    }

    @Override
    public int[] init() {
      final int[] ord = super.init();
      boost = new float[ArrayUtil.oversize(ord.length, RamUsageEstimator.NUM_BYTES_FLOAT)];
      termState = new TermContext[ArrayUtil.oversize(ord.length, RamUsageEstimator.NUM_BYTES_OBJECT_REF)];
      assert termState.length >= ord.length && boost.length >= ord.length;
      return ord;
    }

    @Override
    public int[] grow() {
      final int[] ord = super.grow();
      boost = ArrayUtil.grow(boost, ord.length);
      if (termState.length < ord.length) {
        TermContext[] tmpTermState = new TermContext[ArrayUtil.oversize(ord.length, RamUsageEstimator.NUM_BYTES_OBJECT_REF)];
        System.arraycopy(termState, 0, tmpTermState, 0, termState.length);
        termState = tmpTermState;
      }     
      assert termState.length >= ord.length && boost.length >= ord.length;
      return ord;
    }

    @Override
    public int[] clear() {
     boost = null;
     termState = null;
     return super.clear();
    }
    
  }
}
