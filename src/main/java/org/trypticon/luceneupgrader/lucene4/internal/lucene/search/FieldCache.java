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

import java.io.IOException;
import java.io.PrintStream;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.NumericTokenStream;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.DoubleField;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.FloatField;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.IntField;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.LongField;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.NumericDocValuesField;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.BinaryDocValues;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocTermOrds;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReader; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SortedDocValues;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SortedSetDocValues;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.NumericUtils;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.RamUsageEstimator;

public interface FieldCache {

  public static abstract class Bytes {
    public abstract byte get(int docID);
    
    public static final Bytes EMPTY = new Bytes() {
      @Override
      public byte get(int docID) {
        return 0;
      }
    };
  }

  public static abstract class Shorts {
    public abstract short get(int docID);
    
    public static final Shorts EMPTY = new Shorts() {
      @Override
      public short get(int docID) {
        return 0;
      }
    };
  }

  public static abstract class Ints {
    public abstract int get(int docID);
    
    public static final Ints EMPTY = new Ints() {
      @Override
      public int get(int docID) {
        return 0;
      }
    };
  }

  public static abstract class Longs {
    public abstract long get(int docID);
    
    public static final Longs EMPTY = new Longs() {
      @Override
      public long get(int docID) {
        return 0;
      }
    };
  }

  public static abstract class Floats {
    public abstract float get(int docID);
    
    public static final Floats EMPTY = new Floats() {
      @Override
      public float get(int docID) {
        return 0;
      }
    };
  }

  public static abstract class Doubles {
    public abstract double get(int docID);
    
    public static final Doubles EMPTY = new Doubles() {
      @Override
      public double get(int docID) {
        return 0;
      }
    };
  }

  public static final class CreationPlaceholder implements Accountable {
    Accountable value;
    
    @Override
    public long ramBytesUsed() {
      // don't call on the in-progress value, might make things angry.
      return RamUsageEstimator.NUM_BYTES_OBJECT_REF;
    }
  }

  public interface Parser {
    
    public TermsEnum termsEnum(Terms terms) throws IOException;
  }


  @Deprecated
  public interface ByteParser extends Parser {
    public byte parseByte(BytesRef term);
  }


  @Deprecated
  public interface ShortParser extends Parser {
    public short parseShort(BytesRef term);
  }


  public interface IntParser extends Parser {
    public int parseInt(BytesRef term);
  }


  public interface FloatParser extends Parser {
    public float parseFloat(BytesRef term);
  }


  public interface LongParser extends Parser {
    public long parseLong(BytesRef term);
  }


  public interface DoubleParser extends Parser {
    public double parseDouble(BytesRef term);
  }

  public static FieldCache DEFAULT = new FieldCacheImpl();

  @Deprecated
  public static final ByteParser DEFAULT_BYTE_PARSER = new ByteParser() {
    @Override
    public byte parseByte(BytesRef term) {
      // TODO: would be far better to directly parse from
      // UTF8 bytes... but really users should use
      // IntField, instead, which already decodes
      // directly from byte[]
      return Byte.parseByte(term.utf8ToString());
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".DEFAULT_BYTE_PARSER"; 
    }
    @Override
    public TermsEnum termsEnum(Terms terms) throws IOException {
      return terms.iterator(null);
    }
  };

  @Deprecated
  public static final ShortParser DEFAULT_SHORT_PARSER = new ShortParser() {
    @Override
    public short parseShort(BytesRef term) {
      // TODO: would be far better to directly parse from
      // UTF8 bytes... but really users should use
      // IntField, instead, which already decodes
      // directly from byte[]
      return Short.parseShort(term.utf8ToString());
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".DEFAULT_SHORT_PARSER"; 
    }
    
    @Override
    public TermsEnum termsEnum(Terms terms) throws IOException {
      return terms.iterator(null);
    }
  };

  @Deprecated
  public static final IntParser DEFAULT_INT_PARSER = new IntParser() {
    @Override
    public int parseInt(BytesRef term) {
      // TODO: would be far better to directly parse from
      // UTF8 bytes... but really users should use
      // IntField, instead, which already decodes
      // directly from byte[]
      return Integer.parseInt(term.utf8ToString());
    }
    
    @Override
    public TermsEnum termsEnum(Terms terms) throws IOException {
      return terms.iterator(null);
    }
    
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".DEFAULT_INT_PARSER"; 
    }
  };

  @Deprecated
  public static final FloatParser DEFAULT_FLOAT_PARSER = new FloatParser() {
    @Override
    public float parseFloat(BytesRef term) {
      // TODO: would be far better to directly parse from
      // UTF8 bytes... but really users should use
      // FloatField, instead, which already decodes
      // directly from byte[]
      return Float.parseFloat(term.utf8ToString());
    }
    
    @Override
    public TermsEnum termsEnum(Terms terms) throws IOException {
      return terms.iterator(null);
    }
    
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".DEFAULT_FLOAT_PARSER"; 
    }
  };

  @Deprecated
  public static final LongParser DEFAULT_LONG_PARSER = new LongParser() {
    @Override
    public long parseLong(BytesRef term) {
      // TODO: would be far better to directly parse from
      // UTF8 bytes... but really users should use
      // LongField, instead, which already decodes
      // directly from byte[]
      return Long.parseLong(term.utf8ToString());
    }
    
    @Override
    public TermsEnum termsEnum(Terms terms) throws IOException {
      return terms.iterator(null);
    }
    
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".DEFAULT_LONG_PARSER"; 
    }
  };

  @Deprecated
  public static final DoubleParser DEFAULT_DOUBLE_PARSER = new DoubleParser() {
    @Override
    public double parseDouble(BytesRef term) {
      // TODO: would be far better to directly parse from
      // UTF8 bytes... but really users should use
      // DoubleField, instead, which already decodes
      // directly from byte[]
      return Double.parseDouble(term.utf8ToString());
    }
    
    @Override
    public TermsEnum termsEnum(Terms terms) throws IOException {
      return terms.iterator(null);
    }
    
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".DEFAULT_DOUBLE_PARSER"; 
    }
  };

  public static final IntParser NUMERIC_UTILS_INT_PARSER=new IntParser(){
    @Override
    public int parseInt(BytesRef term) {
      return NumericUtils.prefixCodedToInt(term);
    }
    
    @Override
    public TermsEnum termsEnum(Terms terms) throws IOException {
      return NumericUtils.filterPrefixCodedInts(terms.iterator(null));
    }
    
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".NUMERIC_UTILS_INT_PARSER"; 
    }
  };

  public static final FloatParser NUMERIC_UTILS_FLOAT_PARSER=new FloatParser(){
    @Override
    public float parseFloat(BytesRef term) {
      return NumericUtils.sortableIntToFloat(NumericUtils.prefixCodedToInt(term));
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".NUMERIC_UTILS_FLOAT_PARSER"; 
    }
    
    @Override
    public TermsEnum termsEnum(Terms terms) throws IOException {
      return NumericUtils.filterPrefixCodedInts(terms.iterator(null));
    }
  };

  public static final LongParser NUMERIC_UTILS_LONG_PARSER = new LongParser(){
    @Override
    public long parseLong(BytesRef term) {
      return NumericUtils.prefixCodedToLong(term);
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".NUMERIC_UTILS_LONG_PARSER"; 
    }
    
    @Override
    public TermsEnum termsEnum(Terms terms) throws IOException {
      return NumericUtils.filterPrefixCodedLongs(terms.iterator(null));
    }
  };

  public static final DoubleParser NUMERIC_UTILS_DOUBLE_PARSER = new DoubleParser(){
    @Override
    public double parseDouble(BytesRef term) {
      return NumericUtils.sortableLongToDouble(NumericUtils.prefixCodedToLong(term));
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".NUMERIC_UTILS_DOUBLE_PARSER"; 
    }
    
    @Override
    public TermsEnum termsEnum(Terms terms) throws IOException {
      return NumericUtils.filterPrefixCodedLongs(terms.iterator(null));
    }
  };
  

  public Bits getDocsWithField(AtomicReader reader, String field) throws IOException;


  @Deprecated
  public Bytes getBytes(AtomicReader reader, String field, boolean setDocsWithField) throws IOException;


  @Deprecated
  public Bytes getBytes(AtomicReader reader, String field, ByteParser parser, boolean setDocsWithField) throws IOException;


  @Deprecated
  public Shorts getShorts (AtomicReader reader, String field, boolean setDocsWithField) throws IOException;


  @Deprecated
  public Shorts getShorts (AtomicReader reader, String field, ShortParser parser, boolean setDocsWithField) throws IOException;
  
  public Ints getInts(AtomicReader reader, String field, boolean setDocsWithField) throws IOException;

  public Ints getInts(AtomicReader reader, String field, IntParser parser, boolean setDocsWithField) throws IOException;

  public Floats getFloats(AtomicReader reader, String field, boolean setDocsWithField) throws IOException;

  public Floats getFloats(AtomicReader reader, String field, FloatParser parser, boolean setDocsWithField) throws IOException;

  public Longs getLongs(AtomicReader reader, String field, boolean setDocsWithField) throws IOException;

  public Longs getLongs(AtomicReader reader, String field, LongParser parser, boolean setDocsWithField) throws IOException;

  public Doubles getDoubles(AtomicReader reader, String field, boolean setDocsWithField) throws IOException;

  public Doubles getDoubles(AtomicReader reader, String field, DoubleParser parser, boolean setDocsWithField) throws IOException;


  public BinaryDocValues getTerms(AtomicReader reader, String field, boolean setDocsWithField) throws IOException;


  public BinaryDocValues getTerms(AtomicReader reader, String field, boolean setDocsWithField, float acceptableOverheadRatio) throws IOException;


  public SortedDocValues getTermsIndex(AtomicReader reader, String field) throws IOException;


  public SortedDocValues getTermsIndex(AtomicReader reader, String field, float acceptableOverheadRatio) throws IOException;

  public SortedSetDocValues getDocTermOrds(AtomicReader reader, String field) throws IOException;

  public final class CacheEntry {

    private final Object readerKey;
    private final String fieldName;
    private final Class<?> cacheType;
    private final Object custom;
    private final Accountable value;
    private String size;

    public CacheEntry(Object readerKey, String fieldName,
                      Class<?> cacheType,
                      Object custom,
                      Accountable value) {
      this.readerKey = readerKey;
      this.fieldName = fieldName;
      this.cacheType = cacheType;
      this.custom = custom;
      this.value = value;
    }

    public Object getReaderKey() {
      return readerKey;
    }

    public String getFieldName() {
      return fieldName;
    }

    public Class<?> getCacheType() {
      return cacheType;
    }

    public Object getCustom() {
      return custom;
    }

    public Object getValue() {
      return value;
    }

    public String getEstimatedSize() {
      long bytesUsed = value == null ? 0 : value.ramBytesUsed();
      return RamUsageEstimator.humanReadableUnits(bytesUsed);
    }
    
    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append("'").append(getReaderKey()).append("'=>");
      b.append("'").append(getFieldName()).append("',");
      b.append(getCacheType()).append(",").append(getCustom());
      b.append("=>").append(getValue().getClass().getName()).append("#");
      b.append(System.identityHashCode(getValue()));
      
      String s = getEstimatedSize();
      if(null != s) {
        b.append(" (size =~ ").append(s).append(')');
      }

      return b.toString();
    }
  }
  
  public CacheEntry[] getCacheEntries();

  public void purgeAllCaches();

  public void purgeByCacheKey(Object coreCacheKey);

  public void setInfoStream(PrintStream stream);

  public PrintStream getInfoStream();
}
