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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.util;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.analysis.NumericTokenStream;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.document.DoubleField; // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.document.FloatField; // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.document.IntField; // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.document.LongField; // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FilterLeafReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FilteredTermsEnum;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.NumericRangeQuery; // for javadocs

public final class NumericUtils {

  private NumericUtils() {} // no instance!
  
  public static final int PRECISION_STEP_DEFAULT = 16;
  
  public static final int PRECISION_STEP_DEFAULT_32 = 8;
  
  public static final byte SHIFT_START_LONG = 0x20;

  public static final int BUF_SIZE_LONG = 63/7 + 2;

  public static final byte SHIFT_START_INT  = 0x60;

  public static final int BUF_SIZE_INT = 31/7 + 2;

  @Deprecated
  public static void longToPrefixCodedBytes(final long val, final int shift, final BytesRefBuilder bytes) {
    longToPrefixCoded(val, shift, bytes);
  }

  @Deprecated
  public static void intToPrefixCodedBytes(final int val, final int shift, final BytesRefBuilder bytes) {
    intToPrefixCoded(val, shift, bytes);
  }

  public static void longToPrefixCoded(final long val, final int shift, final BytesRefBuilder bytes) {
    // ensure shift is 0..63
    if ((shift & ~0x3f) != 0) {
      throw new IllegalArgumentException("Illegal shift value, must be 0..63; got shift=" + shift);
    }
    int nChars = (((63-shift)*37)>>8) + 1;    // i/7 is the same as (i*37)>>8 for i in 0..63
    bytes.setLength(nChars+1);   // one extra for the byte that contains the shift info
    bytes.grow(BUF_SIZE_LONG);
    bytes.setByteAt(0, (byte)(SHIFT_START_LONG + shift));
    long sortableBits = val ^ 0x8000000000000000L;
    sortableBits >>>= shift;
    while (nChars > 0) {
      // Store 7 bits per byte for compatibility
      // with UTF-8 encoding of terms
      bytes.setByteAt(nChars--, (byte)(sortableBits & 0x7f));
      sortableBits >>>= 7;
    }
  }


  public static void intToPrefixCoded(final int val, final int shift, final BytesRefBuilder bytes) {
    // ensure shift is 0..31
    if ((shift & ~0x1f) != 0) {
      throw new IllegalArgumentException("Illegal shift value, must be 0..31; got shift=" + shift);
    }
    int nChars = (((31-shift)*37)>>8) + 1;    // i/7 is the same as (i*37)>>8 for i in 0..63
    bytes.setLength(nChars+1);   // one extra for the byte that contains the shift info
    bytes.grow(NumericUtils.BUF_SIZE_LONG);  // use the max
    bytes.setByteAt(0, (byte)(SHIFT_START_INT + shift));
    int sortableBits = val ^ 0x80000000;
    sortableBits >>>= shift;
    while (nChars > 0) {
      // Store 7 bits per byte for compatibility
      // with UTF-8 encoding of terms
      bytes.setByteAt(nChars--, (byte)(sortableBits & 0x7f));
      sortableBits >>>= 7;
    }
  }


  public static int getPrefixCodedLongShift(final BytesRef val) {
    final int shift = val.bytes[val.offset] - SHIFT_START_LONG;
    if (shift > 63 || shift < 0)
      throw new NumberFormatException("Invalid shift value (" + shift + ") in prefixCoded bytes (is encoded value really an INT?)");
    return shift;
  }

  public static int getPrefixCodedIntShift(final BytesRef val) {
    final int shift = val.bytes[val.offset] - SHIFT_START_INT;
    if (shift > 31 || shift < 0)
      throw new NumberFormatException("Invalid shift value in prefixCoded bytes (is encoded value really an INT?)");
    return shift;
  }

  public static long prefixCodedToLong(final BytesRef val) {
    long sortableBits = 0L;
    for (int i=val.offset+1, limit=val.offset+val.length; i<limit; i++) {
      sortableBits <<= 7;
      final byte b = val.bytes[i];
      if (b < 0) {
        throw new NumberFormatException(
          "Invalid prefixCoded numerical value representation (byte "+
          Integer.toHexString(b&0xff)+" at position "+(i-val.offset)+" is invalid)"
        );
      }
      sortableBits |= b;
    }
    return (sortableBits << getPrefixCodedLongShift(val)) ^ 0x8000000000000000L;
  }

  public static int prefixCodedToInt(final BytesRef val) {
    int sortableBits = 0;
    for (int i=val.offset+1, limit=val.offset+val.length; i<limit; i++) {
      sortableBits <<= 7;
      final byte b = val.bytes[i];
      if (b < 0) {
        throw new NumberFormatException(
          "Invalid prefixCoded numerical value representation (byte "+
          Integer.toHexString(b&0xff)+" at position "+(i-val.offset)+" is invalid)"
        );
      }
      sortableBits |= b;
    }
    return (sortableBits << getPrefixCodedIntShift(val)) ^ 0x80000000;
  }

  public static long doubleToSortableLong(double val) {
    return sortableDoubleBits(Double.doubleToLongBits(val));
  }

  public static double sortableLongToDouble(long val) {
    return Double.longBitsToDouble(sortableDoubleBits(val));
  }

  public static int floatToSortableInt(float val) {
    return sortableFloatBits(Float.floatToIntBits(val));
  }

  public static float sortableIntToFloat(int val) {
    return Float.intBitsToFloat(sortableFloatBits(val));
  }
  
  public static long sortableDoubleBits(long bits) {
    return bits ^ (bits >> 63) & 0x7fffffffffffffffL;
  }
  
  public static int sortableFloatBits(int bits) {
    return bits ^ (bits >> 31) & 0x7fffffff;
  }

  public static void splitLongRange(final LongRangeBuilder builder,
    final int precisionStep,  final long minBound, final long maxBound
  ) {
    splitRange(builder, 64, precisionStep, minBound, maxBound);
  }
  
  public static void splitIntRange(final IntRangeBuilder builder,
    final int precisionStep,  final int minBound, final int maxBound
  ) {
    splitRange(builder, 32, precisionStep, minBound, maxBound);
  }
  
  private static void splitRange(
    final Object builder, final int valSize,
    final int precisionStep, long minBound, long maxBound
  ) {
    if (precisionStep < 1)
      throw new IllegalArgumentException("precisionStep must be >=1");
    if (minBound > maxBound) return;
    for (int shift=0; ; shift += precisionStep) {
      // calculate new bounds for inner precision
      final long diff = 1L << (shift+precisionStep),
        mask = ((1L<<precisionStep) - 1L) << shift;
      final boolean
        hasLower = (minBound & mask) != 0L,
        hasUpper = (maxBound & mask) != mask;
      final long
        nextMinBound = (hasLower ? (minBound + diff) : minBound) & ~mask,
        nextMaxBound = (hasUpper ? (maxBound - diff) : maxBound) & ~mask;
      final boolean
        lowerWrapped = nextMinBound < minBound,
        upperWrapped = nextMaxBound > maxBound;
      
      if (shift+precisionStep>=valSize || nextMinBound>nextMaxBound || lowerWrapped || upperWrapped) {
        // We are in the lowest precision or the next precision is not available.
        addRange(builder, valSize, minBound, maxBound, shift);
        // exit the split recursion loop
        break;
      }
      
      if (hasLower)
        addRange(builder, valSize, minBound, minBound | mask, shift);
      if (hasUpper)
        addRange(builder, valSize, maxBound & ~mask, maxBound, shift);
      
      // recurse to next precision
      minBound = nextMinBound;
      maxBound = nextMaxBound;
    }
  }
  
  private static void addRange(
    final Object builder, final int valSize,
    long minBound, long maxBound,
    final int shift
  ) {
    // for the max bound set all lower bits (that were shifted away):
    // this is important for testing or other usages of the splitted range
    // (e.g. to reconstruct the full range). The prefixEncoding will remove
    // the bits anyway, so they do not hurt!
    maxBound |= (1L << shift) - 1L;
    // delegate to correct range builder
    switch(valSize) {
      case 64:
        ((LongRangeBuilder)builder).addRange(minBound, maxBound, shift);
        break;
      case 32:
        ((IntRangeBuilder)builder).addRange((int)minBound, (int)maxBound, shift);
        break;
      default:
        // Should not happen!
        throw new IllegalArgumentException("valSize must be 32 or 64.");
    }
  }

  public static abstract class LongRangeBuilder {
    
    public void addRange(BytesRef minPrefixCoded, BytesRef maxPrefixCoded) {
      throw new UnsupportedOperationException();
    }
    
    public void addRange(final long min, final long max, final int shift) {
      final BytesRefBuilder minBytes = new BytesRefBuilder(), maxBytes = new BytesRefBuilder();
      longToPrefixCoded(min, shift, minBytes);
      longToPrefixCoded(max, shift, maxBytes);
      addRange(minBytes.get(), maxBytes.get());
    }
  
  }
  
  public static abstract class IntRangeBuilder {
    
    public void addRange(BytesRef minPrefixCoded, BytesRef maxPrefixCoded) {
      throw new UnsupportedOperationException();
    }
    
    public void addRange(final int min, final int max, final int shift) {
      final BytesRefBuilder minBytes = new BytesRefBuilder(), maxBytes = new BytesRefBuilder();
      intToPrefixCoded(min, shift, minBytes);
      intToPrefixCoded(max, shift, maxBytes);
      addRange(minBytes.get(), maxBytes.get());
    }
  
  }
  
  public static TermsEnum filterPrefixCodedLongs(TermsEnum termsEnum) {
    return new SeekingNumericFilteredTermsEnum(termsEnum) {

      @Override
      protected AcceptStatus accept(BytesRef term) {
        return NumericUtils.getPrefixCodedLongShift(term) == 0 ? AcceptStatus.YES : AcceptStatus.END;
      }
    };
  }

  public static TermsEnum filterPrefixCodedInts(TermsEnum termsEnum) {
    return new SeekingNumericFilteredTermsEnum(termsEnum) {
      
      @Override
      protected AcceptStatus accept(BytesRef term) {
        return NumericUtils.getPrefixCodedIntShift(term) == 0 ? AcceptStatus.YES : AcceptStatus.END;
      }
    };
  }


  private static abstract class SeekingNumericFilteredTermsEnum extends FilteredTermsEnum {
    public SeekingNumericFilteredTermsEnum(final TermsEnum tenum) {
      super(tenum, false);
    }

    @Override
    @SuppressWarnings("fallthrough")
    public SeekStatus seekCeil(BytesRef term) throws IOException {

      // NOTE: This is not general!!  It only handles YES
      // and END, because that's all we need for the numeric
      // case here

      SeekStatus status = tenum.seekCeil(term);
      if (status == SeekStatus.END) {
        return SeekStatus.END;
      }

      actualTerm = tenum.term();

      if (accept(actualTerm) == AcceptStatus.YES) {
        return status;
      } else {
        return SeekStatus.END;
      }
    }
  }

  private static Terms intTerms(Terms terms) {
    return new FilterLeafReader.FilterTerms(terms) {
        @Override
        public TermsEnum iterator() throws IOException {
          return filterPrefixCodedInts(in.iterator());
        }
      };
  }

  private static Terms longTerms(Terms terms) {
    return new FilterLeafReader.FilterTerms(terms) {
        @Override
        public TermsEnum iterator() throws IOException {
          return filterPrefixCodedLongs(in.iterator());
        }
      };
  }
    
  public static Integer getMinInt(Terms terms) throws IOException {
    // All shift=0 terms are sorted first, so we don't need
    // to filter the incoming terms; we can just get the
    // min:
    BytesRef min = terms.getMin();
    return (min != null) ? NumericUtils.prefixCodedToInt(min) : null;
  }

  public static Integer getMaxInt(Terms terms) throws IOException {
    BytesRef max = intTerms(terms).getMax();
    return (max != null) ? NumericUtils.prefixCodedToInt(max) : null;
  }

  public static Long getMinLong(Terms terms) throws IOException {
    // All shift=0 terms are sorted first, so we don't need
    // to filter the incoming terms; we can just get the
    // min:
    BytesRef min = terms.getMin();
    return (min != null) ? NumericUtils.prefixCodedToLong(min) : null;
  }

  public static Long getMaxLong(Terms terms) throws IOException {
    BytesRef max = longTerms(terms).getMax();
    return (max != null) ? NumericUtils.prefixCodedToLong(max) : null;
  }
  
}
