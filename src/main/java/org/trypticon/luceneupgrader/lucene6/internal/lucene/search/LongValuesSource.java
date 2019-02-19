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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.search;

import java.io.IOException;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Bits;

public abstract class LongValuesSource {

  public abstract LongValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException;

  public abstract boolean needsScores();

  public SortField getSortField(boolean reverse) {
    return new LongValuesSortField(this, reverse);
  }

  public static LongValuesSource fromLongField(String field) {
    return new FieldValuesSource(field);
  }

  public static LongValuesSource fromIntField(String field) {
    return fromLongField(field);
  }

  public static LongValuesSource constant(long value) {
    return new LongValuesSource() {
      @Override
      public LongValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
        return new LongValues() {
          @Override
          public long longValue() throws IOException {
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
    };
  }

  private static class FieldValuesSource extends LongValuesSource {

    final String field;

    private FieldValuesSource(String field) {
      this.field = field;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      FieldValuesSource that = (FieldValuesSource) o;
      return Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
      return Objects.hash(field);
    }

    @Override
    public LongValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
      final NumericDocValues values = DocValues.getNumeric(ctx.reader(), field);
      final Bits matchingBits = DocValues.getDocsWithField(ctx.reader(), field);
      return toLongValues(values, matchingBits);
    }

    @Override
    public boolean needsScores() {
      return false;
    }
  }

  private static class LongValuesSortField extends SortField {

    final LongValuesSource producer;

    public LongValuesSortField(LongValuesSource producer, boolean reverse) {
      super(producer.toString(), new LongValuesComparatorSource(producer), reverse);
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

  }

  private static class LongValuesHolder {
    LongValues values;
  }

  private static class LongValuesComparatorSource extends FieldComparatorSource {
    private final LongValuesSource producer;

    public LongValuesComparatorSource(LongValuesSource producer) {
      this.producer = producer;
    }

    @Override
    public FieldComparator<Long> newComparator(String fieldname, int numHits,
                                                 int sortPos, boolean reversed) {
      return new FieldComparator.LongComparator(numHits, fieldname, 0L){

        LeafReaderContext ctx;
        LongValuesHolder holder = new LongValuesHolder();

        @Override
        protected NumericDocValues getNumericDocValues(LeafReaderContext context, String field) throws IOException {
          ctx = context;
          return asNumericDocValues(holder, missingValue);
        }

        @Override
        public void setScorer(Scorer scorer) throws IOException {
          holder.values = producer.getValues(ctx, DoubleValuesSource.fromScorer(scorer));
        }
      };
    }
  }

  private static LongValues toLongValues(NumericDocValues in, Bits matchingBits) {
    return new LongValues() {

      int current = -1;

      @Override
      public long longValue() throws IOException {
        return in.get(current);
      }

      @Override
      public boolean advanceExact(int target) throws IOException {
        current = target;
        return matchingBits.get(target);
      }

    };
  }

  private static NumericDocValues asNumericDocValues(LongValuesHolder in, Long missingValue) {
    return new NumericDocValues() {
      @Override
      public long get(int docID) {
        try {
          if (in.values.advanceExact(docID))
            return in.values.longValue();
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        return missingValue;
      }

    };
  }

}
