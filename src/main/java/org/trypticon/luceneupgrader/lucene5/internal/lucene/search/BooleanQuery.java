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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.BooleanClause.Occur;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.similarities.Similarity;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ToStringUtils;

public class BooleanQuery extends Query implements Iterable<BooleanClause> {

  private static int maxClauseCount = 1024;


  public static class TooManyClauses extends RuntimeException {
    public TooManyClauses() {
      super("maxClauseCount is set to " + maxClauseCount);
    }
  }


  public static int getMaxClauseCount() { return maxClauseCount; }


  public static void setMaxClauseCount(int maxClauseCount) {
    if (maxClauseCount < 1) {
      throw new IllegalArgumentException("maxClauseCount must be >= 1");
    }
    BooleanQuery.maxClauseCount = maxClauseCount;
  }

  public static class Builder {

    private boolean disableCoord;
    private int minimumNumberShouldMatch;
    private final List<BooleanClause> clauses = new ArrayList<>();

    public Builder() {}

    public Builder setDisableCoord(boolean disableCoord) {
      this.disableCoord = disableCoord;
      return this;
    }

    public Builder setMinimumNumberShouldMatch(int min) {
      this.minimumNumberShouldMatch = min;
      return this;
    }

    public Builder add(BooleanClause clause) {
      add(clause.getQuery(), clause.getOccur());
      return this;
    }

    public Builder add(Query query, Occur occur) {
      if (clauses.size() >= maxClauseCount) {
        throw new TooManyClauses();
      }
      clauses.add(new BooleanClause(query, occur));
      return this;
    }

    public BooleanQuery build() {
      return new BooleanQuery(disableCoord, minimumNumberShouldMatch, clauses.toArray(new BooleanClause[0]));
    }

  }

  private final boolean mutable;
  private final boolean disableCoord;
  private int minimumNumberShouldMatch;
  private List<BooleanClause> clauses;                    // used for toString() and getClauses()
  private final Map<Occur, Collection<Query>> clauseSets; // used for equals/hashcode

  private BooleanQuery(boolean disableCoord, int minimumNumberShouldMatch,
      BooleanClause[] clauses) {
    this.disableCoord = disableCoord;
    this.minimumNumberShouldMatch = minimumNumberShouldMatch;
    this.clauses = Collections.unmodifiableList(Arrays.asList(clauses));
    this.mutable = false;
    clauseSets = new EnumMap<>(Occur.class);
    // duplicates matter for SHOULD and MUST
    clauseSets.put(Occur.SHOULD, new Multiset<Query>());
    clauseSets.put(Occur.MUST, new Multiset<Query>());
    // but not for FILTER and MUST_NOT
    clauseSets.put(Occur.FILTER, new HashSet<Query>());
    clauseSets.put(Occur.MUST_NOT, new HashSet<Query>());
    for (BooleanClause clause : clauses) {
      clauseSets.get(clause.getOccur()).add(clause.getQuery());
    }
  }

  public boolean isCoordDisabled() {
    return disableCoord;
  }

  public int getMinimumNumberShouldMatch() {
    return minimumNumberShouldMatch;
  }

  public List<BooleanClause> clauses() {
    return clauses;
  }

  Collection<Query> getClauses(Occur occur) {
    if (mutable) {
      List<Query> queries = new ArrayList<>();
      for (BooleanClause clause : clauses) {
        if (clause.getOccur() == occur) {
          queries.add(clause.getQuery());
        }
      }
      return Collections.unmodifiableList(queries);
    } else {
      return clauseSets.get(occur);
    }
  }


  @Override
  public final Iterator<BooleanClause> iterator() {
    return clauses.iterator();
  }

  private BooleanQuery rewriteNoScoring() {
    BooleanQuery.Builder newQuery = new BooleanQuery.Builder();
    // ignore disableCoord, which only matters for scores
    newQuery.setMinimumNumberShouldMatch(getMinimumNumberShouldMatch());
    for (BooleanClause clause : clauses) {
      if (clause.getOccur() == Occur.MUST) {
        newQuery.add(clause.getQuery(), Occur.FILTER);
      } else {
        newQuery.add(clause);
      }
    }
    return newQuery.build();
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    BooleanQuery query = this;
    if (needsScores == false) {
      query = rewriteNoScoring();
    }
    return new BooleanWeight(query, searcher, needsScores, disableCoord);
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    if (getBoost() != 1f) {
      return super.rewrite(reader);
    }
    // optimize 1-clause queries
    if (clauses.size() == 1) {
      BooleanClause c = clauses.get(0);
      Query query = c.getQuery();
      if (minimumNumberShouldMatch == 1 && c.getOccur() == Occur.SHOULD) {
        return query;
      } else if (minimumNumberShouldMatch == 0) {
        switch (c.getOccur()) {
          case SHOULD:
          case MUST:
            return query;
          case FILTER:
            // no scoring clauses, so return a score of 0
            return new BoostQuery(new ConstantScoreQuery(query), 0);
          case MUST_NOT:
            // no positive clauses
            return new MatchNoDocsQuery();
          default:
            throw new AssertionError();
        }
      }
    }

    // recursively rewrite
    {
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      builder.setDisableCoord(isCoordDisabled());
      builder.setMinimumNumberShouldMatch(getMinimumNumberShouldMatch());
      boolean actuallyRewritten = false;
      for (BooleanClause clause : this) {
        Query query = clause.getQuery();
        Query rewritten = query.rewrite(reader);
        if (rewritten != query) {
          actuallyRewritten = true;
        }
        builder.add(rewritten, clause.getOccur());
      }
      if (mutable || actuallyRewritten) {
        return builder.build();
      }
    }

    assert mutable == false;
    // remove duplicate FILTER and MUST_NOT clauses
    {
      int clauseCount = 0;
      for (Collection<Query> queries : clauseSets.values()) {
        clauseCount += queries.size();
      }
      if (clauseCount != clauses.size()) {
        // since clauseSets implicitly deduplicates FILTER and MUST_NOT
        // clauses, this means there were duplicates
        BooleanQuery.Builder rewritten = new BooleanQuery.Builder();
        rewritten.setDisableCoord(disableCoord);
        rewritten.setMinimumNumberShouldMatch(minimumNumberShouldMatch);
        for (Map.Entry<Occur, Collection<Query>> entry : clauseSets.entrySet()) {
          final Occur occur = entry.getKey();
          for (Query query : entry.getValue()) {
            rewritten.add(query, occur);
          }
        }
        return rewritten.build();
      }
    }

    // remove FILTER clauses that are also MUST clauses
    // or that match all documents
    if (clauseSets.get(Occur.MUST).size() > 0 && clauseSets.get(Occur.FILTER).size() > 0) {
      final Set<Query> filters = new HashSet<Query>(clauseSets.get(Occur.FILTER));
      boolean modified = filters.remove(new MatchAllDocsQuery());
      modified |= filters.removeAll(clauseSets.get(Occur.MUST));
      if (modified) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.setDisableCoord(isCoordDisabled());
        builder.setMinimumNumberShouldMatch(getMinimumNumberShouldMatch());
        for (BooleanClause clause : clauses) {
          if (clause.getOccur() != Occur.FILTER) {
            builder.add(clause);
          }
        }
        for (Query filter : filters) {
          builder.add(filter, Occur.FILTER);
        }
        return builder.build();
      }
    }

    // Rewrite queries whose single scoring clause is a MUST clause on a
    // MatchAllDocsQuery to a ConstantScoreQuery
    {
      final Collection<Query> musts = clauseSets.get(Occur.MUST);
      final Collection<Query> filters = clauseSets.get(Occur.FILTER);
      if (musts.size() == 1
          && filters.size() > 0) {
        Query must = musts.iterator().next();
        float boost = 1f;
        if (must instanceof BoostQuery) {
          BoostQuery boostQuery = (BoostQuery) must;
          must = boostQuery.getQuery();
          boost = boostQuery.getBoost();
        }
        if (must.getClass() == MatchAllDocsQuery.class) {
          // our single scoring clause matches everything: rewrite to a CSQ on the filter
          // ignore SHOULD clause for now
          BooleanQuery.Builder builder = new BooleanQuery.Builder();
          for (BooleanClause clause : clauses) {
            switch (clause.getOccur()) {
              case FILTER:
              case MUST_NOT:
                builder.add(clause);
                break;
              default:
                // ignore
                break;
            }
          }
          Query rewritten = builder.build();
          rewritten = new ConstantScoreQuery(rewritten);
          if (boost != 1f) {
            rewritten = new BoostQuery(rewritten, boost);
          }

          // now add back the SHOULD clauses
          builder = new BooleanQuery.Builder()
            .setDisableCoord(isCoordDisabled())
            .setMinimumNumberShouldMatch(getMinimumNumberShouldMatch())
            .add(rewritten, Occur.MUST);
          for (Query query : clauseSets.get(Occur.SHOULD)) {
            builder.add(query, Occur.SHOULD);
          }
          rewritten = builder.build();
          return rewritten;
        }
      }
    }

    return super.rewrite(reader);
  }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    boolean needParens = getBoost() != 1.0 || getMinimumNumberShouldMatch() > 0;
    if (needParens) {
      buffer.append("(");
    }

    int i = 0;
    for (BooleanClause c : this) {
      buffer.append(c.getOccur().toString());

      Query subQuery = c.getQuery();
      if (subQuery instanceof BooleanQuery) {  // wrap sub-bools in parens
        buffer.append("(");
        buffer.append(subQuery.toString(field));
        buffer.append(")");
      } else {
        buffer.append(subQuery.toString(field));
      }

      if (i != clauses.size() - 1) {
        buffer.append(" ");
      }
      i += 1;
    }

    if (needParens) {
      buffer.append(")");
    }

    if (getMinimumNumberShouldMatch()>0) {
      buffer.append('~');
      buffer.append(getMinimumNumberShouldMatch());
    }

    buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (super.equals(o) == false) {
      return false;
    }
    BooleanQuery that = (BooleanQuery)o;
    if (this.getMinimumNumberShouldMatch() != that.getMinimumNumberShouldMatch()) {
      return false;
    }
    if (this.disableCoord != that.disableCoord) {
      return false;
    }
    if (this.mutable != that.mutable) {
      return false;
    }
    if (this.mutable) {
      // depends on order
      return clauses.equals(that.clauses);
    } else {
      // does not depend on order
      return clauseSets.equals(that.clauseSets);
    }
  }

  private int computeHashCode() {
    int hashCode =Objects.hash(disableCoord, minimumNumberShouldMatch, clauseSets);
    if (hashCode == 0) {
      hashCode = 1;
    }
    return hashCode;
  }

  // cached hash code is only ok for immutable queries
  private int hashCode;

  @Override
  public int hashCode() {
    if (mutable) {
      assert clauseSets == null;
      return 31 * super.hashCode() + Objects.hash(disableCoord, minimumNumberShouldMatch, clauses);
    }

    if (hashCode == 0) {
      // no need for synchronization, in the worst case we would just compute the hash several times
      hashCode = computeHashCode();
      assert hashCode != 0;
    }
    assert hashCode == computeHashCode();
    return 31 * super.hashCode() + hashCode;
  }

  // Backward compatibility for pre-5.3 BooleanQuery APIs


  @Deprecated
  public BooleanClause[] getClauses() {
    return clauses.toArray(new BooleanClause[clauses.size()]);
  }

  @Override
  public BooleanQuery clone() {
    BooleanQuery clone = (BooleanQuery) super.clone();
    clone.clauses = new ArrayList<>(clauses);
    return clone;
  }


  @Deprecated
  public BooleanQuery() {
    this(false);
  }


  @Deprecated
  public BooleanQuery(boolean disableCoord) {
    this.clauses = new ArrayList<>();
    this.disableCoord = disableCoord;
    this.minimumNumberShouldMatch = 0;
    this.mutable = true;
    this.clauseSets = null;
  }

  private void ensureMutable(String method) {
    if (mutable == false) {
      throw new IllegalStateException("This BooleanQuery has been created with the new "
          + "BooleanQuery.Builder API. It must not be modified afterwards. The "
          + method + " method only exists for backward compatibility");
    }
  }

  @Deprecated
  public void setMinimumNumberShouldMatch(int min) {
    ensureMutable("setMinimumNumberShouldMatch");
    this.minimumNumberShouldMatch = min;
  }


  @Deprecated
  public void add(Query query, BooleanClause.Occur occur) {
    add(new BooleanClause(query, occur));
  }


  @Deprecated
  public void add(BooleanClause clause) {
    ensureMutable("add");
    Objects.requireNonNull(clause, "BooleanClause must not be null");
    if (clauses.size() >= maxClauseCount) {
      throw new TooManyClauses();
    }

    clauses.add(clause);
  }
}
