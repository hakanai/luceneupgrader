package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

import java.util.*;

public class FieldSortedTermVectorMapper extends TermVectorMapper{
  private Map<String,SortedSet<TermVectorEntry>> fieldToTerms = new HashMap<String,SortedSet<TermVectorEntry>>();
  private SortedSet<TermVectorEntry> currentSet;
  private String currentField;
  private Comparator<TermVectorEntry> comparator;

  public FieldSortedTermVectorMapper(Comparator<TermVectorEntry> comparator) {
    this(false, false, comparator);
  }


  public FieldSortedTermVectorMapper(boolean ignoringPositions, boolean ignoringOffsets, Comparator<TermVectorEntry> comparator) {
    super(ignoringPositions, ignoringOffsets);
    this.comparator = comparator;
  }

  @Override
  public void map(String term, int frequency, TermVectorOffsetInfo[] offsets, int[] positions) {
    TermVectorEntry entry = new TermVectorEntry(currentField, term, frequency, offsets, positions);
    currentSet.add(entry);
  }

  @Override
  public void setExpectations(String field, int numTerms, boolean storeOffsets, boolean storePositions) {
    currentSet = new TreeSet<TermVectorEntry>(comparator);
    currentField = field;
    fieldToTerms.put(field, currentSet);
  }

  public Map<String,SortedSet<TermVectorEntry>> getFieldToTerms() {
    return fieldToTerms;
  }


  public Comparator<TermVectorEntry> getComparator() {
    return comparator;
  }
}
