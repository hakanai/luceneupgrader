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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search.spans;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public abstract class SpanPositionCheckQuery extends SpanQuery implements Cloneable {
  protected SpanQuery match;


  public SpanPositionCheckQuery(SpanQuery match) {
    this.match = match;
  }


  public SpanQuery getMatch() { return match; }



  @Override
  public String getField() { return match.getField(); }



  @Override
  public void extractTerms(Set<Term> terms) {
	    match.extractTerms(terms);
  }


  protected static enum AcceptStatus { YES, NO, NO_AND_ADVANCE };
  
  protected abstract AcceptStatus acceptPosition(Spans spans) throws IOException;

  @Override
  public Spans getSpans(final IndexReader reader) throws IOException {
    return new PositionCheckSpan(reader);
  }


  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    SpanPositionCheckQuery clone = null;

    SpanQuery rewritten = (SpanQuery) match.rewrite(reader);
    if (rewritten != match) {
      clone = (SpanPositionCheckQuery) this.clone();
      clone.match = rewritten;
    }

    if (clone != null) {
      return clone;                        // some clauses rewrote
    } else {
      return this;                         // no clauses rewrote
    }
  }

  protected class PositionCheckSpan extends Spans {
    private Spans spans;

    public PositionCheckSpan(IndexReader reader) throws IOException {
      spans = match.getSpans(reader);
    }

    @Override
    public boolean next() throws IOException {
      if (!spans.next())
        return false;
      
      return doNext();
    }

    @Override
    public boolean skipTo(int target) throws IOException {
      if (!spans.skipTo(target))
        return false;

      return doNext();
    }
    
    protected boolean doNext() throws IOException {
      for (;;) {
        switch(acceptPosition(this)) {
          case YES: return true;
          case NO: 
            if (!spans.next()) 
              return false;
            break;
          case NO_AND_ADVANCE: 
            if (!spans.skipTo(spans.doc()+1)) 
              return false;
            break;
        }
      }
    }

    @Override
    public int doc() { return spans.doc(); }

    @Override
    public int start() { return spans.start(); }

    @Override
    public int end() { return spans.end(); }
    // TODO: Remove warning after API has been finalized

    @Override
    public Collection<byte[]> getPayload() throws IOException {
      ArrayList<byte[]> result = null;
      if (spans.isPayloadAvailable()) {
        result = new ArrayList<byte[]>(spans.getPayload());
      }
      return result;//TODO: any way to avoid the new construction?
    }
    // TODO: Remove warning after API has been finalized

    @Override
    public boolean isPayloadAvailable() {
      return spans.isPayloadAvailable();
    }

    @Override
    public String toString() {
        return "spans(" + SpanPositionCheckQuery.this.toString() + ")";
      }

  }
}