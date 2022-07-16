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

package org.trypticon.luceneupgrader.lucene8.internal.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.LeafReaderContext;

public class NamedMatches implements Matches {

  private final Matches in;
  private final String name;

  public NamedMatches(String name, Matches in) {
    this.in = Objects.requireNonNull(in);
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Override
  public MatchesIterator getMatches(String field) throws IOException {
    return in.getMatches(field);
  }

  @Override
  public Collection<Matches> getSubMatches() {
    return Collections.singleton(in);
  }

  @Override
  public Iterator<String> iterator() {
    return in.iterator();
  }

  public static Query wrapQuery(String name, Query in) {
    return new NamedQuery(name, in);
  }

  public static List<NamedMatches> findNamedMatches(Matches matches) {
    List<NamedMatches> nm = new ArrayList<>();
    List<Matches> toProcess = new LinkedList<>();
    toProcess.add(matches);
    while (toProcess.size() > 0) {
      matches = toProcess.remove(0);
      if (matches instanceof NamedMatches) {
        nm.add((NamedMatches) matches);
      }
      toProcess.addAll(matches.getSubMatches());
    }
    return nm;
  }

  private static class NamedQuery extends Query {

    private final String name;
    private final Query in;

    private NamedQuery(String name, Query in) {
      this.name = name;
      this.in = in;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
      Weight w = in.createWeight(searcher, scoreMode, boost);
      return new FilterWeight(w) {
        @Override
        public Matches matches(LeafReaderContext context, int doc) throws IOException {
          Matches m = in.matches(context, doc);
          if (m == null) {
            return null;
          }
          return new NamedMatches(name, m);
        }
      };
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
      Query rewritten = in.rewrite(reader);
      if (rewritten != in) {
        return new NamedQuery(name, rewritten);
      }
      return this;
    }

    @Override
    public String toString(String field) {
      return "NamedQuery(" + name + "," + in.toString(field) + ")";
    }

    @Override
    public void visit(QueryVisitor visitor) {
      QueryVisitor sub = visitor.getSubVisitor(BooleanClause.Occur.MUST, this);
      in.visit(sub);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      NamedQuery that = (NamedQuery) o;
      return Objects.equals(name, that.name) &&
          Objects.equals(in, that.in);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, in);
    }
  }
}
