package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PositionBasedTermVectorMapper extends TermVectorMapper{
  private Map<String, Map<Integer,TVPositionInfo>> fieldToTerms;

  private String currentField;
  private Map<Integer,TVPositionInfo> currentPositions;
  private boolean storeOffsets;

  


  public PositionBasedTermVectorMapper() {
    super(false, false);
  }

  public PositionBasedTermVectorMapper(boolean ignoringOffsets)
  {
    super(false, ignoringOffsets);
  }

  @Override
  public boolean isIgnoringPositions() {
    return false;
  }

  @Override
  public void map(String term, int frequency, TermVectorOffsetInfo[] offsets, int[] positions) {
    for (int i = 0; i < positions.length; i++) {
      Integer posVal = Integer.valueOf(positions[i]);
      TVPositionInfo pos = currentPositions.get(posVal);
      if (pos == null) {
        pos = new TVPositionInfo(positions[i], storeOffsets);
        currentPositions.put(posVal, pos);
      }
      pos.addTerm(term, offsets != null ? offsets[i] : null);
    }
  }

  @Override
  public void setExpectations(String field, int numTerms, boolean storeOffsets, boolean storePositions) {
    if (storePositions == false)
    {
      throw new RuntimeException("You must store positions in order to use this Mapper");
    }
    if (storeOffsets == true)
    {
      //ignoring offsets
    }
    fieldToTerms = new HashMap<String,Map<Integer,TVPositionInfo>>(numTerms);
    this.storeOffsets = storeOffsets;
    currentField = field;
    currentPositions = new HashMap<Integer,TVPositionInfo>();
    fieldToTerms.put(currentField, currentPositions);
  }

  public Map<String,Map<Integer,TVPositionInfo>>  getFieldToTerms() {
    return fieldToTerms;
  }

  public static class TVPositionInfo{
    private int position;

    private List<String> terms;

    private List<TermVectorOffsetInfo> offsets;


    public TVPositionInfo(int position, boolean storeOffsets) {
      this.position = position;
      terms = new ArrayList<String>();
      if (storeOffsets) {
        offsets = new ArrayList<TermVectorOffsetInfo>();
      }
    }

    void addTerm(String term, TermVectorOffsetInfo info)
    {
      terms.add(term);
      if (offsets != null) {
        offsets.add(info);
      }
    }

    public int getPosition() {
      return position;
    }

    public List<String> getTerms() {
      return terms;
    }

    public List<TermVectorOffsetInfo> getOffsets() {
      return offsets;
    }
  }


}
