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

package org.trypticon.luceneupgrader.lucene9.internal.lucene.document;

import java.io.IOException;
import java.util.Arrays;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.Component2D;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.XYEncodingUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.XYGeometry;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SortedNumericDocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.ConstantScoreScorer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.ConstantScoreWeight;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.QueryVisitor;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.ScoreMode;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Scorer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.TwoPhaseIterator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Weight;

/** XYGeometry query for {@link XYDocValuesField}. */
public class XYDocValuesPointInGeometryQuery extends Query {

  private final String field;
  private final XYGeometry[] geometries;

  XYDocValuesPointInGeometryQuery(String field, XYGeometry... geometries) {
    if (field == null) {
      throw new IllegalArgumentException("field must not be null");
    }
    if (geometries == null) {
      throw new IllegalArgumentException("geometries must not be null");
    }
    if (geometries.length == 0) {
      throw new IllegalArgumentException("geometries must not be empty");
    }
    for (int i = 0; i < geometries.length; i++) {
      if (geometries[i] == null) {
        throw new IllegalArgumentException("geometries[" + i + "] must not be null");
      }
    }
    this.field = field;
    this.geometries = geometries.clone();
  }

  @Override
  public String toString(String field) {
    StringBuilder sb = new StringBuilder();
    if (!this.field.equals(field)) {
      sb.append(this.field);
      sb.append(':');
    }
    sb.append("geometries(").append(Arrays.toString(geometries));
    return sb.append(")").toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (sameClassAs(obj) == false) {
      return false;
    }
    XYDocValuesPointInGeometryQuery other = (XYDocValuesPointInGeometryQuery) obj;
    return field.equals(other.field) && Arrays.equals(geometries, other.geometries);
  }

  @Override
  public int hashCode() {
    int h = classHash();
    h = 31 * h + field.hashCode();
    h = 31 * h + Arrays.hashCode(geometries);
    return h;
  }

  @Override
  public void visit(QueryVisitor visitor) {
    if (visitor.acceptField(field)) {
      visitor.visitLeaf(this);
    }
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
      throws IOException {

    return new ConstantScoreWeight(this, boost) {

      final Component2D component2D = XYGeometry.create(geometries);

      @Override
      public Scorer scorer(LeafReaderContext context) throws IOException {
        final SortedNumericDocValues values = context.reader().getSortedNumericDocValues(field);
        if (values == null) {
          return null;
        }

        final TwoPhaseIterator iterator =
            new TwoPhaseIterator(values) {

              @Override
              public boolean matches() throws IOException {
                for (int i = 0, count = values.docValueCount(); i < count; ++i) {
                  final long value = values.nextValue();
                  final double x = XYEncodingUtils.decode((int) (value >>> 32));
                  final double y = XYEncodingUtils.decode((int) (value & 0xFFFFFFFF));
                  if (component2D.contains(x, y)) {
                    return true;
                  }
                }
                return false;
              }

              @Override
              public float matchCost() {
                return 1000f; // TODO: what should it be?
              }
            };
        return new ConstantScoreScorer(this, boost, scoreMode, iterator);
      }

      @Override
      public boolean isCacheable(LeafReaderContext ctx) {
        return DocValues.isCacheable(ctx, field);
      }
    };
  }
}
