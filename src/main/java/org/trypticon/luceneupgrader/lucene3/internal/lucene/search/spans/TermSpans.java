package org.trypticon.luceneupgrader.lucene3.internal.lucene.search.spans;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.TermPositions;

import java.io.IOException;
import java.util.Collections;
import java.util.Collection;

public class TermSpans extends Spans {
  protected TermPositions positions;
  protected Term term;
  protected int doc;
  protected int freq;
  protected int count;
  protected int position;


  public TermSpans(TermPositions positions, Term term) throws IOException {

    this.positions = positions;
    this.term = term;
    doc = -1;
  }

  @Override
  public boolean next() throws IOException {
    if (count == freq) {
      if (!positions.next()) {
        doc = Integer.MAX_VALUE;
        return false;
      }
      doc = positions.doc();
      freq = positions.freq();
      count = 0;
    }
    position = positions.nextPosition();
    count++;
    return true;
  }

  @Override
  public boolean skipTo(int target) throws IOException {
    if (!positions.skipTo(target)) {
      doc = Integer.MAX_VALUE;
      return false;
    }

    doc = positions.doc();
    freq = positions.freq();
    count = 0;

    position = positions.nextPosition();
    count++;

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

  // TODO: Remove warning after API has been finalized
  @Override
  public Collection<byte[]> getPayload() throws IOException {
    byte [] bytes = new byte[positions.getPayloadLength()]; 
    bytes = positions.getPayload(bytes, 0);
    return Collections.singletonList(bytes);
  }

  // TODO: Remove warning after API has been finalized
  @Override
  public boolean isPayloadAvailable() {
    return positions.isPayloadAvailable();
  }

  @Override
  public String toString() {
    return "spans(" + term.toString() + ")@" +
            (doc == -1 ? "START" : (doc == Integer.MAX_VALUE) ? "END" : doc + "-" + position);
  }


  public TermPositions getPositions() {
    return positions;
  }
}
