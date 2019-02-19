package org.trypticon.luceneupgrader.lucene3.internal.lucene.document;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.FieldInfo.IndexOptions;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.FieldInvertState; // for javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.PhraseQuery; // for javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.spans.SpanQuery; // for javadocs

import java.io.Reader;
import java.io.Serializable;


public interface Fieldable extends Serializable {

  void setBoost(float boost);


  float getBoost();


  String name();


  public String stringValue();
  

  public Reader readerValue();
  

  public TokenStream tokenStreamValue();

  boolean  isStored();

  boolean  isIndexed();

  boolean  isTokenized();


  boolean isTermVectorStored();

  boolean isStoreOffsetWithTermVector();

  boolean isStorePositionWithTermVector();

  boolean  isBinary();

  boolean getOmitNorms();


  void setOmitNorms(boolean omitNorms);

  boolean isLazy();
  
  abstract int getBinaryOffset();
  
  abstract int getBinaryLength();

  abstract byte[] getBinaryValue();

  abstract byte[] getBinaryValue(byte[] result);
  
  IndexOptions getIndexOptions();
  
  void setIndexOptions(IndexOptions indexOptions);
}
