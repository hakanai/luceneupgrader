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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.index;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.FieldsProducer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnVectorsReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.NormsProducer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.PointsReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.StoredFieldsReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.TermVectorsReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.KnnCollector;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Bits;

/**
 * Wraps arbitrary readers for merging. Note that this can cause slow and memory-intensive merges.
 * Consider using {@link FilterCodecReader} instead.
 */
public final class SlowCodecReaderWrapper {

  /** No instantiation */
  private SlowCodecReaderWrapper() {}

  /**
   * Returns a {@code CodecReader} view of reader.
   *
   * <p>If {@code reader} is already a {@code CodecReader}, it is returned directly. Otherwise, a
   * (slow) view is returned.
   */
  public static CodecReader wrap(final LeafReader reader) throws IOException {
    if (reader instanceof CodecReader) {
      return (CodecReader) reader;
    } else {
      // simulate it slowly, over the leafReader api:
      reader.checkIntegrity();
      return new CodecReader() {

        @Override
        public TermVectorsReader getTermVectorsReader() {
          reader.ensureOpen();
          return readerToTermVectorsReader(reader);
        }

        @Override
        public StoredFieldsReader getFieldsReader() {
          reader.ensureOpen();
          return readerToStoredFieldsReader(reader);
        }

        @Override
        public NormsProducer getNormsReader() {
          reader.ensureOpen();
          return readerToNormsProducer(reader);
        }

        @Override
        public DocValuesProducer getDocValuesReader() {
          reader.ensureOpen();
          return readerToDocValuesProducer(reader);
        }

        @Override
        public KnnVectorsReader getVectorReader() {
          reader.ensureOpen();
          return readerToVectorReader(reader);
        }

        @Override
        public FieldsProducer getPostingsReader() {
          reader.ensureOpen();
          try {
            return readerToFieldsProducer(reader);
          } catch (IOException bogus) {
            throw new AssertionError(bogus);
          }
        }

        @Override
        public FieldInfos getFieldInfos() {
          return reader.getFieldInfos();
        }

        @Override
        public PointsReader getPointsReader() {
          return pointValuesToReader(reader);
        }

        @Override
        public Bits getLiveDocs() {
          return reader.getLiveDocs();
        }

        @Override
        public int numDocs() {
          return reader.numDocs();
        }

        @Override
        public int maxDoc() {
          return reader.maxDoc();
        }

        @Override
        public CacheHelper getCoreCacheHelper() {
          return reader.getCoreCacheHelper();
        }

        @Override
        public CacheHelper getReaderCacheHelper() {
          return reader.getReaderCacheHelper();
        }

        @Override
        public String toString() {
          return "SlowCodecReaderWrapper(" + reader + ")";
        }

        @Override
        public LeafMetaData getMetaData() {
          return reader.getMetaData();
        }
      };
    }
  }

  private static PointsReader pointValuesToReader(LeafReader reader) {
    return new PointsReader() {

      @Override
      public PointValues getValues(String field) throws IOException {
        return reader.getPointValues(field);
      }

      @Override
      public void checkIntegrity() throws IOException {
        // We already checkIntegrity the entire reader up front
      }

      @Override
      public void close() {}
    };
  }

  private static KnnVectorsReader readerToVectorReader(LeafReader reader) {
    return new KnnVectorsReader() {
      @Override
      public FloatVectorValues getFloatVectorValues(String field) throws IOException {
        return reader.getFloatVectorValues(field);
      }

      @Override
      public ByteVectorValues getByteVectorValues(String field) throws IOException {
        return reader.getByteVectorValues(field);
      }

      @Override
      public void search(String field, float[] target, KnnCollector knnCollector, Bits acceptDocs)
          throws IOException {
        reader.searchNearestVectors(field, target, knnCollector, acceptDocs);
      }

      @Override
      public void search(String field, byte[] target, KnnCollector knnCollector, Bits acceptDocs)
          throws IOException {
        reader.searchNearestVectors(field, target, knnCollector, acceptDocs);
      }

      @Override
      public void checkIntegrity() {
        // We already checkIntegrity the entire reader up front
      }

      @Override
      public void close() {}

      @Override
      public long ramBytesUsed() {
        return 0L;
      }
    };
  }

  private static NormsProducer readerToNormsProducer(final LeafReader reader) {
    return new NormsProducer() {

      @Override
      public NumericDocValues getNorms(FieldInfo field) throws IOException {
        return reader.getNormValues(field.name);
      }

      @Override
      public void checkIntegrity() throws IOException {
        // We already checkIntegrity the entire reader up front
      }

      @Override
      public void close() {}
    };
  }

  private static DocValuesProducer readerToDocValuesProducer(final LeafReader reader) {
    return new DocValuesProducer() {

      @Override
      public NumericDocValues getNumeric(FieldInfo field) throws IOException {
        return reader.getNumericDocValues(field.name);
      }

      @Override
      public BinaryDocValues getBinary(FieldInfo field) throws IOException {
        return reader.getBinaryDocValues(field.name);
      }

      @Override
      public SortedDocValues getSorted(FieldInfo field) throws IOException {
        return reader.getSortedDocValues(field.name);
      }

      @Override
      public SortedNumericDocValues getSortedNumeric(FieldInfo field) throws IOException {
        return reader.getSortedNumericDocValues(field.name);
      }

      @Override
      public SortedSetDocValues getSortedSet(FieldInfo field) throws IOException {
        return reader.getSortedSetDocValues(field.name);
      }

      @Override
      public void checkIntegrity() throws IOException {
        // We already checkIntegrity the entire reader up front
      }

      @Override
      public void close() {}
    };
  }

  private static StoredFieldsReader readerToStoredFieldsReader(final LeafReader reader) {
    final StoredFields storedFields;
    try {
      storedFields = reader.storedFields();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new StoredFieldsReader() {
      @Override
      public void document(int docID, StoredFieldVisitor visitor) throws IOException {
        storedFields.document(docID, visitor);
      }

      @Override
      public StoredFieldsReader clone() {
        return readerToStoredFieldsReader(reader);
      }

      @Override
      public void checkIntegrity() throws IOException {
        // We already checkIntegrity the entire reader up front
      }

      @Override
      public void close() {}
    };
  }

  private static TermVectorsReader readerToTermVectorsReader(final LeafReader reader) {
    final TermVectors termVectors;
    try {
      termVectors = reader.termVectors();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new TermVectorsReader() {
      @Override
      public Fields get(int docID) throws IOException {
        return termVectors.get(docID);
      }

      @Override
      public TermVectorsReader clone() {
        return readerToTermVectorsReader(reader);
      }

      @Override
      public void checkIntegrity() throws IOException {
        // We already checkIntegrity the entire reader up front
      }

      @Override
      public void close() {}
    };
  }

  private static FieldsProducer readerToFieldsProducer(final LeafReader reader) throws IOException {
    ArrayList<String> indexedFields = new ArrayList<>();
    for (FieldInfo fieldInfo : reader.getFieldInfos()) {
      if (fieldInfo.getIndexOptions() != IndexOptions.NONE) {
        indexedFields.add(fieldInfo.name);
      }
    }
    Collections.sort(indexedFields);
    return new FieldsProducer() {
      @Override
      public Iterator<String> iterator() {
        return indexedFields.iterator();
      }

      @Override
      public Terms terms(String field) throws IOException {
        return reader.terms(field);
      }

      @Override
      public int size() {
        return indexedFields.size();
      }

      @Override
      public void checkIntegrity() throws IOException {
        // We already checkIntegrity the entire reader up front
      }

      @Override
      public void close() {}
    };
  }
}
