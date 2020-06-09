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

package org.trypticon.luceneupgrader.lucene7.internal.lucene.search;

import java.io.IOException;
import java.util.Objects;
import java.util.function.DoubleToLongFunction;
import java.util.function.LongToDoubleFunction;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.NumericDocValues;

public abstract class DoubleValuesSource implements SegmentCacheable {

  public abstract DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException;

  public abstract boolean needsScores();

  public Explanation explain(LeafReaderContext ctx, int docId, Explanation scoreExplanation) throws IOException {
    DoubleValues dv = getValues(ctx, DoubleValuesSource.constant(scoreExplanation.getValue()).getValues(ctx, null));
    if (dv.advanceExact(docId))
      return Explanation.match((float) dv.doubleValue(), this.toString());
    return Explanation.noMatch(this.toString());
  }

  public abstract DoubleValuesSource rewrite(IndexSearcher reader) throws IOException;

  public SortField getSortField(boolean reverse) {
    return new DoubleValuesSortField(this, reverse);
  }

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract String toString();

  public final LongValuesSource toLongValuesSource() {
    return new LongDoubleValuesSource(this);
  }

  private static class LongDoubleValuesSource extends LongValuesSource {

    private final DoubleValuesSource inner;

    private LongDoubleValuesSource(DoubleValuesSource inner) {
      this.inner = inner;
    }

    @Override
    public LongValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
      DoubleValues in = inner.getValues(ctx, scores);
      return new LongValues() {
        @Override
        public long longValue() throws IOException {
          return (long) in.doubleValue();
        }

        @Override
        public boolean advanceExact(int doc) throws IOException {
          return in.advanceExact(doc);
        }
      };
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      return inner.isCacheable(ctx);
    }

    @Override
    public boolean needsScores() {
      return inner.needsScores();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      LongDoubleValuesSource that = (LongDoubleValuesSource) o;
      return Objects.equals(inner, that.inner);
    }

    @Override
    public int hashCode() {
      return Objects.hash(inner);
    }

    @Override
    public String toString() {
      return "long(" + inner.toString() + ")";
    }

    @Override
    public LongValuesSource rewrite(IndexSearcher searcher) throws IOException {
      return inner.rewrite(searcher).toLongValuesSource();
    }

  }

  public static DoubleValuesSource fromField(String field, LongToDoubleFunction decoder) {
    return new FieldValuesSource(field, decoder);
  }

  public static DoubleValuesSource fromDoubleField(String field) {
    return fromField(field, Double::longBitsToDouble);
  }

  public static DoubleValuesSource fromFloatField(String field) {
    return fromField(field, (v) -> (double)Float.intBitsToFloat((int)v));
  }

  public static DoubleValuesSource fromLongField(String field) {
    return fromField(field, (v) -> (double) v);
  }

  public static DoubleValuesSource fromIntField(String field) {
    return fromLongField(field);
  }

  public static final DoubleValuesSource SCORES = new DoubleValuesSource() {
    @Override
    public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
      assert scores != null;
      return scores;
    }

    @Override
    public boolean needsScores() {
      return true;
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      return false;
    }

    @Override
    public Explanation explain(LeafReaderContext ctx, int docId, Explanation scoreExplanation) {
      return scoreExplanation;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this;
    }

    @Override
    public String toString() {
      return "scores";
    }

    @Override
    public DoubleValuesSource rewrite(IndexSearcher searcher) {
      return this;
    }
  };

  public static DoubleValuesSource constant(double value) {
    return new ConstantValuesSource(value);
  }

  private static class ConstantValuesSource extends DoubleValuesSource {

    private final double value;

    private ConstantValuesSource(double value) {
      this.value = value;
    }

    @Override
    public DoubleValuesSource rewrite(IndexSearcher searcher) {
      return this;
    }


    @Override
    public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
      return new DoubleValues() {
        @Override
        public double doubleValue() throws IOException {
          return value;
        }

        @Override
        public boolean advanceExact(int doc) throws IOException {
          return true;
        }
      };
    }

    @Override
    public boolean needsScores() {
      return false;
    }


    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      return true;
    }

    @Override
    public Explanation explain(LeafReaderContext ctx, int docId, Explanation scoreExplanation) {
      return Explanation.match((float) value, "constant(" + value + ")");
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ConstantValuesSource that = (ConstantValuesSource) o;
      return Double.compare(that.value, value) == 0;
    }

    @Override
    public String toString() {
      return "constant(" + value + ")";
    }

  }

  public static DoubleValues fromScorer(Scorer scorer) {
    return new DoubleValues() {
      @Override
      public double doubleValue() throws IOException {
        return scorer.score();
      }

      @Override
      public boolean advanceExact(int doc) throws IOException {
        assert scorer.docID() == doc;
        return true;
      }
    };
  }

  private static class FieldValuesSource extends DoubleValuesSource {

    final String field;
    final LongToDoubleFunction decoder;

    private FieldValuesSource(String field, LongToDoubleFunction decoder) {
      this.field = field;
      this.decoder = decoder;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      FieldValuesSource that = (FieldValuesSource) o;
      return Objects.equals(field, that.field) &&
          Objects.equals(decoder, that.decoder);
    }

    @Override
    public String toString() {
      return "double(" + field + ")";
    }

    @Override
    public int hashCode() {
      return Objects.hash(field, decoder);
    }

    @Override
    public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
      final NumericDocValues values = DocValues.getNumeric(ctx.reader(), field);
      return new DoubleValues() {
        @Override
        public double doubleValue() throws IOException {
          return decoder.applyAsDouble(values.longValue());
        }

        @Override
        public boolean advanceExact(int target) throws IOException {
          return values.advanceExact(target);
        }
      };
    }

    @Override
    public boolean needsScores() {
      return false;
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      return DocValues.isCacheable(ctx, field);
    }

    @Override
    public Explanation explain(LeafReaderContext ctx, int docId, Explanation scoreExplanation) throws IOException {
      DoubleValues values = getValues(ctx, null);
      if (values.advanceExact(docId))
        return Explanation.match((float) values.doubleValue(), this.toString());
      else
        return Explanation.noMatch(this.toString());
    }

    public DoubleValuesSource rewrite(IndexSearcher searcher) throws IOException {
      return this;
    }

  }

  private static class DoubleValuesSortField extends SortField {

    final DoubleValuesSource producer;

    DoubleValuesSortField(DoubleValuesSource producer, boolean reverse) {
      super(producer.toString(), new DoubleValuesComparatorSource(producer), reverse);
      this.producer = producer;
    }

    @Override
    public boolean needsScores() {
      return producer.needsScores();
    }

    @Override
    public String toString() {
      StringBuilder buffer = new StringBuilder("<");
      buffer.append(getField()).append(">");
      if (reverse)
        buffer.append("!");
      return buffer.toString();
    }

    @Override
    public SortField rewrite(IndexSearcher searcher) throws IOException {
      return new DoubleValuesSortField(producer.rewrite(searcher), reverse);
    }
  }

  private static class DoubleValuesHolder {
    DoubleValues values;
  }

  private static class DoubleValuesComparatorSource extends FieldComparatorSource {
    private final DoubleValuesSource producer;

    DoubleValuesComparatorSource(DoubleValuesSource producer) {
      this.producer = producer;
    }

    @Override
    public FieldComparator<Double> newComparator(String fieldname, int numHits,
                                               int sortPos, boolean reversed) {
      return new FieldComparator.DoubleComparator(numHits, fieldname, 0.0){

        LeafReaderContext ctx;
        DoubleValuesHolder holder = new DoubleValuesHolder();

        @Override
        protected NumericDocValues getNumericDocValues(LeafReaderContext context, String field) throws IOException {
          ctx = context;
          return asNumericDocValues(holder, Double::doubleToLongBits);
        }

        @Override
        public void setScorer(Scorer scorer) throws IOException {
          holder.values = producer.getValues(ctx, fromScorer(scorer));
        }
      };
    }
  }

  private static NumericDocValues asNumericDocValues(DoubleValuesHolder in, DoubleToLongFunction converter) {
    return new NumericDocValues() {
      @Override
      public long longValue() throws IOException {
        return converter.applyAsLong(in.values.doubleValue());
      }

      @Override
      public boolean advanceExact(int target) throws IOException {
        return in.values.advanceExact(target);
      }

      @Override
      public int docID() {
        throw new UnsupportedOperationException();
      }

      @Override
      public int nextDoc() throws IOException {
        throw new UnsupportedOperationException();
      }

      @Override
      public int advance(int target) throws IOException {
        throw new UnsupportedOperationException();
      }

      @Override
      public long cost() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public static DoubleValuesSource fromQuery(Query query) {
    return new QueryDoubleValuesSource(query);
  }

  private static class QueryDoubleValuesSource extends DoubleValuesSource {

    private final Query query;

    private QueryDoubleValuesSource(Query query) {
      this.query = query;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      QueryDoubleValuesSource that = (QueryDoubleValuesSource) o;
      return Objects.equals(query, that.query);
    }

    @Override
    public int hashCode() {
      return Objects.hash(query);
    }

    @Override
    public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
      throw new UnsupportedOperationException("This DoubleValuesSource must be rewritten");

    }

    @Override
    public boolean needsScores() {
      return false;
    }

    @Override
    public DoubleValuesSource rewrite(IndexSearcher searcher) throws IOException {
      return new WeightDoubleValuesSource(searcher.rewrite(query).createWeight(searcher, true, 1f));
    }

    @Override
    public String toString() {
      return "score(" + query.toString() + ")";
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      return false;
    }
  }

  private static class WeightDoubleValuesSource extends DoubleValuesSource {

    private final Weight weight;

    private WeightDoubleValuesSource(Weight weight) {
      this.weight = weight;
    }

    @Override
    public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
      Scorer scorer = weight.scorer(ctx);
      if (scorer == null)
        return DoubleValues.EMPTY;
      DocIdSetIterator it = scorer.iterator();
      return new DoubleValues() {
        @Override
        public double doubleValue() throws IOException {
          return scorer.score();
        }

        @Override
        public boolean advanceExact(int doc) throws IOException {
          if (it.docID() > doc)
            return false;
          return it.docID() == doc || it.advance(doc) == doc;
        }
      };
    }

    @Override
    public Explanation explain(LeafReaderContext ctx, int docId, Explanation scoreExplanation) throws IOException {
      return weight.explain(ctx, docId);
    }

    @Override
    public boolean needsScores() {
      return false;
    }

    @Override
    public DoubleValuesSource rewrite(IndexSearcher searcher) throws IOException {
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      WeightDoubleValuesSource that = (WeightDoubleValuesSource) o;
      return Objects.equals(weight, that.weight);
    }

    @Override
    public int hashCode() {
      return Objects.hash(weight);
    }

    @Override
    public String toString() {
      return "score(" + weight.parentQuery.toString() + ")";
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      return false;
    }
  }

}
