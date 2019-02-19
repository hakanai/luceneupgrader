package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;
import java.util.Comparator;


public class TermVectorEntryFreqSortedComparator implements Comparator<TermVectorEntry> {
  public int compare(TermVectorEntry entry, TermVectorEntry entry1) {
    int result = 0;
    result = entry1.getFrequency() - entry.getFrequency();
    if (result == 0)
    {
      result = entry.getTerm().compareTo(entry1.getTerm());
      if (result == 0)
      {
        result = entry.getField().compareTo(entry1.getField());
      }
    }
    return result;
  }
}
