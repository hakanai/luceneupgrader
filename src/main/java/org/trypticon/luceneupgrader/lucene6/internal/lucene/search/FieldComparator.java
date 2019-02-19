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

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.BinaryDocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SortedDocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRefBuilder;

public abstract class FieldComparator<T> {

  public abstract int compare(int slot1, int slot2);

  public abstract void setTopValue(T value);

  public abstract T value(int slot);

  public abstract LeafFieldComparator getLeafComparator(LeafReaderContext context) throws IOException;


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


  public static abstract class NumericComparator<T extends Number> extends SimpleFieldComparator<T> {
    protected final T missingValue;
    protected final String field;
    protected Bits docsWithField;
    protected NumericDocValues currentReaderValues;
    
    public NumericComparator(String field, T missingValue) {
      this.field = field;
      this.missingValue = missingValue;
    }

    @Override
    protected void doSetNextReader(LeafReaderContext context) throws IOException {
      currentReaderValues = getNumericDocValues(context, field);
      if (missingValue != null) {
        docsWithField = getDocsWithValue(context, field);
        // optimization to remove unneeded checks on the bit interface:
        if (docsWithField instanceof Bits.MatchAllBits) {
          docsWithField = null;
        }
      } else {
        docsWithField = null;
      }
    }
    
    protected NumericDocValues getNumericDocValues(LeafReaderContext context, String field) throws IOException {
      return DocValues.getNumeric(context.reader(), field);
    }

    protected Bits getDocsWithValue(LeafReaderContext context, String field) throws IOException {
      return DocValues.getDocsWithField(context.reader(), field);
    }
  }

  public static class DoubleComparator extends NumericComparator<Double> {
    private final double[] values;
    private double bottom;
    private double topValue;

    public DoubleComparator(int numHits, String field, Double missingValue) {
      super(field, missingValue);
      values = new double[numHits];
    }

    @Override
    public int compare(int slot1, int slot2) {
      return Double.compare(values[slot1], values[slot2]);
    }

    @Override
    public int compareBottom(int doc) {
      double v2 = Double.longBitsToDouble(currentReaderValues.get(doc));
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }

      return Double.compare(bottom, v2);
    }

    @Override
    public void copy(int slot, int doc) {
      double v2 = Double.longBitsToDouble(currentReaderValues.get(doc));
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }

      values[slot] = v2;
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public void setTopValue(Double value) {
      topValue = value;
    }

    @Override
    public Double value(int slot) {
      return Double.valueOf(values[slot]);
    }

    @Override
    public int compareTop(int doc) {
      double docValue = Double.longBitsToDouble(currentReaderValues.get(doc));
      // Test for docValue == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && docValue == 0 && !docsWithField.get(doc)) {
        docValue = missingValue;
      }
      return Double.compare(topValue, docValue);
    }
  }

  public static class FloatComparator extends NumericComparator<Float> {
    private final float[] values;
    private float bottom;
    private float topValue;

    public FloatComparator(int numHits, String field, Float missingValue) {
      super(field, missingValue);
      values = new float[numHits];
    }
    
    @Override
    public int compare(int slot1, int slot2) {
      return Float.compare(values[slot1], values[slot2]);
    }

    @Override
    public int compareBottom(int doc) {
      // TODO: are there sneaky non-branch ways to compute sign of float?
      float v2 = Float.intBitsToFloat((int)currentReaderValues.get(doc));
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }

      return Float.compare(bottom, v2);
    }

    @Override
    public void copy(int slot, int doc) {
      float v2 =  Float.intBitsToFloat((int)currentReaderValues.get(doc));
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }

      values[slot] = v2;
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public void setTopValue(Float value) {
      topValue = value;
    }

    @Override
    public Float value(int slot) {
      return Float.valueOf(values[slot]);
    }

    @Override
    public int compareTop(int doc) {
      float docValue = Float.intBitsToFloat((int)currentReaderValues.get(doc));
      // Test for docValue == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && docValue == 0 && !docsWithField.get(doc)) {
        docValue = missingValue;
      }
      return Float.compare(topValue, docValue);
    }
  }

  public static class IntComparator extends NumericComparator<Integer> {
    private final int[] values;
    private int bottom;                           // Value of bottom of queue
    private int topValue;

    public IntComparator(int numHits, String field, Integer missingValue) {
      super(field, missingValue);
      values = new int[numHits];
    }
        
    @Override
    public int compare(int slot1, int slot2) {
      return Integer.compare(values[slot1], values[slot2]);
    }

    @Override
    public int compareBottom(int doc) {
      int v2 = (int) currentReaderValues.get(doc);
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }

      return Integer.compare(bottom, v2);
    }

    @Override
    public void copy(int slot, int doc) {
      int v2 = (int) currentReaderValues.get(doc);
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }

      values[slot] = v2;
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public void setTopValue(Integer value) {
      topValue = value;
    }

    @Override
    public Integer value(int slot) {
      return Integer.valueOf(values[slot]);
    }

    @Override
    public int compareTop(int doc) {
      int docValue = (int) currentReaderValues.get(doc);
      // Test for docValue == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && docValue == 0 && !docsWithField.get(doc)) {
        docValue = missingValue;
      }
      return Integer.compare(topValue, docValue);
    }
  }

  public static class LongComparator extends NumericComparator<Long> {
    private final long[] values;
    private long bottom;
    private long topValue;

    public LongComparator(int numHits, String field, Long missingValue) {
      super(field, missingValue);
      values = new long[numHits];
    }

    @Override
    public int compare(int slot1, int slot2) {
      return Long.compare(values[slot1], values[slot2]);
    }

    @Override
    public int compareBottom(int doc) {
      // TODO: there are sneaky non-branch ways to compute
      // -1/+1/0 sign
      long v2 = currentReaderValues.get(doc);
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }

      return Long.compare(bottom, v2);
    }

    @Override
    public void copy(int slot, int doc) {
      long v2 = currentReaderValues.get(doc);
      // Test for v2 == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && v2 == 0 && !docsWithField.get(doc)) {
        v2 = missingValue;
      }

      values[slot] = v2;
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public void setTopValue(Long value) {
      topValue = value;
    }

    @Override
    public Long value(int slot) {
      return Long.valueOf(values[slot]);
    }

    @Override
    public int compareTop(int doc) {
      long docValue = currentReaderValues.get(doc);
      // Test for docValue == 0 to save Bits.get method call for
      // the common case (doc has value and value is non-zero):
      if (docsWithField != null && docValue == 0 && !docsWithField.get(doc)) {
        docValue = missingValue;
      }
      return Long.compare(topValue, docValue);
    }
  }


  public static final class RelevanceComparator extends FieldComparator<Float> implements LeafFieldComparator {
    private final float[] scores;
    private float bottom;
    private Scorer scorer;
    private float topValue;

    public RelevanceComparator(int numHits) {
      scores = new float[numHits];
    }

    @Override
    public int compare(int slot1, int slot2) {
      return Float.compare(scores[slot2], scores[slot1]);
    }

    @Override
    public int compareBottom(int doc) throws IOException {
      float score = scorer.score();
      assert !Float.isNaN(score);
      return Float.compare(score, bottom);
    }

    @Override
    public void copy(int slot, int doc) throws IOException {
      scores[slot] = scorer.score();
      assert !Float.isNaN(scores[slot]);
    }

    @Override
    public LeafFieldComparator getLeafComparator(LeafReaderContext context) {
      return this;
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = scores[bottom];
    }

    @Override
    public void setTopValue(Float value) {
      topValue = value;
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

    @Override
    public int compareTop(int doc) throws IOException {
      float docValue = scorer.score();
      assert !Float.isNaN(docValue);
      return Float.compare(docValue, topValue);
    }
  }

  public static final class DocComparator extends FieldComparator<Integer> implements LeafFieldComparator {
    private final int[] docIDs;
    private int docBase;
    private int bottom;
    private int topValue;

    public DocComparator(int numHits) {
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
    public LeafFieldComparator getLeafComparator(LeafReaderContext context) {
      // TODO: can we "map" our docIDs to the current
      // reader? saves having to then subtract on every
      // compare call
      this.docBase = context.docBase;
      return this;
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = docIDs[bottom];
    }

    @Override
    public void setTopValue(Integer value) {
      topValue = value;
    }

    @Override
    public Integer value(int slot) {
      return Integer.valueOf(docIDs[slot]);
    }

    @Override
    public int compareTop(int doc) {
      int docValue = docBase + doc;
      return Integer.compare(topValue, docValue);
    }

    @Override
    public void setScorer(Scorer scorer) {}
  }
  

  public static class TermOrdValComparator extends FieldComparator<BytesRef> implements LeafFieldComparator {
    /* Ords for each slot.
       @lucene.internal */
    final int[] ords;

    /* Values for each slot.
       @lucene.internal */
    final BytesRef[] values;
    private final BytesRefBuilder[] tempBRs;

    /* Which reader last copied a value into the slot. When
       we compare two slots, we just compare-by-ord if the
       readerGen is the same; else we must compare the
       values (slower).
       @lucene.internal */
    final int[] readerGen;

    /* Gen of current reader we are on.
       @lucene.internal */
    int currentReaderGen = -1;

    /* Current reader's doc ord/values.
       @lucene.internal */
    SortedDocValues termsIndex;

    private final String field;

    /* Bottom slot, or -1 if queue isn't full yet
       @lucene.internal */
    int bottomSlot = -1;

    /* Bottom ord (same as ords[bottomSlot] once bottomSlot
       is set).  Cached for faster compares.
       @lucene.internal */
    int bottomOrd;

    /* True if current bottom slot matches the current
       reader.
       @lucene.internal */
    boolean bottomSameReader;

    /* Bottom value (same as values[bottomSlot] once
       bottomSlot is set).  Cached for faster compares.
      @lucene.internal */
    BytesRef bottomValue;

    BytesRef topValue;
    boolean topSameReader;
    int topOrd;

    final int missingSortCmp;
    
    final int missingOrd;

    public TermOrdValComparator(int numHits, String field) {
      this(numHits, field, false);
    }


    public TermOrdValComparator(int numHits, String field, boolean sortMissingLast) {
      ords = new int[numHits];
      values = new BytesRef[numHits];
      tempBRs = new BytesRefBuilder[numHits];
      readerGen = new int[numHits];
      this.field = field;
      if (sortMissingLast) {
        missingSortCmp = 1;
        missingOrd = Integer.MAX_VALUE;
      } else {
        missingSortCmp = -1;
        missingOrd = -1;
      }
    }

    @Override
    public int compare(int slot1, int slot2) {
      if (readerGen[slot1] == readerGen[slot2]) {
        return ords[slot1] - ords[slot2];
      }

      final BytesRef val1 = values[slot1];
      final BytesRef val2 = values[slot2];
      if (val1 == null) {
        if (val2 == null) {
          return 0;
        }
        return missingSortCmp;
      } else if (val2 == null) {
        return -missingSortCmp;
      }
      return val1.compareTo(val2);
    }

    @Override
    public int compareBottom(int doc) {
      assert bottomSlot != -1;
      int docOrd = termsIndex.getOrd(doc);
      if (docOrd == -1) {
        docOrd = missingOrd;
      }
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
      int ord = termsIndex.getOrd(doc);
      if (ord == -1) {
        ord = missingOrd;
        values[slot] = null;
      } else {
        assert ord >= 0;
        if (tempBRs[slot] == null) {
          tempBRs[slot] = new BytesRefBuilder();
        }
        tempBRs[slot].copyBytes(termsIndex.lookupOrd(ord));
        values[slot] = tempBRs[slot].get();
      }
      ords[slot] = ord;
      readerGen[slot] = currentReaderGen;
    }
    
    protected SortedDocValues getSortedDocValues(LeafReaderContext context, String field) throws IOException {
      return DocValues.getSorted(context.reader(), field);
    }
    
    @Override
    public LeafFieldComparator getLeafComparator(LeafReaderContext context) throws IOException {
      termsIndex = getSortedDocValues(context, field);
      currentReaderGen++;

      if (topValue != null) {
        // Recompute topOrd/SameReader
        int ord = termsIndex.lookupTerm(topValue);
        if (ord >= 0) {
          topSameReader = true;
          topOrd = ord;
        } else {
          topSameReader = false;
          topOrd = -ord-2;
        }
      } else {
        topOrd = missingOrd;
        topSameReader = true;
      }
      //System.out.println("  getLeafComparator topOrd=" + topOrd + " topSameReader=" + topSameReader);

      if (bottomSlot != -1) {
        // Recompute bottomOrd/SameReader
        setBottom(bottomSlot);
      }

      return this;
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
          // missingOrd is null for all segments
          assert ords[bottomSlot] == missingOrd;
          bottomOrd = missingOrd;
          bottomSameReader = true;
          readerGen[bottomSlot] = currentReaderGen;
        } else {
          final int ord = termsIndex.lookupTerm(bottomValue);
          if (ord < 0) {
            bottomOrd = -ord - 2;
            bottomSameReader = false;
          } else {
            bottomOrd = ord;
            // exact value match
            bottomSameReader = true;
            readerGen[bottomSlot] = currentReaderGen;            
            ords[bottomSlot] = bottomOrd;
          }
        }
      }
    }

    @Override
    public void setTopValue(BytesRef value) {
      // null is fine: it means the last doc of the prior
      // search was missing this value
      topValue = value;
      //System.out.println("setTopValue " + topValue);
    }

    @Override
    public BytesRef value(int slot) {
      return values[slot];
    }

    @Override
    public int compareTop(int doc) {

      int ord = termsIndex.getOrd(doc);
      if (ord == -1) {
        ord = missingOrd;
      }

      if (topSameReader) {
        // ord is precisely comparable, even in the equal
        // case
        //System.out.println("compareTop doc=" + doc + " ord=" + ord + " ret=" + (topOrd-ord));
        return topOrd - ord;
      } else if (ord <= topOrd) {
        // the equals case always means doc is < value
        // (because we set lastOrd to the lower bound)
        return 1;
      } else {
        return -1;
      }
    }

    @Override
    public int compareValues(BytesRef val1, BytesRef val2) {
      if (val1 == null) {
        if (val2 == null) {
          return 0;
        }
        return missingSortCmp;
      } else if (val2 == null) {
        return -missingSortCmp;
      }
      return val1.compareTo(val2);
    }

    @Override
    public void setScorer(Scorer scorer) {}
  }
  

  public static class TermValComparator extends FieldComparator<BytesRef> implements LeafFieldComparator {
    
    private final BytesRef[] values;
    private final BytesRefBuilder[] tempBRs;
    private BinaryDocValues docTerms;
    private Bits docsWithField;
    private final String field;
    private BytesRef bottom;
    private BytesRef topValue;
    private final int missingSortCmp;

    public TermValComparator(int numHits, String field, boolean sortMissingLast) {
      values = new BytesRef[numHits];
      tempBRs = new BytesRefBuilder[numHits];
      this.field = field;
      missingSortCmp = sortMissingLast ? 1 : -1;
    }

    @Override
    public int compare(int slot1, int slot2) {
      final BytesRef val1 = values[slot1];
      final BytesRef val2 = values[slot2];
      return compareValues(val1, val2);
    }

    @Override
    public int compareBottom(int doc) {
      final BytesRef comparableBytes = getComparableBytes(doc, docTerms.get(doc));
      return compareValues(bottom, comparableBytes);
    }

    @Override
    public void copy(int slot, int doc) {
      final BytesRef comparableBytes = getComparableBytes(doc, docTerms.get(doc));
      if (comparableBytes == null) {
        values[slot] = null;
      } else {
        if (tempBRs[slot] == null) {
          tempBRs[slot] = new BytesRefBuilder();
        }
        tempBRs[slot].copyBytes(comparableBytes);
        values[slot] = tempBRs[slot].get();
      }
    }

    protected BinaryDocValues getBinaryDocValues(LeafReaderContext context, String field) throws IOException {
      return DocValues.getBinary(context.reader(), field);
    }

    protected Bits getDocsWithField(LeafReaderContext context, String field) throws IOException {
      return DocValues.getDocsWithField(context.reader(), field);
    }


    protected boolean isNull(int doc, BytesRef term) {
      return docsWithField != null && docsWithField.get(doc) == false;
    }

    @Override
    public LeafFieldComparator getLeafComparator(LeafReaderContext context) throws IOException {
      docTerms = getBinaryDocValues(context, field);
      docsWithField = getDocsWithField(context, field);
      if (docsWithField instanceof Bits.MatchAllBits) {
        docsWithField = null;
      }
      return this;
    }
    
    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public void setTopValue(BytesRef value) {
      // null is fine: it means the last doc of the prior
      // search was missing this value
      topValue = value;
    }

    @Override
    public BytesRef value(int slot) {
      return values[slot];
    }

    @Override
    public int compareValues(BytesRef val1, BytesRef val2) {
      // missing always sorts first:
      if (val1 == null) {
        if (val2 == null) {
          return 0;
        }
        return missingSortCmp;
      } else if (val2 == null) {
        return -missingSortCmp;
      }
      return val1.compareTo(val2);
    }

    @Override
    public int compareTop(int doc) {
      final BytesRef comparableBytes = getComparableBytes(doc, docTerms.get(doc));
      return compareValues(topValue, comparableBytes);
    }

    private BytesRef getComparableBytes(int doc, BytesRef term) {
      if (term.length == 0 && isNull(doc, term)) {
        return null;
      }
      return term;
    }

    @Override
    public void setScorer(Scorer scorer) {}
  }
}
