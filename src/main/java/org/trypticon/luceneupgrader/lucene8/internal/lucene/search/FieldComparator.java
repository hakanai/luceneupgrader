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

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.BinaryDocValues;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.SortedDocValues;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.BytesRefBuilder;

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

  public void setSingleSort() {
  }

  public void disableSkipping() {
  }

  public static final class RelevanceComparator extends FieldComparator<Float> implements LeafFieldComparator {
    private final float[] scores;
    private float bottom;
    private Scorable scorer;
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
    public void setScorer(Scorable scorer) {
      // wrap with a ScoreCachingWrappingScorer so that successive calls to
      // score() will not incur score computation over and
      // over again.
      this.scorer = ScoreCachingWrappingScorer.wrap(scorer);
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

    private int getOrdForDoc(int doc) throws IOException {
      if (termsIndex.advanceExact(doc)) {
        return termsIndex.ordValue();
      } else {
        return -1;
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
    public int compareBottom(int doc) throws IOException {
      assert bottomSlot != -1;
      int docOrd = getOrdForDoc(doc);
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
    public void copy(int slot, int doc) throws IOException {
      int ord = getOrdForDoc(doc);
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
    public void setBottom(final int bottom) throws IOException {
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
    public int compareTop(int doc) throws IOException {

      int ord = getOrdForDoc(doc);
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
    public void setScorer(Scorable scorer) {}
  }
  
  public static class TermValComparator extends FieldComparator<BytesRef> implements LeafFieldComparator {
    
    private final BytesRef[] values;
    private final BytesRefBuilder[] tempBRs;
    private BinaryDocValues docTerms;
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

    private BytesRef getValueForDoc(int doc) throws IOException {
      if (docTerms.advanceExact(doc)) {
        return docTerms.binaryValue();
      } else {
        return null;
      }
    }

    @Override
    public int compare(int slot1, int slot2) {
      final BytesRef val1 = values[slot1];
      final BytesRef val2 = values[slot2];
      return compareValues(val1, val2);
    }

    @Override
    public int compareBottom(int doc) throws IOException {
      final BytesRef comparableBytes = getValueForDoc(doc);
      return compareValues(bottom, comparableBytes);
    }

    @Override
    public void copy(int slot, int doc) throws IOException {
      final BytesRef comparableBytes = getValueForDoc(doc);
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

    @Override
    public LeafFieldComparator getLeafComparator(LeafReaderContext context) throws IOException {
      docTerms = getBinaryDocValues(context, field);
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
    public int compareTop(int doc) throws IOException {
      return compareValues(topValue, getValueForDoc(doc));
    }

    @Override
    public void setScorer(Scorable scorer) {}
  }
}
