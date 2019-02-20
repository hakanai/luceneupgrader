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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import java.io.IOException;
import java.text.Collator;
import java.util.Locale;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.FieldCache.DoubleParser;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.FieldCache.LongParser;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.FieldCache.ByteParser;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.FieldCache.FloatParser;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.FieldCache.IntParser;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.FieldCache.ShortParser;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.FieldCache.StringIndex;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Bits;

public abstract class FieldComparator<T> {

  public abstract int compare(int slot1, int slot2);

  public abstract void setBottom(final int slot);

  public abstract int compareBottom(int doc) throws IOException;

  public abstract void copy(int slot, int doc) throws IOException;

  public abstract void setNextReader(IndexReader reader, int docBase) throws IOException;


  public void setScorer(Scorer scorer) {
    // Empty implementation since most comparators don't need the score. This
    // can be overridden by those that need it.
  }
  
  public abstract T value(int slot);


  @SuppressWarnings("unchecked")
  public int compareValues(T first, T second) {
    if (first == null) {
      if (second == null) {
        return 0;
      } else {
        return -1;
      }
    } else if (second == null) {
      return 1;
    } else {
      return ((Comparable<T>) first).compareTo(second);
    }
  }

  public static abstract class NumericComparator<T extends Number> extends FieldComparator<T> {
    protected final T missingValue;
    protected final String field;
    protected Bits docsWithField;
    
    public NumericComparator(String field, T missingValue) {
      this.field = field;
      this.missingValue = missingValue;
    }
    
    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {
      if (missingValue != null) {
        docsWithField = FieldCache.DEFAULT.getDocsWithField(reader, field);
        // optimization to remove unneeded checks on the bit interface:
        if (docsWithField instanceof Bits.MatchAllBits) {
          docsWithField = null;
        }
      } else {
        docsWithField = null;
      }
    }
  }

  public static final class ByteComparator extends NumericComparator<Byte> {
    private final byte[] values;
    private final ByteParser parser;
    private byte[] currentReaderValues;
    private byte bottom;

    ByteComparator(int numHits, String field, FieldCache.Parser parser, Byte missingValue) {
      super(field, missingValue);
      values = new byte[numHits];
      this.parser = (ByteParser) parser;
    }

    @Override
    public int compare(int slot1, int slot2) {
      return values[slot1] - values[slot2];
    }

    @Override
    public int compareBottom(int doc) {
      byte v2 = currentReaderValues[doc];
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }
      return bottom - v2;
    }

    @Override
    public void copy(int slot, int doc) {
      byte v2 = currentReaderValues[doc];
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }
      values[slot] = v2;
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {
      // NOTE: must do this before calling super otherwise
      // we compute the docsWithField Bits twice!
      currentReaderValues = FieldCache.DEFAULT.getBytes(reader, field, parser, missingValue != null);
      super.setNextReader(reader, docBase);
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public Byte value(int slot) {
      return Byte.valueOf(values[slot]);
    }
  }

  public static final class DocComparator extends FieldComparator<Integer> {
    private final int[] docIDs;
    private int docBase;
    private int bottom;

    DocComparator(int numHits) {
      docIDs = new int[numHits];
    }

    @Override
    public int compare(int slot1, int slot2) {
      // No overflow risk because docIDs are non-negative
      return docIDs[slot1] - docIDs[slot2];
    }

    @Override
    public int compareBottom(int doc) {
      // No overflow risk because docIDs are non-negative
      return bottom - (docBase + doc);
    }

    @Override
    public void copy(int slot, int doc) {
      docIDs[slot] = docBase + doc;
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) {
      // TODO: can we "map" our docIDs to the current
      // reader? saves having to then subtract on every
      // compare call
      this.docBase = docBase;
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = docIDs[bottom];
    }

    @Override
    public Integer value(int slot) {
      return Integer.valueOf(docIDs[slot]);
    }
  }

  public static final class DoubleComparator extends NumericComparator<Double> {
    private final double[] values;
    private final DoubleParser parser;
    private double[] currentReaderValues;
    private double bottom;

    DoubleComparator(int numHits, String field, FieldCache.Parser parser, Double missingValue) {
      super(field, missingValue);
      values = new double[numHits];
      this.parser = (DoubleParser) parser;
    }

    @Override
    public int compare(int slot1, int slot2) {
      final double v1 = values[slot1];
      final double v2 = values[slot2];
      if (v1 > v2) {
        return 1;
      } else if (v1 < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    @Override
    public int compareBottom(int doc) {
      double v2 = currentReaderValues[doc];
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }
      if (bottom > v2) {
        return 1;
      } else if (bottom < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    @Override
    public void copy(int slot, int doc) {
      double v2 = currentReaderValues[doc];
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }
      values[slot] = v2;
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {
      // NOTE: must do this before calling super otherwise
      // we compute the docsWithField Bits twice!
      currentReaderValues = FieldCache.DEFAULT.getDoubles(reader, field, parser, missingValue != null);
      super.setNextReader(reader, docBase);
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public Double value(int slot) {
      return Double.valueOf(values[slot]);
    }
  }

  public static final class FloatComparator extends NumericComparator<Float> {
    private final float[] values;
    private final FloatParser parser;
    private float[] currentReaderValues;
    private float bottom;

    FloatComparator(int numHits, String field, FieldCache.Parser parser, Float missingValue) {
      super(field, missingValue);
      values = new float[numHits];
      this.parser = (FloatParser) parser;
    }

    @Override
    public int compare(int slot1, int slot2) {
      // TODO: are there sneaky non-branch ways to compute
      // sign of float?
      final float v1 = values[slot1];
      final float v2 = values[slot2];
      if (v1 > v2) {
        return 1;
      } else if (v1 < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    @Override
    public int compareBottom(int doc) {
      // TODO: are there sneaky non-branch ways to compute
      // sign of float?
      float v2 = currentReaderValues[doc];
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }
      if (bottom > v2) {
        return 1;
      } else if (bottom < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    @Override
    public void copy(int slot, int doc) {
      float v2 = currentReaderValues[doc];
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }
      values[slot] = v2;
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {
      // NOTE: must do this before calling super otherwise
      // we compute the docsWithField Bits twice!
      currentReaderValues = FieldCache.DEFAULT.getFloats(reader, field, parser, missingValue != null);
      super.setNextReader(reader, docBase);
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public Float value(int slot) {
      return Float.valueOf(values[slot]);
    }
  }

  public static final class IntComparator extends NumericComparator<Integer> {
    private final int[] values;
    private final IntParser parser;
    private int[] currentReaderValues;
    private int bottom;                           // Value of bottom of queue

    IntComparator(int numHits, String field, FieldCache.Parser parser, Integer missingValue) {
      super(field, missingValue);
      values = new int[numHits];
      this.parser = (IntParser) parser;
    }

    @Override
    public int compare(int slot1, int slot2) {
      // TODO: there are sneaky non-branch ways to compute
      // -1/+1/0 sign
      // Cannot return values[slot1] - values[slot2] because that
      // may overflow
      final int v1 = values[slot1];
      final int v2 = values[slot2];
      if (v1 > v2) {
        return 1;
      } else if (v1 < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    @Override
    public int compareBottom(int doc) {
      // TODO: there are sneaky non-branch ways to compute
      // -1/+1/0 sign
      // Cannot return bottom - values[slot2] because that
      // may overflow
      int v2 = currentReaderValues[doc];
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }
      if (bottom > v2) {
        return 1;
      } else if (bottom < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    @Override
    public void copy(int slot, int doc) {
      int v2 = currentReaderValues[doc];
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }
      values[slot] = v2;
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {
      // NOTE: must do this before calling super otherwise
      // we compute the docsWithField Bits twice!
      currentReaderValues = FieldCache.DEFAULT.getInts(reader, field, parser, missingValue != null);
      super.setNextReader(reader, docBase);
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public Integer value(int slot) {
      return Integer.valueOf(values[slot]);
    }
  }

  public static final class LongComparator extends NumericComparator<Long> {
    private final long[] values;
    private final LongParser parser;
    private long[] currentReaderValues;
    private long bottom;

    LongComparator(int numHits, String field, FieldCache.Parser parser, Long missingValue) {
      super(field, missingValue);
      values = new long[numHits];
      this.parser = (LongParser) parser;
    }

    @Override
    public int compare(int slot1, int slot2) {
      // TODO: there are sneaky non-branch ways to compute
      // -1/+1/0 sign
      final long v1 = values[slot1];
      final long v2 = values[slot2];
      if (v1 > v2) {
        return 1;
      } else if (v1 < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    @Override
    public int compareBottom(int doc) {
      // TODO: there are sneaky non-branch ways to compute
      // -1/+1/0 sign
      long v2 = currentReaderValues[doc];
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }
      if (bottom > v2) {
        return 1;
      } else if (bottom < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    @Override
    public void copy(int slot, int doc) {
      long v2 = currentReaderValues[doc];
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }
      values[slot] = v2;
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {
      // NOTE: must do this before calling super otherwise
      // we compute the docsWithField Bits twice!
      currentReaderValues = FieldCache.DEFAULT.getLongs(reader, field, parser, missingValue != null);
      super.setNextReader(reader, docBase);
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public Long value(int slot) {
      return Long.valueOf(values[slot]);
    }
  }


  public static final class RelevanceComparator extends FieldComparator<Float> {
    private final float[] scores;
    private float bottom;
    private Scorer scorer;
    
    RelevanceComparator(int numHits) {
      scores = new float[numHits];
    }

    @Override
    public int compare(int slot1, int slot2) {
      final float score1 = scores[slot1];
      final float score2 = scores[slot2];
      return score1 > score2 ? -1 : (score1 < score2 ? 1 : 0);
    }

    @Override
    public int compareBottom(int doc) throws IOException {
      float score = scorer.score();
      return bottom > score ? -1 : (bottom < score ? 1 : 0);
    }

    @Override
    public void copy(int slot, int doc) throws IOException {
      scores[slot] = scorer.score();
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) {
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = scores[bottom];
    }

    @Override
    public void setScorer(Scorer scorer) {
      // wrap with a ScoreCachingWrappingScorer so that successive calls to
      // score() will not incur score computation over and
      // over again.
      if (!(scorer instanceof ScoreCachingWrappingScorer)) {
        this.scorer = new ScoreCachingWrappingScorer(scorer);
      } else {
        this.scorer = scorer;
      }
    }
    
    @Override
    public Float value(int slot) {
      return Float.valueOf(scores[slot]);
    }

    // Override because we sort reverse of natural Float order:
    @Override
    public int compareValues(Float first, Float second) {
      // Reversed intentionally because relevance by default
      // sorts descending:
      return second.compareTo(first);
    }
  }

  public static final class ShortComparator extends NumericComparator<Short> {
    private final short[] values;
    private final ShortParser parser;
    private short[] currentReaderValues;
    private short bottom;

    ShortComparator(int numHits, String field, FieldCache.Parser parser, Short missingValue) {
      super(field, missingValue);
      values = new short[numHits];
      this.parser = (ShortParser) parser;
    }

    @Override
    public int compare(int slot1, int slot2) {
      return values[slot1] - values[slot2];
    }

    @Override
    public int compareBottom(int doc) {
      short v2 = currentReaderValues[doc];
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }
      return bottom - v2;
    }

    @Override
    public void copy(int slot, int doc) {
      short v2 = currentReaderValues[doc];
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }
      values[slot] = v2;
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {
      // NOTE: must do this before calling super otherwise
      // we compute the docsWithField Bits twice!
      currentReaderValues = FieldCache.DEFAULT.getShorts(reader, field, parser, missingValue != null);
      super.setNextReader(reader, docBase);
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public Short value(int slot) {
      return Short.valueOf(values[slot]);
    }
  }

  public static final class StringComparatorLocale extends FieldComparator<String> {

    private final String[] values;
    private String[] currentReaderValues;
    private final String field;
    final Collator collator;
    private String bottom;

    StringComparatorLocale(int numHits, String field, Locale locale) {
      values = new String[numHits];
      this.field = field;
      collator = Collator.getInstance(locale);
    }

    @Override
    public int compare(int slot1, int slot2) {
      final String val1 = values[slot1];
      final String val2 = values[slot2];
      if (val1 == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      }
      return collator.compare(val1, val2);
    }

    @Override
    public int compareBottom(int doc) {
      final String val2 = currentReaderValues[doc];
      if (bottom == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      }
      return collator.compare(bottom, val2);
    }

    @Override
    public void copy(int slot, int doc) {
      values[slot] = currentReaderValues[doc];
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {
      currentReaderValues = FieldCache.DEFAULT.getStrings(reader, field);
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public String value(int slot) {
      return values[slot];
    }

    @Override
    public int compareValues(String val1, String val2) {
      if (val1 == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      }
      return collator.compare(val1, val2);
    }
  }


  public static final class StringOrdValComparator extends FieldComparator<String> {

    private final int[] ords;
    private final String[] values;
    private final int[] readerGen;

    private int currentReaderGen = -1;
    private String[] lookup;
    private int[] order;
    private final String field;

    private int bottomSlot = -1;
    private int bottomOrd;
    private boolean bottomSameReader;
    private String bottomValue;

    public StringOrdValComparator(int numHits, String field, int sortPos, boolean reversed) {
      ords = new int[numHits];
      values = new String[numHits];
      readerGen = new int[numHits];
      this.field = field;
    }

    @Override
    public int compare(int slot1, int slot2) {
      if (readerGen[slot1] == readerGen[slot2]) {
        return ords[slot1] - ords[slot2];
      }

      final String val1 = values[slot1];
      final String val2 = values[slot2];
      if (val1 == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      }
      return val1.compareTo(val2);
    }

    @Override
    public int compareBottom(int doc) {
      assert bottomSlot != -1;
      final int docOrd = this.order[doc];
      if (bottomSameReader) {
        // ord is precisely comparable, even in the equal case
        return bottomOrd - docOrd;
      } else if (bottomOrd >= docOrd) {
        // the equals case always means bottom is > doc
        // (because we set bottomOrd to the lower bound in
        // setBottom):
        return 1;
      } else {
        return -1;
      }
    }

    @Override
    public void copy(int slot, int doc) {
      final int ord = order[doc];
      ords[slot] = ord;
      assert ord >= 0;
      values[slot] = lookup[ord];
      readerGen[slot] = currentReaderGen;
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {
      StringIndex currentReaderValues = FieldCache.DEFAULT.getStringIndex(reader, field);
      currentReaderGen++;
      order = currentReaderValues.order;
      lookup = currentReaderValues.lookup;
      assert lookup.length > 0;
      if (bottomSlot != -1) {
        setBottom(bottomSlot);
      }
    }
    
    @Override
    public void setBottom(final int bottom) {
      bottomSlot = bottom;

      bottomValue = values[bottomSlot];
      if (currentReaderGen == readerGen[bottomSlot]) {
        bottomOrd = ords[bottomSlot];
        bottomSameReader = true;
      } else {
        if (bottomValue == null) {
          ords[bottomSlot] = 0;
          bottomOrd = 0;
          bottomSameReader = true;
          readerGen[bottomSlot] = currentReaderGen;
        } else {
          final int index = binarySearch(lookup, bottomValue);
          if (index < 0) {
            bottomOrd = -index - 2;
            bottomSameReader = false;
          } else {
            bottomOrd = index;
            // exact value match
            bottomSameReader = true;
            readerGen[bottomSlot] = currentReaderGen;            
            ords[bottomSlot] = bottomOrd;
          }
        }
      }
    }

    @Override
    public String value(int slot) {
      return values[slot];
    }
    
    @Override
    public int compareValues(String val1, String val2) {
      if (val1 == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      }
      return val1.compareTo(val2);
    }

    public String[] getValues() {
      return values;
    }

    public int getBottomSlot() {
      return bottomSlot;
    }

    public String getField() {
      return field;
    }
  }


  public static final class StringValComparator extends FieldComparator<String> {

    private String[] values;
    private String[] currentReaderValues;
    private final String field;
    private String bottom;

    StringValComparator(int numHits, String field) {
      values = new String[numHits];
      this.field = field;
    }

    @Override
    public int compare(int slot1, int slot2) {
      final String val1 = values[slot1];
      final String val2 = values[slot2];
      if (val1 == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      }

      return val1.compareTo(val2);
    }

    @Override
    public int compareBottom(int doc) {
      final String val2 = currentReaderValues[doc];
      if (bottom == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      }
      return bottom.compareTo(val2);
    }

    @Override
    public void copy(int slot, int doc) {
      values[slot] = currentReaderValues[doc];
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {
      currentReaderValues = FieldCache.DEFAULT.getStrings(reader, field);
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public String value(int slot) {
      return values[slot];
    }

    @Override
    public int compareValues(String val1, String val2) {
      if (val1 == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      } else {
        return val1.compareTo(val2);
      }
    }
  }

  final protected static int binarySearch(String[] a, String key) {
    return binarySearch(a, key, 0, a.length-1);
  }

  final protected static int binarySearch(String[] a, String key, int low, int high) {

    while (low <= high) {
      int mid = (low + high) >>> 1;
      String midVal = a[mid];
      int cmp;
      if (midVal != null) {
        cmp = midVal.compareTo(key);
      } else {
        cmp = -1;
      }

      if (cmp < 0)
        low = mid + 1;
      else if (cmp > 0)
        high = mid - 1;
      else
        return mid;
    }
    return -(low + 1);
  }
}
