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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.util;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.analysis.CachingTokenFilter;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.BooleanClause;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.BooleanQuery;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.MultiPhraseQuery;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.PhraseQuery;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.TermQuery;

public class QueryBuilder {
  private Analyzer analyzer;
  private boolean enablePositionIncrements = true;
  
  public QueryBuilder(Analyzer analyzer) {
    this.analyzer = analyzer;
  }
  

  public Query createBooleanQuery(String field, String queryText) {
    return createBooleanQuery(field, queryText, BooleanClause.Occur.SHOULD);
  }
  

  public Query createBooleanQuery(String field, String queryText, BooleanClause.Occur operator) {
    if (operator != BooleanClause.Occur.SHOULD && operator != BooleanClause.Occur.MUST) {
      throw new IllegalArgumentException("invalid operator: only SHOULD or MUST are allowed");
    }
    return createFieldQuery(analyzer, operator, field, queryText, false, 0);
  }
  

  public Query createPhraseQuery(String field, String queryText) {
    return createPhraseQuery(field, queryText, 0);
  }
  

  public Query createPhraseQuery(String field, String queryText, int phraseSlop) {
    return createFieldQuery(analyzer, BooleanClause.Occur.MUST, field, queryText, true, phraseSlop);
  }
  

  public Query createMinShouldMatchQuery(String field, String queryText, float fraction) {
    if (Float.isNaN(fraction) || fraction < 0 || fraction > 1) {
      throw new IllegalArgumentException("fraction should be >= 0 and <= 1");
    }
    
    // TODO: wierd that BQ equals/rewrite/scorer doesn't handle this?
    if (fraction == 1) {
      return createBooleanQuery(field, queryText, BooleanClause.Occur.MUST);
    }
    
    Query query = createFieldQuery(analyzer, BooleanClause.Occur.SHOULD, field, queryText, false, 0);
    if (query instanceof BooleanQuery) {
      BooleanQuery bq = (BooleanQuery) query;
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      builder.setDisableCoord(bq.isCoordDisabled());
      builder.setMinimumNumberShouldMatch((int) (fraction * bq.clauses().size()));
      for (BooleanClause clause : bq) {
        builder.add(clause);
      }
      query = builder.build();
    }
    return query;
  }
  

  public Analyzer getAnalyzer() {
    return analyzer;
  }
  

  public void setAnalyzer(Analyzer analyzer) {
    this.analyzer = analyzer;
  }
  
  public boolean getEnablePositionIncrements() {
    return enablePositionIncrements;
  }
  
  public void setEnablePositionIncrements(boolean enable) {
    this.enablePositionIncrements = enable;
  }

  protected final Query createFieldQuery(Analyzer analyzer, BooleanClause.Occur operator, String field, String queryText, boolean quoted, int phraseSlop) {
    assert operator == BooleanClause.Occur.SHOULD || operator == BooleanClause.Occur.MUST;
    
    // Use the analyzer to get all the tokens, and then build an appropriate
    // query based on the analysis chain.
    
    try (TokenStream source = analyzer.tokenStream(field, queryText);
         CachingTokenFilter stream = new CachingTokenFilter(source)) {
      
      TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
      PositionIncrementAttribute posIncAtt = stream.addAttribute(PositionIncrementAttribute.class);
      
      if (termAtt == null) {
        return null; 
      }
      
      // phase 1: read through the stream and assess the situation:
      // counting the number of tokens/positions and marking if we have any synonyms.
      
      int numTokens = 0;
      int positionCount = 0;
      boolean hasSynonyms = false;

      stream.reset();
      while (stream.incrementToken()) {
        numTokens++;
        int positionIncrement = posIncAtt.getPositionIncrement();
        if (positionIncrement != 0) {
          positionCount += positionIncrement;
        } else {
          hasSynonyms = true;
        }
      }
      
      // phase 2: based on token count, presence of synonyms, and options
      // formulate a single term, boolean, or phrase.
      
      if (numTokens == 0) {
        return null;
      } else if (numTokens == 1) {
        // single term
        return analyzeTerm(field, stream);
      } else if (quoted && positionCount > 1) {
        // phrase
        if (hasSynonyms) {
          // complex phrase with synonyms
          return analyzeMultiPhrase(field, stream, phraseSlop);
        } else {
          // simple phrase
          return analyzePhrase(field, stream, phraseSlop);
        }
      } else {
        // boolean
        if (positionCount == 1) {
          // only one position, with synonyms
          return analyzeBoolean(field, stream);
        } else {
          // complex case: multiple positions
          return analyzeMultiBoolean(field, stream, operator);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Error analyzing query text", e);
    }
  }
  

  private Query analyzeTerm(String field, TokenStream stream) throws IOException {
    TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
    
    stream.reset();
    if (!stream.incrementToken()) {
      throw new AssertionError();
    }
    
    return newTermQuery(new Term(field, termAtt.getBytesRef()));
  }
  

  private Query analyzeBoolean(String field, TokenStream stream) throws IOException {
    BooleanQuery.Builder q = new BooleanQuery.Builder();
    q.setDisableCoord(true);

    TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
    
    stream.reset();
    while (stream.incrementToken()) {
      Query currentQuery = newTermQuery(new Term(field, termAtt.getBytesRef()));
      q.add(currentQuery, BooleanClause.Occur.SHOULD);
    }
    
    return q.build();
  }

  private void add(BooleanQuery.Builder q, BooleanQuery current, BooleanClause.Occur operator) {
    if (current.clauses().isEmpty()) {
      return;
    }
    if (current.clauses().size() == 1) {
      q.add(current.clauses().iterator().next().getQuery(), operator);
    } else {
      q.add(current, operator);
    }
  }


  private Query analyzeMultiBoolean(String field, TokenStream stream, BooleanClause.Occur operator) throws IOException {
    BooleanQuery.Builder q = newBooleanQuery(false);
    BooleanQuery.Builder currentQuery = newBooleanQuery(true);
    
    TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
    PositionIncrementAttribute posIncrAtt = stream.getAttribute(PositionIncrementAttribute.class);
    
    stream.reset();
    while (stream.incrementToken()) {
      BytesRef bytes = termAtt.getBytesRef();
      if (posIncrAtt.getPositionIncrement() != 0) {
        add(q, currentQuery.build(), operator);
        currentQuery = newBooleanQuery(true);
      }
      currentQuery.add(newTermQuery(new Term(field, termAtt.getBytesRef())), BooleanClause.Occur.SHOULD);
    }
    add(q, currentQuery.build(), operator);
    
    return q.build();
  }
  

  private Query analyzePhrase(String field, TokenStream stream, int slop) throws IOException {
    PhraseQuery.Builder builder = new PhraseQuery.Builder();
    builder.setSlop(slop);
    
    TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
    PositionIncrementAttribute posIncrAtt = stream.getAttribute(PositionIncrementAttribute.class);
    int position = -1;    
    
    stream.reset();
    while (stream.incrementToken()) {
      BytesRef bytes = termAtt.getBytesRef();
      if (enablePositionIncrements) {
        position += posIncrAtt.getPositionIncrement();
      } else {
        position += 1;
      }
      builder.add(new Term(field, bytes), position);
    }

    return builder.build();
  }
  

  private Query analyzeMultiPhrase(String field, TokenStream stream, int slop) throws IOException {
    MultiPhraseQuery mpq = newMultiPhraseQuery();
    mpq.setSlop(slop);
    
    TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);

    PositionIncrementAttribute posIncrAtt = stream.getAttribute(PositionIncrementAttribute.class);
    int position = -1;  
    
    List<Term> multiTerms = new ArrayList<>();
    stream.reset();
    while (stream.incrementToken()) {
      int positionIncrement = posIncrAtt.getPositionIncrement();
      
      if (positionIncrement > 0 && multiTerms.size() > 0) {
        if (enablePositionIncrements) {
          mpq.add(multiTerms.toArray(new Term[0]), position);
        } else {
          mpq.add(multiTerms.toArray(new Term[0]));
        }
        multiTerms.clear();
      }
      position += positionIncrement;
      multiTerms.add(new Term(field, termAtt.getBytesRef()));
    }
    
    if (enablePositionIncrements) {
      mpq.add(multiTerms.toArray(new Term[0]), position);
    } else {
      mpq.add(multiTerms.toArray(new Term[0]));
    }
    return mpq;
  }
  
  protected BooleanQuery.Builder newBooleanQuery(boolean disableCoord) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.setDisableCoord(disableCoord);
    return builder;
  }
  
  protected Query newTermQuery(Term term) {
    return new TermQuery(term);
  }
  
  protected MultiPhraseQuery newMultiPhraseQuery() {
    return new MultiPhraseQuery();
  }
}
