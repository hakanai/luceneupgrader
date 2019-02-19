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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Fieldable;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.FieldInfo.IndexOptions;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.StringHelper;

import java.io.IOException;
import java.util.*;

public final class FieldInfos implements Iterable<FieldInfo> {

  // Used internally (ie not written to *.fnm files) for pre-2.9 files
  public static final int FORMAT_PRE = -1;

  // First used in 2.9; prior to 2.9 there was no format header
  public static final int FORMAT_START = -2;

  // First used in 3.4: omit only positional information
  public static final int FORMAT_OMIT_POSITIONS = -3;

  // whenever you add a new format, make it 1 smaller (negative version logic)!
  static final int CURRENT_FORMAT = FORMAT_OMIT_POSITIONS;
  
  static final byte IS_INDEXED = 0x1;
  static final byte STORE_TERMVECTOR = 0x2;
  static final byte OMIT_NORMS = 0x10;
  static final byte STORE_PAYLOADS = 0x20;
  static final byte OMIT_TERM_FREQ_AND_POSITIONS = 0x40;
  static final byte OMIT_POSITIONS = -128;

  private final ArrayList<FieldInfo> byNumber = new ArrayList<FieldInfo>();
  private final HashMap<String,FieldInfo> byName = new HashMap<String,FieldInfo>();
  private int format;

  public FieldInfos() { }

  public FieldInfos(Directory d, String name) throws IOException {
    IndexInput input = d.openInput(name);
    try {
      try {
        read(input, name);
      } catch (IOException ioe) {
        if (format == FORMAT_PRE) {
          // LUCENE-1623: FORMAT_PRE (before there was a
          // format) may be 2.3.2 (pre-utf8) or 2.4.x (utf8)
          // encoding; retry with input set to pre-utf8
          input.seek(0);
          input.setModifiedUTF8StringsMode();
          byNumber.clear();
          byName.clear();
          try {
            read(input, name);
          } catch (Throwable t) {
            // Ignore any new exception & throw original IOE
            throw ioe;
          }
        } else {
          // The IOException cannot be caused by
          // LUCENE-1623, so re-throw it
          throw ioe;
        }
      }
    } finally {
      input.close();
    }
  }

  public void add(FieldInfos other) {
    for(FieldInfo fieldInfo : other){ 
      add(fieldInfo);
    }
  }

  @Override
  synchronized public Object clone() {
    FieldInfos fis = new FieldInfos();
    final int numField = byNumber.size();
    for(int i=0;i<numField;i++) {
      FieldInfo fi = (FieldInfo) ( byNumber.get(i)).clone();
      fis.byNumber.add(fi);
      fis.byName.put(fi.name, fi);
    }
    return fis;
  }

  synchronized public void add(Document doc) {
    List<Fieldable> fields = doc.getFields();
    for (Fieldable field : fields) {
      add(field.name(), field.isIndexed(), field.isTermVectorStored(),
          field.getOmitNorms(), false, field.getIndexOptions());
    }
  }

  public boolean hasProx() {
    final int numFields = byNumber.size();
    for(int i=0;i<numFields;i++) {
      final FieldInfo fi = fieldInfo(i);
      if (fi.isIndexed && fi.indexOptions == IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) {
        return true;
      }
    }
    return false;
  }
  
  synchronized public void add(String name, boolean isIndexed) {
    add(name, isIndexed, false, false);
  }

  synchronized public void add(String name, boolean isIndexed, boolean storeTermVector){
    add(name, isIndexed, storeTermVector, false);
  }
  

  synchronized public void add(String name, boolean isIndexed, boolean storeTermVector,
                               boolean omitNorms) {
    add(name, isIndexed, storeTermVector,
        omitNorms, false, IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
  }
  

  synchronized public FieldInfo add(String name, boolean isIndexed, boolean storeTermVector,
                       boolean omitNorms, boolean storePayloads, IndexOptions indexOptions) {
    FieldInfo fi = fieldInfo(name);
    if (fi == null) {
      return addInternal(name, isIndexed, storeTermVector, omitNorms, storePayloads, indexOptions);
    } else {
      fi.update(isIndexed, storeTermVector, omitNorms, storePayloads, indexOptions);
    }
    assert fi.indexOptions == IndexOptions.DOCS_AND_FREQS_AND_POSITIONS || !fi.storePayloads;
    return fi;
  }

  synchronized public FieldInfo add(FieldInfo fi) {
    return add(fi.name, fi.isIndexed, fi.storeTermVector,
               fi.omitNorms, fi.storePayloads,
               fi.indexOptions);
  }

  private FieldInfo addInternal(String name, boolean isIndexed,
                                boolean storeTermVector, boolean omitNorms, boolean storePayloads, IndexOptions indexOptions) {
    name = StringHelper.intern(name);
    FieldInfo fi = new FieldInfo(name, isIndexed, byNumber.size(), storeTermVector,
                                 omitNorms, storePayloads, indexOptions);
    byNumber.add(fi);
    byName.put(name, fi);
    return fi;
  }

  public int fieldNumber(String fieldName) {
    FieldInfo fi = fieldInfo(fieldName);
    return (fi != null) ? fi.number : -1;
  }

  public FieldInfo fieldInfo(String fieldName) {
    return byName.get(fieldName);
  }

  public String fieldName(int fieldNumber) {
	FieldInfo fi = fieldInfo(fieldNumber);
	return (fi != null) ? fi.name : "";
  }

  public FieldInfo fieldInfo(int fieldNumber) {
    return (fieldNumber >= 0) ? byNumber.get(fieldNumber) : null;
  }

  public Iterator<FieldInfo> iterator() {
    return byNumber.iterator();
  }

  public int size() {
    return byNumber.size();
  }

  public boolean hasVectors() {
    boolean hasVectors = false;
    for (int i = 0; i < size(); i++) {
      if (fieldInfo(i).storeTermVector) {
        hasVectors = true;
        break;
      }
    }
    return hasVectors;
  }

  public void write(Directory d, String name) throws IOException {
    IndexOutput output = d.createOutput(name);
    try {
      write(output);
    } finally {
      output.close();
    }
  }

  public void write(IndexOutput output) throws IOException {
    output.writeVInt(CURRENT_FORMAT);
    output.writeVInt(size());
    for (int i = 0; i < size(); i++) {
      FieldInfo fi = fieldInfo(i);
      assert fi.indexOptions == IndexOptions.DOCS_AND_FREQS_AND_POSITIONS || !fi.storePayloads;
      byte bits = 0x0;
      if (fi.isIndexed) bits |= IS_INDEXED;
      if (fi.storeTermVector) bits |= STORE_TERMVECTOR;
      if (fi.omitNorms) bits |= OMIT_NORMS;
      if (fi.storePayloads) bits |= STORE_PAYLOADS;
      if (fi.indexOptions == IndexOptions.DOCS_ONLY) {
        bits |= OMIT_TERM_FREQ_AND_POSITIONS;
      } else if (fi.indexOptions == IndexOptions.DOCS_AND_FREQS) {
        bits |= OMIT_POSITIONS;
      }
      output.writeString(fi.name);
      output.writeByte(bits);
    }
  }

  private void read(IndexInput input, String fileName) throws IOException {
    int firstInt = input.readVInt();

    if (firstInt < 0) {
      // This is a real format
      format = firstInt;
    } else {
      format = FORMAT_PRE;
    }

    if (format != FORMAT_PRE && format != FORMAT_START && format != FORMAT_OMIT_POSITIONS) {
      throw new CorruptIndexException("unrecognized format " + format + " in file \"" + fileName + "\"");
    }

    int size;
    if (format == FORMAT_PRE) {
      size = firstInt;
    } else {
      size = input.readVInt(); //read in the size
    }

    for (int i = 0; i < size; i++) {
      String name = StringHelper.intern(input.readString());
      byte bits = input.readByte();
      boolean isIndexed = (bits & IS_INDEXED) != 0;
      boolean storeTermVector = (bits & STORE_TERMVECTOR) != 0;
      boolean omitNorms = (bits & OMIT_NORMS) != 0;
      boolean storePayloads = (bits & STORE_PAYLOADS) != 0;
      final IndexOptions indexOptions;
      if ((bits & OMIT_TERM_FREQ_AND_POSITIONS) != 0) {
        indexOptions = IndexOptions.DOCS_ONLY;
      } else if ((bits & OMIT_POSITIONS) != 0) {
        if (format <= FORMAT_OMIT_POSITIONS) {
          indexOptions = IndexOptions.DOCS_AND_FREQS;
        } else {
          throw new CorruptIndexException("Corrupt fieldinfos, OMIT_POSITIONS set but format=" + format + " (resource: " + input + ")");
        }
      } else {
        indexOptions = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;
      }
      
      // LUCENE-3027: past indices were able to write
      // storePayloads=true when omitTFAP is also true,
      // which is invalid.  We correct that, here:
      if (indexOptions != IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) {
        storePayloads = false;
      }

      addInternal(name, isIndexed, storeTermVector, omitNorms, storePayloads, indexOptions);
    }

    if (input.getFilePointer() != input.length()) {
      throw new CorruptIndexException("did not read all bytes from file \"" + fileName + "\": read " + input.getFilePointer() + " vs size " + input.length() + " (resource: " + input + ")");
    }    
  }

}
