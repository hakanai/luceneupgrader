package org.trypticon.luceneupgrader.lucene4.internal.lucene.search.spans;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsAndPositionsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Collections;
import java.util.Collection;

public class TermSpans extends Spans {
  protected final DocsAndPositionsEnum postings;
  protected final Term term;
  protected int doc;
  protected int freq;
  protected int count;
  protected int position;
  protected boolean readPayload;

  public TermSpans(DocsAndPositionsEnum postings, Term term) {
    this.postings = postings;
    this.term = term;
    doc = -1;
  }

  // only for EmptyTermSpans (below)
  TermSpans() {
    term = null;
    postings = null;
  }

  @Override
  public boolean next() throws IOException {
    if (count == freq) {
      if (postings == null) {
        return false;
      }
      doc = postings.nextDoc();
      if (doc == DocIdSetIterator.NO_MORE_DOCS) {
        return false;
      }
      freq = postings.freq();
      count = 0;
    }
    position = postings.nextPosition();
    count++;
    readPayload = false;
    return true;
  }

  @Override
  public boolean skipTo(int target) throws IOException {
    assert target > doc;
    doc = postings.advance(target);
    if (doc == DocIdSetIterator.NO_MORE_DOCS) {
      return false;
    }

    freq = postings.freq();
    count = 0;
    position = postings.nextPosition();
    count++;
    readPayload = false;
    return true;
  }

  @Override
  public int doc() {
    return doc;
  }

  @Override
  public int start() {
    return position;
  }

  @Override
  public int end() {
    return position + 1;
  }

  @Override
  public long cost() {
    return postings.cost();
  }

  // TODO: Remove warning after API has been finalized
  @Override
  public Collection<byte[]> getPayload() throws IOException {
    final BytesRef payload = postings.getPayload();
    readPayload = true;
    final byte[] bytes;
    if (payload != null) {
      bytes = new byte[payload.length];
      System.arraycopy(payload.bytes, payload.offset, bytes, 0, payload.length);
    } else {
      bytes = null;
    }
    return Collections.singletonList(bytes);
  }

  // TODO: Remove warning after API has been finalized
  @Override
  public boolean isPayloadAvailable() throws IOException {
    return readPayload == false && postings.getPayload() != null;
  }

  @Override
  public String toString() {
    return "spans(" + term.toString() + ")@" +
            (doc == -1 ? "START" : (doc == Integer.MAX_VALUE) ? "END" : doc + "-" + position);
  }

  public DocsAndPositionsEnum getPostings() {
    return postings;
  }

  private static final class EmptyTermSpans extends TermSpans {

    @Override
    public boolean next() {
      return false;
    }

    @Override
    public boolean skipTo(int target) {
      return false;
    }

    @Override
    public int doc() {
      return DocIdSetIterator.NO_MORE_DOCS;
    }
    
    @Override
    public int start() {
      return -1;
    }

    @Override
    public int end() {
      return -1;
    }

    @Override
    public Collection<byte[]> getPayload() {
      return null;
    }

    @Override
    public boolean isPayloadAvailable() {
      return false;
    }

    @Override
    public long cost() {
      return 0;
    }
  }

  public static final TermSpans EMPTY_TERM_SPANS = new EmptyTermSpans();
}
