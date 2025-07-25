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
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.KnnCollector;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.ScoreDoc;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.TopDocs;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.TopDocsCollector;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.TopKnnCollector;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.TotalHits;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Bits;

/**
 * {@code LeafReader} is an abstract class, providing an interface for accessing an index. Search of
 * an index is done entirely through this abstract interface, so that any subclass which implements
 * it is searchable. IndexReaders implemented by this subclass do not consist of several
 * sub-readers, they are atomic. They support retrieval of stored fields, doc values, terms, and
 * postings.
 *
 * <p>For efficiency, in this API documents are often referred to via <i>document numbers</i>,
 * non-negative integers which each name a unique document in the index. These document numbers are
 * ephemeral -- they may change as documents are added to and deleted from an index. Clients should
 * thus not rely on a given document having the same number between sessions.
 *
 * <p><a id="thread-safety"></a>
 *
 * <p><b>NOTE</b>: {@link IndexReader} instances are completely thread safe, meaning multiple
 * threads can call any of its methods, concurrently. If your application requires external
 * synchronization, you should <b>not</b> synchronize on the <code>IndexReader</code> instance; use
 * your own (non-Lucene) objects instead.
 */
public abstract class LeafReader extends IndexReader {

  private final LeafReaderContext readerContext = new LeafReaderContext(this);

  /** Sole constructor. (For invocation by subclass constructors, typically implicit.) */
  protected LeafReader() {
    super();
  }

  @Override
  public final LeafReaderContext getContext() {
    ensureOpen();
    return readerContext;
  }

  /**
   * Optional method: Return a {@link IndexReader.CacheHelper} that can be used to cache based on
   * the content of this leaf regardless of deletions. Two readers that have the same data but
   * different sets of deleted documents or doc values updates may be considered equal. Consider
   * using {@link #getReaderCacheHelper} if you need deletions or dv updates to be taken into
   * account.
   *
   * <p>A return value of {@code null} indicates that this reader is not suited for caching, which
   * is typically the case for short-lived wrappers that alter the content of the wrapped leaf
   * reader.
   *
   * @lucene.experimental
   */
  public abstract CacheHelper getCoreCacheHelper();

  @Override
  public final int docFreq(Term term) throws IOException {
    final Terms terms = Terms.getTerms(this, term.field());
    final TermsEnum termsEnum = terms.iterator();
    if (termsEnum.seekExact(term.bytes())) {
      return termsEnum.docFreq();
    } else {
      return 0;
    }
  }

  /**
   * Returns the number of documents containing the term <code>t</code>. This method returns 0 if
   * the term or field does not exists. This method does not take into account deleted documents
   * that have not yet been merged away.
   */
  @Override
  public final long totalTermFreq(Term term) throws IOException {
    final Terms terms = Terms.getTerms(this, term.field());
    final TermsEnum termsEnum = terms.iterator();
    if (termsEnum.seekExact(term.bytes())) {
      return termsEnum.totalTermFreq();
    } else {
      return 0;
    }
  }

  @Override
  public final long getSumDocFreq(String field) throws IOException {
    final Terms terms = terms(field);
    if (terms == null) {
      return 0;
    }
    return terms.getSumDocFreq();
  }

  @Override
  public final int getDocCount(String field) throws IOException {
    final Terms terms = terms(field);
    if (terms == null) {
      return 0;
    }
    return terms.getDocCount();
  }

  @Override
  public final long getSumTotalTermFreq(String field) throws IOException {
    final Terms terms = terms(field);
    if (terms == null) {
      return 0;
    }
    return terms.getSumTotalTermFreq();
  }

  /** Returns the {@link Terms} index for this field, or null if it has none. */
  public abstract Terms terms(String field) throws IOException;

  /**
   * Returns {@link PostingsEnum} for the specified term. This will return null if either the field
   * or term does not exist.
   *
   * <p><b>NOTE:</b> The returned {@link PostingsEnum} may contain deleted docs.
   *
   * @see TermsEnum#postings(PostingsEnum)
   */
  public final PostingsEnum postings(Term term, int flags) throws IOException {
    assert term.field() != null;
    assert term.bytes() != null;
    final Terms terms = Terms.getTerms(this, term.field());
    final TermsEnum termsEnum = terms.iterator();
    if (termsEnum.seekExact(term.bytes())) {
      return termsEnum.postings(null, flags);
    }
    return null;
  }

  /**
   * Returns {@link PostingsEnum} for the specified term with {@link PostingsEnum#FREQS}.
   *
   * <p>Use this method if you only require documents and frequencies, and do not need any proximity
   * data. This method is equivalent to {@link #postings(Term, int) postings(term,
   * PostingsEnum.FREQS)}
   *
   * <p><b>NOTE:</b> The returned {@link PostingsEnum} may contain deleted docs.
   *
   * @see #postings(Term, int)
   */
  public final PostingsEnum postings(Term term) throws IOException {
    return postings(term, PostingsEnum.FREQS);
  }

  /**
   * Returns {@link NumericDocValues} for this field, or null if no numeric doc values were indexed
   * for this field. The returned instance should only be used by a single thread.
   */
  public abstract NumericDocValues getNumericDocValues(String field) throws IOException;

  /**
   * Returns {@link BinaryDocValues} for this field, or null if no binary doc values were indexed
   * for this field. The returned instance should only be used by a single thread.
   */
  public abstract BinaryDocValues getBinaryDocValues(String field) throws IOException;

  /**
   * Returns {@link SortedDocValues} for this field, or null if no {@link SortedDocValues} were
   * indexed for this field. The returned instance should only be used by a single thread.
   */
  public abstract SortedDocValues getSortedDocValues(String field) throws IOException;

  /**
   * Returns {@link SortedNumericDocValues} for this field, or null if no {@link
   * SortedNumericDocValues} were indexed for this field. The returned instance should only be used
   * by a single thread.
   */
  public abstract SortedNumericDocValues getSortedNumericDocValues(String field) throws IOException;

  /**
   * Returns {@link SortedSetDocValues} for this field, or null if no {@link SortedSetDocValues}
   * were indexed for this field. The returned instance should only be used by a single thread.
   */
  public abstract SortedSetDocValues getSortedSetDocValues(String field) throws IOException;

  /**
   * Returns {@link NumericDocValues} representing norms for this field, or null if no {@link
   * NumericDocValues} were indexed. The returned instance should only be used by a single thread.
   */
  public abstract NumericDocValues getNormValues(String field) throws IOException;

  /**
   * Returns {@link FloatVectorValues} for this field, or null if no {@link FloatVectorValues} were
   * indexed. The returned instance should only be used by a single thread.
   *
   * @lucene.experimental
   */
  public abstract FloatVectorValues getFloatVectorValues(String field) throws IOException;

  /**
   * Returns {@link ByteVectorValues} for this field, or null if no {@link ByteVectorValues} were
   * indexed. The returned instance should only be used by a single thread.
   *
   * @lucene.experimental
   */
  public abstract ByteVectorValues getByteVectorValues(String field) throws IOException;

  /**
   * Return the k nearest neighbor documents as determined by comparison of their vector values for
   * this field, to the given vector, by the field's similarity function. The score of each document
   * is derived from the vector similarity in a way that ensures scores are positive and that a
   * larger score corresponds to a higher ranking.
   *
   * <p>The search is allowed to be approximate, meaning the results are not guaranteed to be the
   * true k closest neighbors. For large values of k (for example when k is close to the total
   * number of documents), the search may also retrieve fewer than k documents.
   *
   * <p>The returned {@link TopDocs} will contain a {@link ScoreDoc} for each nearest neighbor,
   * sorted in order of their similarity to the query vector (decreasing scores). The {@link
   * TotalHits} contains the number of documents visited during the search. If the search stopped
   * early because it hit {@code visitedLimit}, it is indicated through the relation {@code
   * TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO}.
   *
   * @param field the vector field to search
   * @param target the vector-valued query
   * @param k the number of docs to return
   * @param acceptDocs {@link Bits} that represents the allowed documents to match, or {@code null}
   *     if they are all allowed to match.
   * @param visitedLimit the maximum number of nodes that the search is allowed to visit
   * @return the k nearest neighbor documents, along with their (searchStrategy-specific) scores.
   * @lucene.experimental
   */
  public final TopDocs searchNearestVectors(
      String field, float[] target, int k, Bits acceptDocs, int visitedLimit) throws IOException {
    FieldInfo fi = getFieldInfos().fieldInfo(field);
    if (fi == null || fi.getVectorDimension() == 0) {
      return TopDocsCollector.EMPTY_TOPDOCS;
    }
    FloatVectorValues floatVectorValues = getFloatVectorValues(fi.name);
    if (floatVectorValues == null) {
      return TopDocsCollector.EMPTY_TOPDOCS;
    }
    k = Math.min(k, floatVectorValues.size());
    if (k == 0) {
      return TopDocsCollector.EMPTY_TOPDOCS;
    }
    KnnCollector collector = new TopKnnCollector(k, visitedLimit);
    searchNearestVectors(field, target, collector, acceptDocs);
    return collector.topDocs();
  }

  /**
   * Return the k nearest neighbor documents as determined by comparison of their vector values for
   * this field, to the given vector, by the field's similarity function. The score of each document
   * is derived from the vector similarity in a way that ensures scores are positive and that a
   * larger score corresponds to a higher ranking.
   *
   * <p>The search is allowed to be approximate, meaning the results are not guaranteed to be the
   * true k closest neighbors. For large values of k (for example when k is close to the total
   * number of documents), the search may also retrieve fewer than k documents.
   *
   * <p>The returned {@link TopDocs} will contain a {@link ScoreDoc} for each nearest neighbor,
   * sorted in order of their similarity to the query vector (decreasing scores). The {@link
   * TotalHits} contains the number of documents visited during the search. If the search stopped
   * early because it hit {@code visitedLimit}, it is indicated through the relation {@code
   * TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO}.
   *
   * @param field the vector field to search
   * @param target the vector-valued query
   * @param k the number of docs to return
   * @param acceptDocs {@link Bits} that represents the allowed documents to match, or {@code null}
   *     if they are all allowed to match.
   * @param visitedLimit the maximum number of nodes that the search is allowed to visit
   * @return the k nearest neighbor documents, along with their (searchStrategy-specific) scores.
   * @lucene.experimental
   */
  public final TopDocs searchNearestVectors(
      String field, byte[] target, int k, Bits acceptDocs, int visitedLimit) throws IOException {
    FieldInfo fi = getFieldInfos().fieldInfo(field);
    if (fi == null || fi.getVectorDimension() == 0) {
      return TopDocsCollector.EMPTY_TOPDOCS;
    }
    ByteVectorValues byteVectorValues = getByteVectorValues(fi.name);
    if (byteVectorValues == null) {
      return TopDocsCollector.EMPTY_TOPDOCS;
    }
    k = Math.min(k, byteVectorValues.size());
    if (k == 0) {
      return TopDocsCollector.EMPTY_TOPDOCS;
    }
    KnnCollector collector = new TopKnnCollector(k, visitedLimit);
    searchNearestVectors(field, target, collector, acceptDocs);
    return collector.topDocs();
  }

  /**
   * Return the k nearest neighbor documents as determined by comparison of their vector values for
   * this field, to the given vector, by the field's similarity function. The score of each document
   * is derived from the vector similarity in a way that ensures scores are positive and that a
   * larger score corresponds to a higher ranking.
   *
   * <p>The search is allowed to be approximate, meaning the results are not guaranteed to be the
   * true k closest neighbors. For large values of k (for example when k is close to the total
   * number of documents), the search may also retrieve fewer than k documents.
   *
   * <p>The returned {@link TopDocs} will contain a {@link ScoreDoc} for each nearest neighbor, in
   * order of their similarity to the query vector (decreasing scores). The {@link TotalHits}
   * contains the number of documents visited during the search. If the search stopped early because
   * it hit {@code visitedLimit}, it is indicated through the relation {@code
   * TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO}.
   *
   * <p>The behavior is undefined if the given field doesn't have KNN vectors enabled on its {@link
   * FieldInfo}. The return value is never {@code null}.
   *
   * @param field the vector field to search
   * @param target the vector-valued query
   * @param knnCollector collector with settings for gathering the vector results.
   * @param acceptDocs {@link Bits} that represents the allowed documents to match, or {@code null}
   *     if they are all allowed to match.
   * @lucene.experimental
   */
  public abstract void searchNearestVectors(
      String field, float[] target, KnnCollector knnCollector, Bits acceptDocs) throws IOException;

  /**
   * Return the k nearest neighbor documents as determined by comparison of their vector values for
   * this field, to the given vector, by the field's similarity function. The score of each document
   * is derived from the vector similarity in a way that ensures scores are positive and that a
   * larger score corresponds to a higher ranking.
   *
   * <p>The search is allowed to be approximate, meaning the results are not guaranteed to be the
   * true k closest neighbors. For large values of k (for example when k is close to the total
   * number of documents), the search may also retrieve fewer than k documents.
   *
   * <p>The returned {@link TopDocs} will contain a {@link ScoreDoc} for each nearest neighbor, in
   * order of their similarity to the query vector (decreasing scores). The {@link TotalHits}
   * contains the number of documents visited during the search. If the search stopped early because
   * it hit {@code visitedLimit}, it is indicated through the relation {@code
   * TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO}.
   *
   * <p>The behavior is undefined if the given field doesn't have KNN vectors enabled on its {@link
   * FieldInfo}. The return value is never {@code null}.
   *
   * @param field the vector field to search
   * @param target the vector-valued query
   * @param knnCollector collector with settings for gathering the vector results.
   * @param acceptDocs {@link Bits} that represents the allowed documents to match, or {@code null}
   *     if they are all allowed to match.
   * @lucene.experimental
   */
  public abstract void searchNearestVectors(
      String field, byte[] target, KnnCollector knnCollector, Bits acceptDocs) throws IOException;

  /**
   * Get the {@link FieldInfos} describing all fields in this reader.
   *
   * <p>Note: Implementations should cache the FieldInfos instance returned by this method such that
   * subsequent calls to this method return the same instance.
   *
   * @lucene.experimental
   */
  public abstract FieldInfos getFieldInfos();

  /**
   * Returns the {@link Bits} representing live (not deleted) docs. A set bit indicates the doc ID
   * has not been deleted. If this method returns null it means there are no deleted documents (all
   * documents are live).
   *
   * <p>The returned instance has been safely published for use by multiple threads without
   * additional synchronization.
   */
  public abstract Bits getLiveDocs();

  /**
   * Returns the {@link PointValues} used for numeric or spatial searches for the given field, or
   * null if there are no point fields.
   */
  public abstract PointValues getPointValues(String field) throws IOException;

  /**
   * Checks consistency of this reader.
   *
   * <p>Note that this may be costly in terms of I/O, e.g. may involve computing a checksum value
   * against large data files.
   *
   * @lucene.internal
   */
  public abstract void checkIntegrity() throws IOException;

  /**
   * Return metadata about this leaf.
   *
   * @lucene.experimental
   */
  public abstract LeafMetaData getMetaData();
}
