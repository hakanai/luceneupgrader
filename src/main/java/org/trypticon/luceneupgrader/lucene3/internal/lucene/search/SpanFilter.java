package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;

import java.io.IOException;

public abstract class SpanFilter extends Filter{
  public abstract SpanFilterResult bitSpans(IndexReader reader) throws IOException;
}
