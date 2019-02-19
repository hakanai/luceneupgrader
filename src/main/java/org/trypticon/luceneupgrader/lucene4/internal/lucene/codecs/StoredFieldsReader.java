package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs;

import java.io.Closeable;
import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.StoredFieldVisitor;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Accountable;

public abstract class StoredFieldsReader implements Cloneable, Closeable, Accountable {
  protected StoredFieldsReader() {
  }
  
  public abstract void visitDocument(int n, StoredFieldVisitor visitor) throws IOException;

  @Override
  public abstract StoredFieldsReader clone();
  

  public abstract void checkIntegrity() throws IOException;
}
