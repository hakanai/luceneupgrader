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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.CachingTokenFilter;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.BooleanClause;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.BooleanQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.MultiPhraseQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.PhraseQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.SynonymQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.TermQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.spans.SpanNearQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.spans.SpanOrQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.spans.SpanQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.spans.SpanTermQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.graph.GraphTokenStreamFiniteStrings;

public class QueryBuilder {
  protected Analyzer analyzer;
  protected boolean enablePositionIncrements = true;
  protected boolean enableGraphQueries = true;
  protected boolean autoGenerateMultiTermSynonymsPhraseQuery = false;

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
    
    // TODO: weird that BQ equals/rewrite/scorer doesn't handle this?
    if (fraction == 1) {
      return createBooleanQuery(field, queryText, BooleanClause.Occur.MUST);
    }
    
    Query query = createFieldQuery(analyzer, BooleanClause.Occur.SHOULD, field, queryText, false, 0);
    if (query instanceof BooleanQuery) {
      query = addMinShouldMatchToBoolean((BooleanQuery) query, fraction);
    }
    return query;
  }

  private BooleanQuery addMinShouldMatchToBoolean(BooleanQuery query, float fraction) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.setMinimumNumberShouldMatch((int) (fraction * query.clauses().size()));
    for (BooleanClause clause : query) {
      builder.add(clause);
    }

    return builder.build();
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

  public boolean getAutoGenerateMultiTermSynonymsPhraseQuery() {
    return autoGenerateMultiTermSynonymsPhraseQuery;
  }

  public void setAutoGenerateMultiTermSynonymsPhraseQuery(boolean enable) {
    this.autoGenerateMultiTermSynonymsPhraseQuery = enable;
  }

  protected Query createFieldQuery(Analyzer analyzer, BooleanClause.Occur operator, String field, String queryText, boolean quoted, int phraseSlop) {
    assert operator == BooleanClause.Occur.SHOULD || operator == BooleanClause.Occur.MUST;

    // Use the analyzer to get all the tokens, and then build an appropriate
    // query based on the analysis chain.
    try (TokenStream source = analyzer.tokenStream(field, queryText)) {
      return createFieldQuery(source, operator, field, quoted, phraseSlop);
    } catch (IOException e) {
      throw new RuntimeException("Error analyzing query text", e);
    }
  }

  public void setEnableGraphQueries(boolean v) {
    enableGraphQueries = v;
  }

  public boolean getEnableGraphQueries() {
    return enableGraphQueries;
  }

  protected Query createFieldQuery(TokenStream source, BooleanClause.Occur operator, String field, boolean quoted, int phraseSlop) {
    assert operator == BooleanClause.Occur.SHOULD || operator == BooleanClause.Occur.MUST;

    // Build an appropriate query based on the analysis chain.
    try (CachingTokenFilter stream = new CachingTokenFilter(source)) {
      
      TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
      PositionIncrementAttribute posIncAtt = stream.addAttribute(PositionIncrementAttribute.class);
      PositionLengthAttribute posLenAtt = stream.addAttribute(PositionLengthAttribute.class);

      if (termAtt == null) {
        return null; 
      }
      
      // phase 1: read through the stream and assess the situation:
      // counting the number of tokens/positions and marking if we have any synonyms.
      
      int numTokens = 0;
      int positionCount = 0;
      boolean hasSynonyms = false;
      boolean isGraph = false;

      stream.reset();
      while (stream.incrementToken()) {
        numTokens++;
        int positionIncrement = posIncAtt.getPositionIncrement();
        if (positionIncrement != 0) {
          positionCount += positionIncrement;
        } else {
          hasSynonyms = true;
        }

        int positionLength = posLenAtt.getPositionLength();
        if (enableGraphQueries && positionLength > 1) {
          isGraph = true;
        }
      }
      
      // phase 2: based on token count, presence of synonyms, and options
      // formulate a single term, boolean, or phrase.
      
      if (numTokens == 0) {
        return null;
      } else if (numTokens == 1) {
        // single term
        return analyzeTerm(field, stream);
      } else if (isGraph) {
        // graph
        if (quoted) {
          return analyzeGraphPhrase(stream, field, phraseSlop);
        } else {
          return analyzeGraphBoolean(field, stream, operator);
        }
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

  protected SpanQuery createSpanQuery(TokenStream in, String field) throws IOException {
    TermToBytesRefAttribute termAtt = in.getAttribute(TermToBytesRefAttribute.class);
    if (termAtt == null) {
      return null;
    }

    List<SpanTermQuery> terms = new ArrayList<>();
    while (in.incrementToken()) {
      terms.add(new SpanTermQuery(new Term(field, termAtt.getBytesRef())));
    }

    if (terms.isEmpty()) {
      return null;
    } else if (terms.size() == 1) {
      return terms.get(0);
    } else {
      return new SpanNearQuery(terms.toArray(new SpanTermQuery[0]), 0, true);
    }
  }

  protected Query analyzeTerm(String field, TokenStream stream) throws IOException {
    TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
    
    stream.reset();
    if (!stream.incrementToken()) {
      throw new AssertionError();
    }
    
    return newTermQuery(new Term(field, termAtt.getBytesRef()));
  }
  
  protected Query analyzeBoolean(String field, TokenStream stream) throws IOException {
    TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
    
    stream.reset();
    List<Term> terms = new ArrayList<>();
    while (stream.incrementToken()) {
      terms.add(new Term(field, termAtt.getBytesRef()));
    }
    
    return newSynonymQuery(terms.toArray(new Term[terms.size()]));
  }

  protected void add(BooleanQuery.Builder q, List<Term> current, BooleanClause.Occur operator) {
    if (current.isEmpty()) {
      return;
    }
    if (current.size() == 1) {
      q.add(newTermQuery(current.get(0)), operator);
    } else {
      q.add(newSynonymQuery(current.toArray(new Term[current.size()])), operator);
    }
  }

  protected Query analyzeMultiBoolean(String field, TokenStream stream, BooleanClause.Occur operator) throws IOException {
    BooleanQuery.Builder q = newBooleanQuery();
    List<Term> currentQuery = new ArrayList<>();
    
    TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
    PositionIncrementAttribute posIncrAtt = stream.getAttribute(PositionIncrementAttribute.class);
    
    stream.reset();
    while (stream.incrementToken()) {
      if (posIncrAtt.getPositionIncrement() != 0) {
        add(q, currentQuery, operator);
        currentQuery.clear();
      }
      currentQuery.add(new Term(field, termAtt.getBytesRef()));
    }
    add(q, currentQuery, operator);
    
    return q.build();
  }
  
  protected Query analyzePhrase(String field, TokenStream stream, int slop) throws IOException {
    PhraseQuery.Builder builder = new PhraseQuery.Builder();
    builder.setSlop(slop);
    
    TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);
    PositionIncrementAttribute posIncrAtt = stream.getAttribute(PositionIncrementAttribute.class);
    int position = -1;    
    
    stream.reset();
    while (stream.incrementToken()) {
      if (enablePositionIncrements) {
        position += posIncrAtt.getPositionIncrement();
      } else {
        position += 1;
      }
      builder.add(new Term(field, termAtt.getBytesRef()), position);
    }

    return builder.build();
  }
  
  protected Query analyzeMultiPhrase(String field, TokenStream stream, int slop) throws IOException {
    MultiPhraseQuery.Builder mpqb = newMultiPhraseQueryBuilder();
    mpqb.setSlop(slop);
    
    TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);

    PositionIncrementAttribute posIncrAtt = stream.getAttribute(PositionIncrementAttribute.class);
    int position = -1;  
    
    List<Term> multiTerms = new ArrayList<>();
    stream.reset();
    while (stream.incrementToken()) {
      int positionIncrement = posIncrAtt.getPositionIncrement();
      
      if (positionIncrement > 0 && multiTerms.size() > 0) {
        if (enablePositionIncrements) {
          mpqb.add(multiTerms.toArray(new Term[0]), position);
        } else {
          mpqb.add(multiTerms.toArray(new Term[0]));
        }
        multiTerms.clear();
      }
      position += positionIncrement;
      multiTerms.add(new Term(field, termAtt.getBytesRef()));
    }
    
    if (enablePositionIncrements) {
      mpqb.add(multiTerms.toArray(new Term[0]), position);
    } else {
      mpqb.add(multiTerms.toArray(new Term[0]));
    }
    return mpqb.build();
  }

  protected Query analyzeGraphBoolean(String field, TokenStream source, BooleanClause.Occur operator) throws IOException {
    source.reset();
    GraphTokenStreamFiniteStrings graph = new GraphTokenStreamFiniteStrings(source);
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    int[] articulationPoints = graph.articulationPoints();
    int lastState = 0;
    for (int i = 0; i <= articulationPoints.length; i++) {
      int start = lastState;
      int end = -1;
      if (i < articulationPoints.length) {
        end = articulationPoints[i];
      }
      lastState = end;
      final Query queryPos;
      if (graph.hasSidePath(start)) {
        final Iterator<TokenStream> it = graph.getFiniteStrings(start, end);
        Iterator<Query> queries = new Iterator<Query>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public Query next() {
            TokenStream ts = it.next();
            return createFieldQuery(ts, BooleanClause.Occur.MUST, field, getAutoGenerateMultiTermSynonymsPhraseQuery(), 0);
          }
        };
        queryPos = newGraphSynonymQuery(queries);
      } else {
        Term[] terms = graph.getTerms(field, start);
        assert terms.length > 0;
        if (terms.length == 1) {
          queryPos = newTermQuery(terms[0]);
        } else {
          queryPos = newSynonymQuery(terms);
        }
      }
      if (queryPos != null) {
        builder.add(queryPos, operator);
      }
    }
    return builder.build();
  }

  protected Query analyzeGraphPhrase(TokenStream source, String field, int phraseSlop)
      throws IOException {
    source.reset();
    GraphTokenStreamFiniteStrings graph = new GraphTokenStreamFiniteStrings(source);
    if (phraseSlop > 0) {
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      Iterator<TokenStream> it = graph.getFiniteStrings();
      while (it.hasNext()) {
        Query query = createFieldQuery(it.next(), BooleanClause.Occur.MUST, field, true, phraseSlop);
        if (query != null) {
          builder.add(query, BooleanClause.Occur.SHOULD);
        }
      }
      return builder.build();
    }

    List<SpanQuery> clauses = new ArrayList<>();
    int[] articulationPoints = graph.articulationPoints();
    int lastState = 0;
    int maxClauseCount = BooleanQuery.getMaxClauseCount();
    for (int i = 0; i <= articulationPoints.length; i++) {
      int start = lastState;
      int end = -1;
      if (i < articulationPoints.length) {
        end = articulationPoints[i];
      }
      lastState = end;
      final SpanQuery queryPos;
      if (graph.hasSidePath(start)) {
        List<SpanQuery> queries = new ArrayList<>();
        Iterator<TokenStream> it = graph.getFiniteStrings(start, end);
        while (it.hasNext()) {
          TokenStream ts = it.next();
          SpanQuery q = createSpanQuery(ts, field);
          if (q != null) {
            if (queries.size() >= maxClauseCount) {
              throw new BooleanQuery.TooManyClauses();
            }
            queries.add(q);
          }
        }
        if (queries.size() > 0) {
          queryPos = new SpanOrQuery(queries.toArray(new SpanQuery[0]));
        } else {
          queryPos = null;
        }
      } else {
        Term[] terms = graph.getTerms(field, start);
        assert terms.length > 0;
        if (terms.length == 1) {
          queryPos = new SpanTermQuery(terms[0]);
        } else {
          if (terms.length >= maxClauseCount) {
            throw new BooleanQuery.TooManyClauses();
          }
          SpanTermQuery[] orClauses = new SpanTermQuery[terms.length];
          for (int idx = 0; idx < terms.length; idx++) {
            orClauses[idx] = new SpanTermQuery(terms[idx]);
          }

          queryPos = new SpanOrQuery(orClauses);
        }
      }

      if (queryPos != null) {
        if (clauses.size() >= maxClauseCount) {
          throw new BooleanQuery.TooManyClauses();
        }
        clauses.add(queryPos);
      }
    }

    if (clauses.isEmpty()) {
      return null;
    } else if (clauses.size() == 1) {
      return clauses.get(0);
    } else {
      return new SpanNearQuery(clauses.toArray(new SpanQuery[0]), 0, true);
    }
  }

  protected BooleanQuery.Builder newBooleanQuery() {
    return new BooleanQuery.Builder();
  }
  
  protected Query newSynonymQuery(Term terms[]) {
    return new SynonymQuery(terms);
  }

  protected Query newGraphSynonymQuery(Iterator<Query> queries) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    while (queries.hasNext()) {
      builder.add(queries.next(), BooleanClause.Occur.SHOULD);
    }
    BooleanQuery bq = builder.build();
    if (bq.clauses().size() == 1) {
      return bq.clauses().get(0).getQuery();
    }
    return bq;
  }
  
  protected Query newTermQuery(Term term) {
    return new TermQuery(term);
  }
  
  protected MultiPhraseQuery.Builder newMultiPhraseQueryBuilder() {
    return new MultiPhraseQuery.Builder();
  }
}
