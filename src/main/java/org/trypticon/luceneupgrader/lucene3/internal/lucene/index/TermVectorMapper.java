package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;
public abstract class TermVectorMapper {

  private boolean ignoringPositions;
  private boolean ignoringOffsets;


  protected TermVectorMapper() {
  }

  protected TermVectorMapper(boolean ignoringPositions, boolean ignoringOffsets) {
    this.ignoringPositions = ignoringPositions;
    this.ignoringOffsets = ignoringOffsets;
  }

  public abstract void setExpectations(String field, int numTerms, boolean storeOffsets, boolean storePositions);
  public abstract void map(String term, int frequency, TermVectorOffsetInfo [] offsets, int [] positions);

  public boolean isIgnoringPositions()
  {
    return ignoringPositions;
  }

  public boolean isIgnoringOffsets()
  {
    return ignoringOffsets;
  }

  public void setDocumentNumber(int documentNumber) {
  }

}
