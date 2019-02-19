package org.trypticon.luceneupgrader.lucene3.internal.lucene.document;

import java.io.Serializable;
public interface FieldSelector extends Serializable {

  FieldSelectorResult accept(String fieldName);
}
