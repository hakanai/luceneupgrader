package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

import java.io.IOException;
import java.util.List;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Fieldable;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.NumericField;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.RAMOutputStream;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.IOUtils;

final class FieldsWriter {
  static final int FIELD_IS_TOKENIZED = 1 << 0;
  static final int FIELD_IS_BINARY = 1 << 1;

  @Deprecated
  static final int FIELD_IS_COMPRESSED = 1 << 2;

  private static final int _NUMERIC_BIT_SHIFT = 3;
  static final int FIELD_IS_NUMERIC_MASK = 0x07 << _NUMERIC_BIT_SHIFT;

  static final int FIELD_IS_NUMERIC_INT = 1 << _NUMERIC_BIT_SHIFT;
  static final int FIELD_IS_NUMERIC_LONG = 2 << _NUMERIC_BIT_SHIFT;
  static final int FIELD_IS_NUMERIC_FLOAT = 3 << _NUMERIC_BIT_SHIFT;
  static final int FIELD_IS_NUMERIC_DOUBLE = 4 << _NUMERIC_BIT_SHIFT;
  // currently unused: static final int FIELD_IS_NUMERIC_SHORT = 5 << _NUMERIC_BIT_SHIFT;
  // currently unused: static final int FIELD_IS_NUMERIC_BYTE = 6 << _NUMERIC_BIT_SHIFT;

  // the next possible bits are: 1 << 6; 1 << 7
  
  // Original format
  static final int FORMAT = 0;

  // Changed strings to UTF8
  static final int FORMAT_VERSION_UTF8_LENGTH_IN_BYTES = 1;
  
  // Lucene 3.0: Removal of compressed fields
  static final int FORMAT_LUCENE_3_0_NO_COMPRESSED_FIELDS = 2;

  // Lucene 3.2: NumericFields are stored in binary format
  static final int FORMAT_LUCENE_3_2_NUMERIC_FIELDS = 3;

  // NOTE: if you introduce a new format, make it 1 higher
  // than the current one, and always change this if you
  // switch to a new format!
  static final int FORMAT_CURRENT = FORMAT_LUCENE_3_2_NUMERIC_FIELDS;
  
  private FieldInfos fieldInfos;

  // If null - we were supplied with streams, if notnull - we manage them ourselves
  private Directory directory;
  private String segment;
  private IndexOutput fieldsStream;
  private IndexOutput indexStream;

  FieldsWriter(Directory directory, String segment, FieldInfos fn) throws IOException {
    this.directory = directory;
    this.segment = segment;
    fieldInfos = fn;

    boolean success = false;
    try {
      fieldsStream = directory.createOutput(IndexFileNames.segmentFileName(segment, IndexFileNames.FIELDS_EXTENSION));
      indexStream = directory.createOutput(IndexFileNames.segmentFileName(segment, IndexFileNames.FIELDS_INDEX_EXTENSION));

      fieldsStream.writeInt(FORMAT_CURRENT);
      indexStream.writeInt(FORMAT_CURRENT);

      success = true;
    } finally {
      if (!success) {
        abort();
      }
    }
  }

  FieldsWriter(IndexOutput fdx, IndexOutput fdt, FieldInfos fn) {
    directory = null;
    segment = null;
    fieldInfos = fn;
    fieldsStream = fdt;
    indexStream = fdx;
  }

  void setFieldsStream(IndexOutput stream) {
    this.fieldsStream = stream;
  }

  // Writes the contents of buffer into the fields stream
  // and adds a new entry for this document into the index
  // stream.  This assumes the buffer was already written
  // in the correct fields format.
  void flushDocument(int numStoredFields, RAMOutputStream buffer) throws IOException {
    indexStream.writeLong(fieldsStream.getFilePointer());
    fieldsStream.writeVInt(numStoredFields);
    buffer.writeTo(fieldsStream);
  }

  void skipDocument() throws IOException {
    indexStream.writeLong(fieldsStream.getFilePointer());
    fieldsStream.writeVInt(0);
  }

  void finish(int numDocs) throws IOException {
    if (4 + ((long) numDocs) * 8 != indexStream.getFilePointer()) {
      // This is most likely a bug in Sun JRE 1.6.0_04/_05;
      // we detect that the bug has struck, here, and
      // throw an exception to prevent the corruption from
      // entering the index.  See LUCENE-1282 for
      // details
      String fieldsIdxName = IndexFileNames.segmentFileName(segment,  IndexFileNames.FIELDS_INDEX_EXTENSION);
      throw new RuntimeException("fdx size mismatch: " + numDocs + " docs vs " + indexStream.getFilePointer() + " length in bytes of " + fieldsIdxName + " file exists?=" + directory.fileExists(fieldsIdxName));
    }
  }

  void close() throws IOException {
    if (directory != null) {
      try {
        IOUtils.close(fieldsStream, indexStream);
      } finally {
        fieldsStream = indexStream = null;
      }
    }
  }

  void abort() {
    if (directory != null) {
      try {
        close();
      } catch (IOException ignored) {
      }
      try {
        directory.deleteFile(IndexFileNames.segmentFileName(segment, IndexFileNames.FIELDS_EXTENSION));
      } catch (IOException ignored) {
      }
      try {
        directory.deleteFile(IndexFileNames.segmentFileName(segment, IndexFileNames.FIELDS_INDEX_EXTENSION));
      } catch (IOException ignored) {
      }
    }
  }

  final void writeField(FieldInfo fi, Fieldable field) throws IOException {
    fieldsStream.writeVInt(fi.number);
    int bits = 0;
    if (field.isTokenized())
      bits |= FIELD_IS_TOKENIZED;
    if (field.isBinary())
      bits |= FIELD_IS_BINARY;
    if (field instanceof NumericField) {
      switch (((NumericField) field).getDataType()) {
        case INT:
          bits |= FIELD_IS_NUMERIC_INT; break;
        case LONG:
          bits |= FIELD_IS_NUMERIC_LONG; break;
        case FLOAT:
          bits |= FIELD_IS_NUMERIC_FLOAT; break;
        case DOUBLE:
          bits |= FIELD_IS_NUMERIC_DOUBLE; break;
        default:
          assert false : "Should never get here";
      }
    }
    fieldsStream.writeByte((byte) bits);

    if (field.isBinary()) {
      final byte[] data;
      final int len;
      final int offset;
      data = field.getBinaryValue();
      len = field.getBinaryLength();
      offset =  field.getBinaryOffset();

      fieldsStream.writeVInt(len);
      fieldsStream.writeBytes(data, offset, len);
    } else if (field instanceof NumericField) {
      final NumericField nf = (NumericField) field;
      final Number n = nf.getNumericValue();
      switch (nf.getDataType()) {
        case INT:
          fieldsStream.writeInt(n.intValue()); break;
        case LONG:
          fieldsStream.writeLong(n.longValue()); break;
        case FLOAT:
          fieldsStream.writeInt(Float.floatToIntBits(n.floatValue())); break;
        case DOUBLE:
          fieldsStream.writeLong(Double.doubleToLongBits(n.doubleValue())); break;
        default:
          assert false : "Should never get here";
      }
    } else {
      fieldsStream.writeString(field.stringValue());
    }
  }


  final void addRawDocuments(IndexInput stream, int[] lengths, int numDocs) throws IOException {
    long position = fieldsStream.getFilePointer();
    long start = position;
    for(int i=0;i<numDocs;i++) {
      indexStream.writeLong(position);
      position += lengths[i];
    }
    fieldsStream.copyBytes(stream, position-start);
    assert fieldsStream.getFilePointer() == position;
  }

  final void addDocument(Document doc) throws IOException {
    indexStream.writeLong(fieldsStream.getFilePointer());

    int storedCount = 0;
    List<Fieldable> fields = doc.getFields();
    for (Fieldable field : fields) {
      if (field.isStored())
          storedCount++;
    }
    fieldsStream.writeVInt(storedCount);



    for (Fieldable field : fields) {
      if (field.isStored())
        writeField(fieldInfos.fieldInfo(field.name()), field);
    }
  }
}
