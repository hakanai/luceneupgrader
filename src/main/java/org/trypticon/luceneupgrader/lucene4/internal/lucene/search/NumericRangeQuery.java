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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.NumericTokenStream;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.DoubleField;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.FieldType.NumericType;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.FloatField;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.IntField;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.LongField;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FilteredTermsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.AttributeSource;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.NumericUtils;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ToStringUtils;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;


public final class NumericRangeQuery<T extends Number> extends MultiTermQuery {

  private NumericRangeQuery(final String field, final int precisionStep, final NumericType dataType,
    T min, T max, final boolean minInclusive, final boolean maxInclusive
  ) {
    super(field);
    if (precisionStep < 1)
      throw new IllegalArgumentException("precisionStep must be >=1");
    this.precisionStep = precisionStep;
    this.dataType = dataType;
    this.min = min;
    this.max = max;
    this.minInclusive = minInclusive;
    this.maxInclusive = maxInclusive;
  }
  
  public static NumericRangeQuery<Long> newLongRange(final String field, final int precisionStep,
    Long min, Long max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeQuery<>(field, precisionStep, NumericType.LONG, min, max, minInclusive, maxInclusive);
  }
  
  public static NumericRangeQuery<Long> newLongRange(final String field,
    Long min, Long max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeQuery<>(field, NumericUtils.PRECISION_STEP_DEFAULT, NumericType.LONG, min, max, minInclusive, maxInclusive);
  }
  
  public static NumericRangeQuery<Integer> newIntRange(final String field, final int precisionStep,
    Integer min, Integer max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeQuery<>(field, precisionStep, NumericType.INT, min, max, minInclusive, maxInclusive);
  }
  
  public static NumericRangeQuery<Integer> newIntRange(final String field,
    Integer min, Integer max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeQuery<>(field, NumericUtils.PRECISION_STEP_DEFAULT_32, NumericType.INT, min, max, minInclusive, maxInclusive);
  }
  
  public static NumericRangeQuery<Double> newDoubleRange(final String field, final int precisionStep,
    Double min, Double max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeQuery<>(field, precisionStep, NumericType.DOUBLE, min, max, minInclusive, maxInclusive);
  }
  
  public static NumericRangeQuery<Double> newDoubleRange(final String field,
    Double min, Double max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeQuery<>(field, NumericUtils.PRECISION_STEP_DEFAULT, NumericType.DOUBLE, min, max, minInclusive, maxInclusive);
  }
  
  public static NumericRangeQuery<Float> newFloatRange(final String field, final int precisionStep,
    Float min, Float max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeQuery<>(field, precisionStep, NumericType.FLOAT, min, max, minInclusive, maxInclusive);
  }
  
  public static NumericRangeQuery<Float> newFloatRange(final String field,
    Float min, Float max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeQuery<>(field, NumericUtils.PRECISION_STEP_DEFAULT_32, NumericType.FLOAT, min, max, minInclusive, maxInclusive);
  }

  @Override @SuppressWarnings("unchecked")
  protected TermsEnum getTermsEnum(final Terms terms, AttributeSource atts) throws IOException {
    // very strange: java.lang.Number itself is not Comparable, but all subclasses used here are
    if (min != null && max != null && ((Comparable<T>) min).compareTo(max) > 0) {
      return TermsEnum.EMPTY;
    }
    return new NumericRangeTermsEnum(terms.iterator(null));
  }

  public boolean includesMin() { return minInclusive; }
  
  public boolean includesMax() { return maxInclusive; }

  public T getMin() { return min; }

  public T getMax() { return max; }
  
  public int getPrecisionStep() { return precisionStep; }
  
  @Override
  public String toString(final String field) {
    final StringBuilder sb = new StringBuilder();
    if (!getField().equals(field)) sb.append(getField()).append(':');
    return sb.append(minInclusive ? '[' : '{')
      .append((min == null) ? "*" : min.toString())
      .append(" TO ")
      .append((max == null) ? "*" : max.toString())
      .append(maxInclusive ? ']' : '}')
      .append(ToStringUtils.boost(getBoost()))
      .toString();
  }

  @Override
  @SuppressWarnings({"unchecked","rawtypes"})
  public final boolean equals(final Object o) {
    if (o==this) return true;
    if (!super.equals(o))
      return false;
    if (o instanceof NumericRangeQuery) {
      final NumericRangeQuery q=(NumericRangeQuery)o;
      return (
        (q.min == null ? min == null : q.min.equals(min)) &&
        (q.max == null ? max == null : q.max.equals(max)) &&
        minInclusive == q.minInclusive &&
        maxInclusive == q.maxInclusive &&
        precisionStep == q.precisionStep
      );
    }
    return false;
  }

  @Override
  public final int hashCode() {
    int hash = super.hashCode();
    hash += precisionStep^0x64365465;
    if (min != null) hash += min.hashCode()^0x14fa55fb;
    if (max != null) hash += max.hashCode()^0x733fa5fe;
    return hash +
      (Boolean.valueOf(minInclusive).hashCode()^0x14fa55fb)+
      (Boolean.valueOf(maxInclusive).hashCode()^0x733fa5fe);
  }

  // members (package private, to be also fast accessible by NumericRangeTermEnum)
  final int precisionStep;
  final NumericType dataType;
  final T min, max;
  final boolean minInclusive,maxInclusive;

  // used to handle float/double infinity correcty
  static final long LONG_NEGATIVE_INFINITY =
    NumericUtils.doubleToSortableLong(Double.NEGATIVE_INFINITY);
  static final long LONG_POSITIVE_INFINITY =
    NumericUtils.doubleToSortableLong(Double.POSITIVE_INFINITY);
  static final int INT_NEGATIVE_INFINITY =
    NumericUtils.floatToSortableInt(Float.NEGATIVE_INFINITY);
  static final int INT_POSITIVE_INFINITY =
    NumericUtils.floatToSortableInt(Float.POSITIVE_INFINITY);

  private final class NumericRangeTermsEnum extends FilteredTermsEnum {

    private BytesRef currentLowerBound, currentUpperBound;

    private final LinkedList<BytesRef> rangeBounds = new LinkedList<>();
    private final Comparator<BytesRef> termComp;

    NumericRangeTermsEnum(final TermsEnum tenum) {
      super(tenum);
      switch (dataType) {
        case LONG:
        case DOUBLE: {
          // lower
          long minBound;
          if (dataType == NumericType.LONG) {
            minBound = (min == null) ? Long.MIN_VALUE : min.longValue();
          } else {
            assert dataType == NumericType.DOUBLE;
            minBound = (min == null) ? LONG_NEGATIVE_INFINITY
              : NumericUtils.doubleToSortableLong(min.doubleValue());
          }
          if (!minInclusive && min != null) {
            if (minBound == Long.MAX_VALUE) break;
            minBound++;
          }
          
          // upper
          long maxBound;
          if (dataType == NumericType.LONG) {
            maxBound = (max == null) ? Long.MAX_VALUE : max.longValue();
          } else {
            assert dataType == NumericType.DOUBLE;
            maxBound = (max == null) ? LONG_POSITIVE_INFINITY
              : NumericUtils.doubleToSortableLong(max.doubleValue());
          }
          if (!maxInclusive && max != null) {
            if (maxBound == Long.MIN_VALUE) break;
            maxBound--;
          }
          
          NumericUtils.splitLongRange(new NumericUtils.LongRangeBuilder() {
            @Override
            public final void addRange(BytesRef minPrefixCoded, BytesRef maxPrefixCoded) {
              rangeBounds.add(minPrefixCoded);
              rangeBounds.add(maxPrefixCoded);
            }
          }, precisionStep, minBound, maxBound);
          break;
        }
          
        case INT:
        case FLOAT: {
          // lower
          int minBound;
          if (dataType == NumericType.INT) {
            minBound = (min == null) ? Integer.MIN_VALUE : min.intValue();
          } else {
            assert dataType == NumericType.FLOAT;
            minBound = (min == null) ? INT_NEGATIVE_INFINITY
              : NumericUtils.floatToSortableInt(min.floatValue());
          }
          if (!minInclusive && min != null) {
            if (minBound == Integer.MAX_VALUE) break;
            minBound++;
          }
          
          // upper
          int maxBound;
          if (dataType == NumericType.INT) {
            maxBound = (max == null) ? Integer.MAX_VALUE : max.intValue();
          } else {
            assert dataType == NumericType.FLOAT;
            maxBound = (max == null) ? INT_POSITIVE_INFINITY
              : NumericUtils.floatToSortableInt(max.floatValue());
          }
          if (!maxInclusive && max != null) {
            if (maxBound == Integer.MIN_VALUE) break;
            maxBound--;
          }
          
          NumericUtils.splitIntRange(new NumericUtils.IntRangeBuilder() {
            @Override
            public final void addRange(BytesRef minPrefixCoded, BytesRef maxPrefixCoded) {
              rangeBounds.add(minPrefixCoded);
              rangeBounds.add(maxPrefixCoded);
            }
          }, precisionStep, minBound, maxBound);
          break;
        }
          
        default:
          // should never happen
          throw new IllegalArgumentException("Invalid NumericType");
      }

      termComp = getComparator();
    }
    
    private void nextRange() {
      assert rangeBounds.size() % 2 == 0;

      currentLowerBound = rangeBounds.removeFirst();
      assert currentUpperBound == null || termComp.compare(currentUpperBound, currentLowerBound) <= 0 :
        "The current upper bound must be <= the new lower bound";
      
      currentUpperBound = rangeBounds.removeFirst();
    }
    
    @Override
    protected final BytesRef nextSeekTerm(BytesRef term) {
      while (rangeBounds.size() >= 2) {
        nextRange();
        
        // if the new upper bound is before the term parameter, the sub-range is never a hit
        if (term != null && termComp.compare(term, currentUpperBound) > 0)
          continue;
        // never seek backwards, so use current term if lower bound is smaller
        return (term != null && termComp.compare(term, currentLowerBound) > 0) ?
          term : currentLowerBound;
      }
      
      // no more sub-range enums available
      assert rangeBounds.isEmpty();
      currentLowerBound = currentUpperBound = null;
      return null;
    }
    
    @Override
    protected final AcceptStatus accept(BytesRef term) {
      while (currentUpperBound == null || termComp.compare(term, currentUpperBound) > 0) {
        if (rangeBounds.isEmpty())
          return AcceptStatus.END;
        // peek next sub-range, only seek if the current term is smaller than next lower bound
        if (termComp.compare(term, rangeBounds.getFirst()) < 0)
          return AcceptStatus.NO_AND_SEEK;
        // step forward to next range without seeking, as next lower range bound is less or equal current term
        nextRange();
      }
      return AcceptStatus.YES;
    }

  }
  
}
