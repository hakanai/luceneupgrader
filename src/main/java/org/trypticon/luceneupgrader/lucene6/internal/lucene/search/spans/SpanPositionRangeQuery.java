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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.search.spans;


import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.spans.FilterSpans.AcceptStatus;

import java.io.IOException;


public class SpanPositionRangeQuery extends SpanPositionCheckQuery {
  protected int start;
  protected int end;

  public SpanPositionRangeQuery(SpanQuery match, int start, int end) {
    super(match);
    this.start = start;
    this.end = end;
  }

  @Override
  protected AcceptStatus acceptPosition(Spans spans) throws IOException {
    assert spans.startPosition() != spans.endPosition();
    AcceptStatus res = (spans.startPosition() >= end)
        ? AcceptStatus.NO_MORE_IN_CURRENT_DOC
        : (spans.startPosition() >= start && spans.endPosition() <= end)
        ? AcceptStatus.YES : AcceptStatus.NO;
    return res;
  }

  public int getStart() {
    return start;
  }

  public int getEnd() {
    return end;
  }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("spanPosRange(");
    buffer.append(match.toString(field));
    buffer.append(", ").append(start).append(", ");
    buffer.append(end);
    buffer.append(")");
    return buffer.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (! super.equals(o)) {
      return false;
    }
    SpanPositionRangeQuery other = (SpanPositionRangeQuery)o;
    return this.end == other.end && this.start == other.start;
  }

  @Override
  public int hashCode() {
    int h = super.hashCode() ^ end;
    h = (h * 127) ^ start;
    return h;
  }

}