package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

public class TermVectorEntry {
  private String field;
  private String term;
  private int frequency;
  private TermVectorOffsetInfo [] offsets;
  int [] positions;


  public TermVectorEntry() {
  }

  public TermVectorEntry(String field, String term, int frequency, TermVectorOffsetInfo[] offsets, int[] positions) {
    this.field = field;
    this.term = term;
    this.frequency = frequency;
    this.offsets = offsets;
    this.positions = positions;
  }


  public String getField() {
    return field;
  }

  public int getFrequency() {
    return frequency;
  }

  public TermVectorOffsetInfo[] getOffsets() {
    return offsets;
  }

  public int[] getPositions() {
    return positions;
  }

  public String getTerm() {
    return term;
  }

  //Keep package local
  void setFrequency(int frequency) {
    this.frequency = frequency;
  }

  void setOffsets(TermVectorOffsetInfo[] offsets) {
    this.offsets = offsets;
  }

  void setPositions(int[] positions) {
    this.positions = positions;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TermVectorEntry that = (TermVectorEntry) o;

    if (term != null ? !term.equals(that.term) : that.term != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return (term != null ? term.hashCode() : 0);
  }

  @Override
  public String toString() {
    return "TermVectorEntry{" +
            "field='" + field + '\'' +
            ", term='" + term + '\'' +
            ", frequency=" + frequency +
            '}';
  }
}
