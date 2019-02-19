/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.DocValuesType;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.IndexOptions;

public class FieldInfos implements Iterable<FieldInfo> {
  private final boolean hasFreq;
  private final boolean hasProx;
  private final boolean hasPayloads;
  private final boolean hasOffsets;
  private final boolean hasVectors;
  private final boolean hasNorms;
  private final boolean hasDocValues;
  
  private final SortedMap<Integer,FieldInfo> byNumber = new TreeMap<>();
  private final HashMap<String,FieldInfo> byName = new HashMap<>();
  private final Collection<FieldInfo> values; // for an unmodifiable iterator
  
  public FieldInfos(FieldInfo[] infos) {
    boolean hasVectors = false;
    boolean hasProx = false;
    boolean hasPayloads = false;
    boolean hasOffsets = false;
    boolean hasFreq = false;
    boolean hasNorms = false;
    boolean hasDocValues = false;
    
    for (FieldInfo info : infos) {
      if (info.number < 0) {
        throw new IllegalArgumentException("illegal field number: " + info.number + " for field " + info.name);
      }
      FieldInfo previous = byNumber.put(info.number, info);
      if (previous != null) {
        throw new IllegalArgumentException("duplicate field numbers: " + previous.name + " and " + info.name + " have: " + info.number);
      }
      previous = byName.put(info.name, info);
      if (previous != null) {
        throw new IllegalArgumentException("duplicate field names: " + previous.number + " and " + info.number + " have: " + info.name);
      }
      
      hasVectors |= info.hasVectors();
      hasProx |= info.isIndexed() && info.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
      hasFreq |= info.isIndexed() && info.getIndexOptions() != IndexOptions.DOCS_ONLY;
      hasOffsets |= info.isIndexed() && info.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0;
      hasNorms |= info.hasNorms();
      hasDocValues |= info.hasDocValues();
      hasPayloads |= info.hasPayloads();
    }
    
    this.hasVectors = hasVectors;
    this.hasProx = hasProx;
    this.hasPayloads = hasPayloads;
    this.hasOffsets = hasOffsets;
    this.hasFreq = hasFreq;
    this.hasNorms = hasNorms;
    this.hasDocValues = hasDocValues;
    this.values = Collections.unmodifiableCollection(byNumber.values());
  }
  
  public boolean hasFreq() {
    return hasFreq;
  }
  
  public boolean hasProx() {
    return hasProx;
  }

  public boolean hasPayloads() {
    return hasPayloads;
  }

  public boolean hasOffsets() {
    return hasOffsets;
  }
  
  public boolean hasVectors() {
    return hasVectors;
  }
  
  public boolean hasNorms() {
    return hasNorms;
  }
  
  public boolean hasDocValues() {
    return hasDocValues;
  }
  
  public int size() {
    assert byNumber.size() == byName.size();
    return byNumber.size();
  }
  
  // TODO: what happens if in fact a different order is used?
  @Override
  public Iterator<FieldInfo> iterator() {
    return values.iterator();
  }

  public FieldInfo fieldInfo(String fieldName) {
    return byName.get(fieldName);
  }

  public FieldInfo fieldInfo(int fieldNumber) {
    if (fieldNumber < 0) {
      throw new IllegalArgumentException("Illegal field number: " + fieldNumber);
    }
    return byNumber.get(fieldNumber);
  }
  
  static final class FieldNumbers {
    
    private final Map<Integer,String> numberToName;
    private final Map<String,Integer> nameToNumber;
    // We use this to enforce that a given field never
    // changes DV type, even across segments / IndexWriter
    // sessions:
    private final Map<String,DocValuesType> docValuesType;

    // TODO: we should similarly catch an attempt to turn
    // norms back on after they were already ommitted; today
    // we silently discard the norm but this is badly trappy
    private int lowestUnassignedFieldNumber = -1;
    
    FieldNumbers() {
      this.nameToNumber = new HashMap<>();
      this.numberToName = new HashMap<>();
      this.docValuesType = new HashMap<>();
    }
    
    synchronized int addOrGet(String fieldName, int preferredFieldNumber, DocValuesType dvType) {
      if (dvType != null) {
        DocValuesType currentDVType = docValuesType.get(fieldName);
        if (currentDVType == null) {
          docValuesType.put(fieldName, dvType);
        } else if (currentDVType != null && currentDVType != dvType) {
          throw new IllegalArgumentException("cannot change DocValues type from " + currentDVType + " to " + dvType + " for field \"" + fieldName + "\"");
        }
      }
      Integer fieldNumber = nameToNumber.get(fieldName);
      if (fieldNumber == null) {
        final Integer preferredBoxed = Integer.valueOf(preferredFieldNumber);

        if (preferredFieldNumber != -1 && !numberToName.containsKey(preferredBoxed)) {
          // cool - we can use this number globally
          fieldNumber = preferredBoxed;
        } else {
          // find a new FieldNumber
          while (numberToName.containsKey(++lowestUnassignedFieldNumber)) {
            // might not be up to date - lets do the work once needed
          }
          fieldNumber = lowestUnassignedFieldNumber;
        }
        
        numberToName.put(fieldNumber, fieldName);
        nameToNumber.put(fieldName, fieldNumber);
      }

      return fieldNumber.intValue();
    }

    synchronized void verifyConsistent(Integer number, String name, DocValuesType dvType) {
      if (name.equals(numberToName.get(number)) == false) {
        throw new IllegalArgumentException("field number " + number + " is already mapped to field name \"" + numberToName.get(number) + "\", not \"" + name + "\"");
      }
      if (number.equals(nameToNumber.get(name)) == false) {
        throw new IllegalArgumentException("field name \"" + name + "\" is already mapped to field number \"" + nameToNumber.get(name) + "\", not \"" + number + "\"");
      }
      DocValuesType currentDVType = docValuesType.get(name);
      if (dvType != null && currentDVType != null && dvType != currentDVType) {
        throw new IllegalArgumentException("cannot change DocValues type from " + currentDVType + " to " + dvType + " for field \"" + name + "\"");
      }
    }

    synchronized boolean contains(String fieldName, DocValuesType dvType) {
      // used by IndexWriter.updateNumericDocValue
      if (!nameToNumber.containsKey(fieldName)) {
        return false;
      } else {
        // only return true if the field has the same dvType as the requested one
        return dvType == docValuesType.get(fieldName);
      }
    }
    
    synchronized void clear() {
      numberToName.clear();
      nameToNumber.clear();
      docValuesType.clear();
    }

    synchronized void setDocValuesType(int number, String name, DocValuesType dvType) {
      verifyConsistent(number, name, dvType);
      docValuesType.put(name, dvType);
    }
  }
  
  static final class Builder {
    private final HashMap<String,FieldInfo> byName = new HashMap<>();
    final FieldNumbers globalFieldNumbers;

    Builder() {
      this(new FieldNumbers());
    }
    
    Builder(FieldNumbers globalFieldNumbers) {
      assert globalFieldNumbers != null;
      this.globalFieldNumbers = globalFieldNumbers;
    }

    public void add(FieldInfos other) {
      for(FieldInfo fieldInfo : other){ 
        add(fieldInfo);
      }
    }
   

    public FieldInfo addOrUpdate(String name, IndexableFieldType fieldType) {
      // TODO: really, indexer shouldn't even call this
      // method (it's only called from DocFieldProcessor);
      // rather, each component in the chain should update
      // what it "owns".  EG fieldType.indexOptions() should
      // be updated by maybe FreqProxTermsWriterPerField:
      return addOrUpdateInternal(name, -1, fieldType.indexed(), false,
                                 fieldType.omitNorms(), false,
                                 fieldType.indexOptions(), null, null);
    }

    private FieldInfo addOrUpdateInternal(String name, int preferredFieldNumber, boolean isIndexed,
        boolean storeTermVector,
        boolean omitNorms, boolean storePayloads, IndexOptions indexOptions, DocValuesType docValues, DocValuesType normType) {
      FieldInfo fi = fieldInfo(name);
      if (fi == null) {
        // This field wasn't yet added to this in-RAM
        // segment's FieldInfo, so now we get a global
        // number for this field.  If the field was seen
        // before then we'll get the same name and number,
        // else we'll allocate a new one:
        final int fieldNumber = globalFieldNumbers.addOrGet(name, preferredFieldNumber, docValues);
        fi = new FieldInfo(name, isIndexed, fieldNumber, storeTermVector, omitNorms, storePayloads, indexOptions, docValues, normType, -1, null);
        assert !byName.containsKey(fi.name);
        globalFieldNumbers.verifyConsistent(Integer.valueOf(fi.number), fi.name, fi.getDocValuesType());
        byName.put(fi.name, fi);
      } else {
        fi.update(isIndexed, storeTermVector, omitNorms, storePayloads, indexOptions);

        if (docValues != null) {
          // Only pay the synchronization cost if fi does not already have a DVType
          boolean updateGlobal = !fi.hasDocValues();
          if (updateGlobal) {
            // Must also update docValuesType map so it's
            // aware of this field's DocValueType.  This will throw IllegalArgumentException if
            // an illegal type change was attempted.
            globalFieldNumbers.setDocValuesType(fi.number, name, docValues);
          }

          fi.setDocValuesType(docValues); // this will also perform the consistency check.
        }

        if (!fi.omitsNorms() && normType != null) {
          fi.setNormValueType(normType);
        }
      }
      return fi;
    }
    
    public FieldInfo add(FieldInfo fi) {
      // IMPORTANT - reuse the field number if possible for consistent field numbers across segments
      return addOrUpdateInternal(fi.name, fi.number, fi.isIndexed(), fi.hasVectors(),
                 fi.omitsNorms(), fi.hasPayloads(),
                 fi.getIndexOptions(), fi.getDocValuesType(), fi.getNormType());
    }
    
    public FieldInfo fieldInfo(String fieldName) {
      return byName.get(fieldName);
    }
    
    final FieldInfos finish() {
      return new FieldInfos(byName.values().toArray(new FieldInfo[byName.size()]));
    }
  }
}
