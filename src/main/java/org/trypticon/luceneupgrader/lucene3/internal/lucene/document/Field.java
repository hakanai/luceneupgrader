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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.document;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.FieldInfo.IndexOptions;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.StringHelper;

import java.io.Reader;
import java.io.Serializable;

public final class Field extends AbstractField implements Fieldable, Serializable {
  
  public static enum Store {

    YES {
      @Override
      public boolean isStored() { return true; }
    },

    NO {
      @Override
      public boolean isStored() { return false; }
    };

    public abstract boolean isStored();
  }

  public static enum Index {


    NO {
      @Override
      public boolean isIndexed()  { return false; }
      @Override
      public boolean isAnalyzed() { return false; }
      @Override
      public boolean omitNorms()  { return true;  }   
    },


    ANALYZED {
      @Override
      public boolean isIndexed()  { return true;  }
      @Override
      public boolean isAnalyzed() { return true;  }
      @Override
      public boolean omitNorms()  { return false; }   	
    },

    NOT_ANALYZED {
      @Override
      public boolean isIndexed()  { return true;  }
      @Override
      public boolean isAnalyzed() { return false; }
      @Override
      public boolean omitNorms()  { return false; }   	
    },


    NOT_ANALYZED_NO_NORMS {
      @Override
      public boolean isIndexed()  { return true;  }
      @Override
      public boolean isAnalyzed() { return false; }
      @Override
      public boolean omitNorms()  { return true;  }   	
    },


    ANALYZED_NO_NORMS {
      @Override
      public boolean isIndexed()  { return true;  }
      @Override
      public boolean isAnalyzed() { return true;  }
      @Override
      public boolean omitNorms()  { return true;  }   	
    };

    public static Index toIndex(boolean indexed, boolean analyzed) {
      return toIndex(indexed, analyzed, false);
    }

    public static Index toIndex(boolean indexed, boolean analyzed, boolean omitNorms) {

      // If it is not indexed nothing else matters
      if (!indexed) {
        return Index.NO;
      }

      // typical, non-expert
      if (!omitNorms) {
        if (analyzed) {
          return Index.ANALYZED;
        }
        return Index.NOT_ANALYZED;
      }

      // Expert: Norms omitted
      if (analyzed) {
        return Index.ANALYZED_NO_NORMS;
      }
      return Index.NOT_ANALYZED_NO_NORMS;
    }

    public abstract boolean isIndexed();
    public abstract boolean isAnalyzed();
    public abstract boolean omitNorms();  	
  }

  public static enum TermVector {
    
    NO {
      @Override
      public boolean isStored()      { return false; }
      @Override
      public boolean withPositions() { return false; }
      @Override
      public boolean withOffsets()   { return false; }
    },
    
    YES {
      @Override
      public boolean isStored()      { return true;  }
      @Override
      public boolean withPositions() { return false; }
      @Override
      public boolean withOffsets()   { return false; }
    },
    
    WITH_POSITIONS {
      @Override
      public boolean isStored()      { return true;  }
      @Override
      public boolean withPositions() { return true;  }
      @Override
      public boolean withOffsets()   { return false; }
    },
    
    WITH_OFFSETS {
      @Override
      public boolean isStored()      { return true;  }
      @Override
      public boolean withPositions() { return false; }
      @Override
      public boolean withOffsets()   { return true;  }
    },
    
    WITH_POSITIONS_OFFSETS {
      @Override
      public boolean isStored()      { return true;  }
      @Override
      public boolean withPositions() { return true;  }
      @Override
      public boolean withOffsets()   { return true;  }
    };

    public static TermVector toTermVector(boolean stored, boolean withOffsets, boolean withPositions) {

      // If it is not stored, nothing else matters.
      if (!stored) {
        return TermVector.NO;
      }

      if (withOffsets) {
        if (withPositions) {
          return Field.TermVector.WITH_POSITIONS_OFFSETS;
        }
        return Field.TermVector.WITH_OFFSETS;
      }

      if (withPositions) {
        return Field.TermVector.WITH_POSITIONS;
      }
      return Field.TermVector.YES;
    }

    public abstract boolean isStored();
    public abstract boolean withPositions();
    public abstract boolean withOffsets();
  }
  
  

  public String stringValue()   { return fieldsData instanceof String ? (String)fieldsData : null; }
  

  public Reader readerValue()   { return fieldsData instanceof Reader ? (Reader)fieldsData : null; }
    
  public TokenStream tokenStreamValue()   { return tokenStream; }
  


  public void setValue(String value) {
    if (isBinary) {
      throw new IllegalArgumentException("cannot set a String value on a binary field");
    }
    fieldsData = value;
  }

  public void setValue(Reader value) {
    if (isBinary) {
      throw new IllegalArgumentException("cannot set a Reader value on a binary field");
    }
    if (isStored) {
      throw new IllegalArgumentException("cannot set a Reader value on a stored field");
    }
    fieldsData = value;
  }

  public void setValue(byte[] value) {
    if (!isBinary) {
      throw new IllegalArgumentException("cannot set a byte[] value on a non-binary field");
    }
    fieldsData = value;
    binaryLength = value.length;
    binaryOffset = 0;
  }

  public void setValue(byte[] value, int offset, int length) {
    if (!isBinary) {
      throw new IllegalArgumentException("cannot set a byte[] value on a non-binary field");
    }
    fieldsData = value;
    binaryLength = length;
    binaryOffset = offset;
  }
  
  public void setTokenStream(TokenStream tokenStream) {
    this.isIndexed = true;
    this.isTokenized = true;
    this.tokenStream = tokenStream;
  }

  public Field(String name, String value, Store store, Index index) {
    this(name, value, store, index, TermVector.NO);
  }
  
  public Field(String name, String value, Store store, Index index, TermVector termVector) {
    this(name, true, value, store, index, termVector);
  }
  
  public Field(String name, boolean internName, String value, Store store, Index index, TermVector termVector) {
    if (name == null)
      throw new NullPointerException("name cannot be null");
    if (value == null)
      throw new NullPointerException("value cannot be null");
    if (index == Index.NO && store == Store.NO)
      throw new IllegalArgumentException("it doesn't make sense to have a field that "
         + "is neither indexed nor stored");
    if (index == Index.NO && termVector != TermVector.NO)
      throw new IllegalArgumentException("cannot store term vector information "
         + "for a field that is not indexed");
          
    if (internName) // field names are optionally interned
      name = StringHelper.intern(name);
    
    this.name = name; 
    
    this.fieldsData = value;

    this.isStored = store.isStored();
   
    this.isIndexed = index.isIndexed();
    this.isTokenized = index.isAnalyzed();
    this.omitNorms = index.omitNorms();
    if (index == Index.NO) {
      // note: now this reads even wierder than before
      this.indexOptions = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;
    }    

    this.isBinary = false;

    setStoreTermVector(termVector);
  }

  public Field(String name, Reader reader) {
    this(name, reader, TermVector.NO);
  }

  public Field(String name, Reader reader, TermVector termVector) {
    if (name == null)
      throw new NullPointerException("name cannot be null");
    if (reader == null)
      throw new NullPointerException("reader cannot be null");
    
    this.name = StringHelper.intern(name);        // field names are interned
    this.fieldsData = reader;
    
    this.isStored = false;
    
    this.isIndexed = true;
    this.isTokenized = true;
    
    this.isBinary = false;
    
    setStoreTermVector(termVector);
  }

  public Field(String name, TokenStream tokenStream) {
    this(name, tokenStream, TermVector.NO);
  }
  
  public Field(String name, TokenStream tokenStream, TermVector termVector) {
    if (name == null)
      throw new NullPointerException("name cannot be null");
    if (tokenStream == null)
      throw new NullPointerException("tokenStream cannot be null");
    
    this.name = StringHelper.intern(name);        // field names are interned
    this.fieldsData = null;
    this.tokenStream = tokenStream;

    this.isStored = false;
    
    this.isIndexed = true;
    this.isTokenized = true;
    
    this.isBinary = false;
    
    setStoreTermVector(termVector);
  }

  
  @Deprecated
  public Field(String name, byte[] value, Store store) {
    this(name, value, 0, value.length);

    if (store == Store.NO) {
      throw new IllegalArgumentException("binary values can't be unstored");
    }
  }

  public Field(String name, byte[] value) {
    this(name, value, 0, value.length);
  }

  @Deprecated
  public Field(String name, byte[] value, int offset, int length, Store store) {
    this(name, value, offset, length);

    if (store == Store.NO) {
      throw new IllegalArgumentException("binary values can't be unstored");
    }
  }

  public Field(String name, byte[] value, int offset, int length) {

    if (name == null)
      throw new IllegalArgumentException("name cannot be null");
    if (value == null)
      throw new IllegalArgumentException("value cannot be null");
    
    this.name = StringHelper.intern(name);        // field names are interned
    fieldsData = value;
    
    isStored = true;
    isIndexed   = false;
    isTokenized = false;
    indexOptions = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;
    omitNorms = true;
    
    isBinary    = true;
    binaryLength = length;
    binaryOffset = offset;
    
    setStoreTermVector(TermVector.NO);
  }
}
