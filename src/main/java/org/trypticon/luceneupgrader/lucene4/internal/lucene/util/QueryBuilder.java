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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.CachingTokenFilter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.BooleanClause;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.BooleanQuery;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.MultiPhraseQuery;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.PhraseQuery;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.TermQuery;

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
      bq.setMinimumNumberShouldMatch((int) (fraction * bq.clauses().size()));
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
    // Use the analyzer to get all the tokens, and then build a TermQuery,
    // PhraseQuery, or nothing based on the term count
    CachingTokenFilter buffer = null;
    TermToBytesRefAttribute termAtt = null;
    PositionIncrementAttribute posIncrAtt = null;
    int numTokens = 0;
    int positionCount = 0;
    boolean severalTokensAtSamePosition = false;
    boolean hasMoreTokens = false;    
    
    TokenStream source = null;
    try {
      source = analyzer.tokenStream(field, queryText);
      source.reset();
      buffer = new CachingTokenFilter(source);
      buffer.reset();

      termAtt = buffer.getAttribute(TermToBytesRefAttribute.class);
      posIncrAtt = buffer.getAttribute(PositionIncrementAttribute.class);

      if (termAtt != null) {
        try {
          hasMoreTokens = buffer.incrementToken();
          while (hasMoreTokens) {
            numTokens++;
            int positionIncrement = (posIncrAtt != null) ? posIncrAtt.getPositionIncrement() : 1;
            if (positionIncrement != 0) {
              positionCount += positionIncrement;
            } else {
              severalTokensAtSamePosition = true;
            }
            hasMoreTokens = buffer.incrementToken();
          }
        } catch (IOException e) {
          // ignore
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Error analyzing query text", e);
    } finally {
      IOUtils.closeWhileHandlingException(source);
    }
    
    // rewind the buffer stream
    buffer.reset();

    BytesRef bytes = termAtt == null ? null : termAtt.getBytesRef();

    if (numTokens == 0)
      return null;
    else if (numTokens == 1) {
      try {
        boolean hasNext = buffer.incrementToken();
        assert hasNext == true;
        termAtt.fillBytesRef();
      } catch (IOException e) {
        // safe to ignore, because we know the number of tokens
      }
      return newTermQuery(new Term(field, BytesRef.deepCopyOf(bytes)));
    } else {
      if (severalTokensAtSamePosition || (!quoted)) {
        if (positionCount == 1 || (!quoted)) {
          // no phrase query:
          
          if (positionCount == 1) {
            // simple case: only one position, with synonyms
            BooleanQuery q = newBooleanQuery(true);
            for (int i = 0; i < numTokens; i++) {
              try {
                boolean hasNext = buffer.incrementToken();
                assert hasNext == true;
                termAtt.fillBytesRef();
              } catch (IOException e) {
                // safe to ignore, because we know the number of tokens
              }
              Query currentQuery = newTermQuery(
                  new Term(field, BytesRef.deepCopyOf(bytes)));
              q.add(currentQuery, BooleanClause.Occur.SHOULD);
            }
            return q;
          } else {
            // multiple positions
            BooleanQuery q = newBooleanQuery(false);
            Query currentQuery = null;
            for (int i = 0; i < numTokens; i++) {
              try {
                boolean hasNext = buffer.incrementToken();
                assert hasNext == true;
                termAtt.fillBytesRef();
              } catch (IOException e) {
                // safe to ignore, because we know the number of tokens
              }
              if (posIncrAtt != null && posIncrAtt.getPositionIncrement() == 0) {
                if (!(currentQuery instanceof BooleanQuery)) {
                  Query t = currentQuery;
                  currentQuery = newBooleanQuery(true);
                  ((BooleanQuery)currentQuery).add(t, BooleanClause.Occur.SHOULD);
                }
                ((BooleanQuery)currentQuery).add(newTermQuery(new Term(field, BytesRef.deepCopyOf(bytes))), BooleanClause.Occur.SHOULD);
              } else {
                if (currentQuery != null) {
                  q.add(currentQuery, operator);
                }
                currentQuery = newTermQuery(new Term(field, BytesRef.deepCopyOf(bytes)));
              }
            }
            q.add(currentQuery, operator);
            return q;
          }
        } else {
          // phrase query:
          MultiPhraseQuery mpq = newMultiPhraseQuery();
          mpq.setSlop(phraseSlop);
          List<Term> multiTerms = new ArrayList<>();
          int position = -1;
          for (int i = 0; i < numTokens; i++) {
            int positionIncrement = 1;
            try {
              boolean hasNext = buffer.incrementToken();
              assert hasNext == true;
              termAtt.fillBytesRef();
              if (posIncrAtt != null) {
                positionIncrement = posIncrAtt.getPositionIncrement();
              }
            } catch (IOException e) {
              // safe to ignore, because we know the number of tokens
            }

            if (positionIncrement > 0 && multiTerms.size() > 0) {
              if (enablePositionIncrements) {
                mpq.add(multiTerms.toArray(new Term[0]),position);
              } else {
                mpq.add(multiTerms.toArray(new Term[0]));
              }
              multiTerms.clear();
            }
            position += positionIncrement;
            multiTerms.add(new Term(field, BytesRef.deepCopyOf(bytes)));
          }
          if (enablePositionIncrements) {
            mpq.add(multiTerms.toArray(new Term[0]),position);
          } else {
            mpq.add(multiTerms.toArray(new Term[0]));
          }
          return mpq;
        }
      } else {
        PhraseQuery pq = newPhraseQuery();
        pq.setSlop(phraseSlop);
        int position = -1;

        for (int i = 0; i < numTokens; i++) {
          int positionIncrement = 1;

          try {
            boolean hasNext = buffer.incrementToken();
            assert hasNext == true;
            termAtt.fillBytesRef();
            if (posIncrAtt != null) {
              positionIncrement = posIncrAtt.getPositionIncrement();
            }
          } catch (IOException e) {
            // safe to ignore, because we know the number of tokens
          }

          if (enablePositionIncrements) {
            position += positionIncrement;
            pq.add(new Term(field, BytesRef.deepCopyOf(bytes)),position);
          } else {
            pq.add(new Term(field, BytesRef.deepCopyOf(bytes)));
          }
        }
        return pq;
      }
    }
  }
  
  protected BooleanQuery newBooleanQuery(boolean disableCoord) {
    return new BooleanQuery(disableCoord);
  }
  
  protected Query newTermQuery(Term term) {
    return new TermQuery(term);
  }
  
  protected PhraseQuery newPhraseQuery() {
    return new PhraseQuery();
  }
  
  protected MultiPhraseQuery newMultiPhraseQuery() {
    return new MultiPhraseQuery();
  }
}
