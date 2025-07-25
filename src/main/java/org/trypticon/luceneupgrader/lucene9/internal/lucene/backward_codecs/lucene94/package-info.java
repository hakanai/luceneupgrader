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

/**
 * Lucene 9.4 file format.
 *
 * <h2>Apache Lucene - Index File Formats</h2>
 *
 * <div>
 *
 * <ul>
 *   <li><a href="#Introduction">Introduction</a>
 *   <li><a href="#Definitions">Definitions</a>
 *       <ul>
 *         <li><a href="#Inverted_Indexing">Inverted Indexing</a>
 *         <li><a href="#Types_of_Fields">Types of Fields</a>
 *         <li><a href="#Segments">Segments</a>
 *         <li><a href="#Document_Numbers">Document Numbers</a>
 *       </ul>
 *   <li><a href="#Overview">Index Structure Overview</a>
 *   <li><a href="#File_Naming">File Naming</a>
 *   <li><a href="#file-names">Summary of File Extensions</a>
 *       <ul>
 *         <li><a href="#Lock_File">Lock File</a>
 *         <li><a href="#History">History</a>
 *         <li><a href="#Limitations">Limitations</a>
 *       </ul>
 * </ul>
 *
 * </div> <a id="Introduction"></a>
 *
 * <h3>Introduction</h3>
 *
 * <div>
 *
 * <p>This document defines the index file formats used in this version of Lucene. If you are using
 * a different version of Lucene, please consult the copy of <code>docs/</code> that was distributed
 * with the version you are using.
 *
 * <p>This document attempts to provide a high-level definition of the Apache Lucene file formats.
 * </div> <a id="Definitions"></a>
 *
 * <h3>Definitions</h3>
 *
 * <div>
 *
 * <p>The fundamental concepts in Lucene are index, document, field and term.
 *
 * <p>An index contains a sequence of documents.
 *
 * <ul>
 *   <li>A document is a sequence of fields.
 *   <li>A field is a named sequence of terms.
 *   <li>A term is a sequence of bytes.
 * </ul>
 *
 * <p>The same sequence of bytes in two different fields is considered a different term. Thus terms
 * are represented as a pair: the string naming the field, and the bytes within the field. <a
 * id="Inverted_Indexing"></a>
 *
 * <h4>Inverted Indexing</h4>
 *
 * <p>Lucene's index stores terms and statistics about those terms in order to make term-based
 * search more efficient. Lucene's terms index falls into the family of indexes known as an
 * <i>inverted index.</i> This is because it can list, for a term, the documents that contain it.
 * This is the inverse of the natural relationship, in which documents list terms. <a
 * id="Types_of_Fields"></a>
 *
 * <h4>Types of Fields</h4>
 *
 * <p>In Lucene, fields may be <i>stored</i>, in which case their text is stored in the index
 * literally, in a non-inverted manner. Fields that are inverted are called <i>indexed</i>. A field
 * may be both stored and indexed.
 *
 * <p>The text of a field may be <i>tokenized</i> into terms to be indexed, or the text of a field
 * may be used literally as a term to be indexed. Most fields are tokenized, but sometimes it is
 * useful for certain identifier fields to be indexed literally.
 *
 * <p>See the {@link org.apache.lucene.document.Field Field} java docs for more information on
 * Fields. <a id="Segments"></a>
 *
 * <h4>Segments</h4>
 *
 * <p>Lucene indexes may be composed of multiple sub-indexes, or <i>segments</i>. Each segment is a
 * fully independent index, which could be searched separately. Indexes evolve by:
 *
 * <ol>
 *   <li>Creating new segments for newly added documents.
 *   <li>Merging existing segments.
 * </ol>
 *
 * <p>Searches may involve multiple segments and/or multiple indexes, each index potentially
 * composed of a set of segments. <a id="Document_Numbers"></a>
 *
 * <h4>Document Numbers</h4>
 *
 * <p>Internally, Lucene refers to documents by an integer <i>document number</i>. The first
 * document added to an index is numbered zero, and each subsequent document added gets a number one
 * greater than the previous.
 *
 * <p>Note that a document's number may change, so caution should be taken when storing these
 * numbers outside of Lucene. In particular, numbers may change in the following situations:
 *
 * <ul>
 *   <li>
 *       <p>The numbers stored in each segment are unique only within the segment, and must be
 *       converted before they can be used in a larger context. The standard technique is to
 *       allocate each segment a range of values, based on the range of numbers used in that
 *       segment. To convert a document number from a segment to an external value, the segment's
 *       <i>base</i> document number is added. To convert an external value back to a
 *       segment-specific value, the segment is identified by the range that the external value is
 *       in, and the segment's base value is subtracted. For example two five document segments
 *       might be combined, so that the first segment has a base value of zero, and the second of
 *       five. Document three from the second segment would have an external value of eight.
 *   <li>
 *       <p>When documents are deleted, gaps are created in the numbering. These are eventually
 *       removed as the index evolves through merging. Deleted documents are dropped when segments
 *       are merged. A freshly-merged segment thus has no gaps in its numbering.
 * </ul>
 *
 * </div> <a id="Overview"></a>
 *
 * <h3>Index Structure Overview</h3>
 *
 * <div>
 *
 * <p>Each segment index maintains the following:
 *
 * <ul>
 *   <li>{@link org.apache.lucene.backward_codecs.lucene90.Lucene90SegmentInfoFormat Segment info}.
 *       This contains metadata about a segment, such as the number of documents, what files it
 *       uses, and information about how the segment is sorted
 *   <li>{@link org.apache.lucene.codecs.lucene94.Lucene94FieldInfosFormat Field names}. This
 *       contains metadata about the set of named fields used in the index.
 *   <li>{@link org.apache.lucene.codecs.lucene90.Lucene90StoredFieldsFormat Stored Field values}.
 *       This contains, for each document, a list of attribute-value pairs, where the attributes are
 *       field names. These are used to store auxiliary information about the document, such as its
 *       title, url, or an identifier to access a database. The set of stored fields are what is
 *       returned for each hit when searching. This is keyed by document number.
 *   <li>{@link org.apache.lucene.backward_codecs.lucene90.Lucene90PostingsFormat Term dictionary}.
 *       A dictionary containing all of the terms used in all of the indexed fields of all of the
 *       documents. The dictionary also contains the number of documents which contain the term, and
 *       pointers to the term's frequency and proximity data.
 *   <li>{@link org.apache.lucene.backward_codecs.lucene90.Lucene90PostingsFormat Term Frequency
 *       data}. For each term in the dictionary, the numbers of all the documents that contain that
 *       term, and the frequency of the term in that document, unless frequencies are omitted
 *       ({@link org.apache.lucene.index.IndexOptions#DOCS IndexOptions.DOCS})
 *   <li>{@link org.apache.lucene.backward_codecs.lucene90.Lucene90PostingsFormat Term Proximity
 *       data}. For each term in the dictionary, the positions that the term occurs in each
 *       document. Note that this will not exist if all fields in all documents omit position data.
 *   <li>{@link org.apache.lucene.codecs.lucene90.Lucene90NormsFormat Normalization factors}. For
 *       each field in each document, a value is stored that is multiplied into the score for hits
 *       on that field.
 *   <li>{@link org.apache.lucene.codecs.lucene90.Lucene90TermVectorsFormat Term Vectors}. For each
 *       field in each document, the term vector (sometimes called document vector) may be stored. A
 *       term vector consists of term text and term frequency. To add Term Vectors to your index see
 *       the {@link org.apache.lucene.document.Field Field} constructors
 *   <li>{@link org.apache.lucene.codecs.lucene90.Lucene90DocValuesFormat Per-document values}. Like
 *       stored values, these are also keyed by document number, but are generally intended to be
 *       loaded into main memory for fast access. Whereas stored values are generally intended for
 *       summary results from searches, per-document values are useful for things like scoring
 *       factors.
 *   <li>{@link org.apache.lucene.codecs.lucene90.Lucene90LiveDocsFormat Live documents}. An
 *       optional file indicating which documents are live.
 *   <li>{@link org.apache.lucene.codecs.lucene90.Lucene90PointsFormat Point values}. Optional pair
 *       of files, recording dimensionally indexed fields, to enable fast numeric range filtering
 *       and large numeric values like BigInteger and BigDecimal (1D) and geographic shape
 *       intersection (2D, 3D).
 *   <li>{@link org.apache.lucene.backward_codecs.lucene94.Lucene94HnswVectorsFormat Vector values}.
 *       The vector format stores numeric vectors in a format optimized for random access and
 *       computation, supporting high-dimensional nearest-neighbor search.
 * </ul>
 *
 * <p>Details on each of these are provided in their linked pages. </div> <a id="File_Naming"></a>
 *
 * <h3>File Naming</h3>
 *
 * <div>
 *
 * <p>All files belonging to a segment have the same name with varying extensions. The extensions
 * correspond to the different file formats described below. When using the Compound File format
 * (default for small segments) these files (except for the Segment info file, the Lock file, and
 * Deleted documents file) are collapsed into a single .cfs file (see below for details)
 *
 * <p>Typically, all segments in an index are stored in a single directory, although this is not
 * required.
 *
 * <p>File names are never re-used. That is, when any file is saved to the Directory it is given a
 * never before used filename. This is achieved using a simple generations approach. For example,
 * the first segments file is segments_1, then segments_2, etc. The generation is a sequential long
 * integer represented in alpha-numeric (base 36) form. </div> <a id="file-names"></a>
 *
 * <h3>Summary of File Extensions</h3>
 *
 * <div>
 *
 * <p>The following table summarizes the names and extensions of the files in Lucene:
 *
 * <table class="padding4" style="border-spacing: 1px; border-collapse: separate">
 * <caption>lucene filenames by extension</caption>
 * <tr>
 * <th>Name</th>
 * <th>Extension</th>
 * <th>Brief Description</th>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.index.SegmentInfos Segments File}</td>
 * <td>segments_N</td>
 * <td>Stores information about a commit point</td>
 * </tr>
 * <tr>
 * <td><a href="#Lock_File">Lock File</a></td>
 * <td>write.lock</td>
 * <td>The Write lock prevents multiple IndexWriters from writing to the same
 * file.</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.backward_codecs.lucene90.Lucene90SegmentInfoFormat Segment Info}</td>
 * <td>.si</td>
 * <td>Stores metadata about a segment</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene90.Lucene90CompoundFormat Compound File}</td>
 * <td>.cfs, .cfe</td>
 * <td>An optional "virtual" file consisting of all the other index files for
 * systems that frequently run out of file handles.</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene94.Lucene94FieldInfosFormat Fields}</td>
 * <td>.fnm</td>
 * <td>Stores information about the fields</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene90.Lucene90StoredFieldsFormat Field Index}</td>
 * <td>.fdx</td>
 * <td>Contains pointers to field data</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene90.Lucene90StoredFieldsFormat Field Data}</td>
 * <td>.fdt</td>
 * <td>The stored fields for documents</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.backward_codecs.lucene90.Lucene90PostingsFormat Term Dictionary}</td>
 * <td>.tim</td>
 * <td>The term dictionary, stores term info</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.backward_codecs.lucene90.Lucene90PostingsFormat Term Index}</td>
 * <td>.tip</td>
 * <td>The index into the Term Dictionary</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.backward_codecs.lucene90.Lucene90PostingsFormat Frequencies}</td>
 * <td>.doc</td>
 * <td>Contains the list of docs which contain each term along with frequency</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.backward_codecs.lucene90.Lucene90PostingsFormat Positions}</td>
 * <td>.pos</td>
 * <td>Stores position information about where a term occurs in the index</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.backward_codecs.lucene90.Lucene90PostingsFormat Payloads}</td>
 * <td>.pay</td>
 * <td>Stores additional per-position metadata information such as character offsets and user payloads</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene90.Lucene90NormsFormat Norms}</td>
 * <td>.nvd, .nvm</td>
 * <td>Encodes length and boost factors for docs and fields</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene90.Lucene90DocValuesFormat Per-Document Values}</td>
 * <td>.dvd, .dvm</td>
 * <td>Encodes additional scoring factors or other per-document information.</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene90.Lucene90TermVectorsFormat Term Vector Index}</td>
 * <td>.tvx</td>
 * <td>Stores offset into the document data file</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene90.Lucene90TermVectorsFormat Term Vector Data}</td>
 * <td>.tvd</td>
 * <td>Contains term vector data.</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene90.Lucene90LiveDocsFormat Live Documents}</td>
 * <td>.liv</td>
 * <td>Info about what documents are live</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.codecs.lucene90.Lucene90PointsFormat Point values}</td>
 * <td>.dii, .dim</td>
 * <td>Holds indexed points</td>
 * </tr>
 * <tr>
 * <td>{@link org.apache.lucene.backward_codecs.lucene94.Lucene94HnswVectorsFormat Vector values}</td>
 * <td>.vec, .vem</td>
 * <td>Holds indexed vectors; <code>.vec</code> files contain the raw vector data, and
 * <code>.vem</code> the vector metadata</td>
 * </tr>
 * </table>
 *
 * </div> <a id="Lock_File"></a>
 *
 * <h3>Lock File</h3>
 *
 * The write lock, which is stored in the index directory by default, is named "write.lock". If the
 * lock directory is different from the index directory then the write lock will be named
 * "XXXX-write.lock" where XXXX is a unique prefix derived from the full path to the index
 * directory. When this file is present, a writer is currently modifying the index (adding or
 * removing documents). This lock file ensures that only one writer is modifying the index at a
 * time. <a id="History"></a>
 *
 * <h3>History</h3>
 *
 * <p>Compatibility notes are provided in this document, describing how file formats have changed
 * from prior versions:
 *
 * <ul>
 *   <li>In version 2.1, the file format was changed to allow lock-less commits (ie, no more commit
 *       lock). The change is fully backwards compatible: you can open a pre-2.1 index for searching
 *       or adding/deleting of docs. When the new segments file is saved (committed), it will be
 *       written in the new file format (meaning no specific "upgrade" process is needed). But note
 *       that once a commit has occurred, pre-2.1 Lucene will not be able to read the index.
 *   <li>In version 2.3, the file format was changed to allow segments to share a single set of doc
 *       store (vectors &amp; stored fields) files. This allows for faster indexing in certain
 *       cases. The change is fully backwards compatible (in the same way as the lock-less commits
 *       change in 2.1).
 *   <li>In version 2.4, Strings are now written as true UTF-8 byte sequence, not Java's modified
 *       UTF-8. See <a href="http://issues.apache.org/jira/browse/LUCENE-510">LUCENE-510</a> for
 *       details.
 *   <li>In version 2.9, an optional opaque Map&lt;String,String&gt; CommitUserData may be passed to
 *       IndexWriter's commit methods (and later retrieved), which is recorded in the segments_N
 *       file. See <a href="http://issues.apache.org/jira/browse/LUCENE-1382">LUCENE-1382</a> for
 *       details. Also, diagnostics were added to each segment written recording details about why
 *       it was written (due to flush, merge; which OS/JRE was used; etc.). See issue <a
 *       href="http://issues.apache.org/jira/browse/LUCENE-1654">LUCENE-1654</a> for details.
 *   <li>In version 3.0, compressed fields are no longer written to the index (they can still be
 *       read, but on merge the new segment will write them, uncompressed). See issue <a
 *       href="http://issues.apache.org/jira/browse/LUCENE-1960">LUCENE-1960</a> for details.
 *   <li>In version 3.1, segments records the code version that created them. See <a
 *       href="http://issues.apache.org/jira/browse/LUCENE-2720">LUCENE-2720</a> for details.
 *       Additionally segments track explicitly whether or not they have term vectors. See <a
 *       href="http://issues.apache.org/jira/browse/LUCENE-2811">LUCENE-2811</a> for details.
 *   <li>In version 3.2, numeric fields are written as natively to stored fields file, previously
 *       they were stored in text format only.
 *   <li>In version 3.4, fields can omit position data while still indexing term frequencies.
 *   <li>In version 4.0, the format of the inverted index became extensible via the {@link
 *       org.apache.lucene.codecs.Codec Codec} api. Fast per-document storage ({@code DocValues})
 *       was introduced. Normalization factors need no longer be a single byte, they can be any
 *       {@link org.apache.lucene.index.NumericDocValues NumericDocValues}. Terms need not be
 *       unicode strings, they can be any byte sequence. Term offsets can optionally be indexed into
 *       the postings lists. Payloads can be stored in the term vectors.
 *   <li>In version 4.1, the format of the postings list changed to use either of FOR compression or
 *       variable-byte encoding, depending upon the frequency of the term. Terms appearing only once
 *       were changed to inline directly into the term dictionary. Stored fields are compressed by
 *       default.
 *   <li>In version 4.2, term vectors are compressed by default. DocValues has a new multi-valued
 *       type (SortedSet), that can be used for faceting/grouping/joining on multi-valued fields.
 *   <li>In version 4.5, DocValues were extended to explicitly represent missing values.
 *   <li>In version 4.6, FieldInfos were extended to support per-field DocValues generation, to
 *       allow updating NumericDocValues fields.
 *   <li>In version 4.8, checksum footers were added to the end of each index file for improved data
 *       integrity. Specifically, the last 8 bytes of every index file contain the zlib-crc32
 *       checksum of the file.
 *   <li>In version 4.9, DocValues has a new multi-valued numeric type (SortedNumeric) that is
 *       suitable for faceting/sorting/analytics.
 *   <li>In version 5.4, DocValues have been improved to store more information on disk: addresses
 *       for binary fields and ord indexes for multi-valued fields.
 *   <li>In version 6.0, Points were added, for multi-dimensional range/distance search.
 *   <li>In version 6.2, new Segment info format that reads/writes the index sort, to support index
 *       sorting.
 *   <li>In version 7.0, DocValues have been improved to better support sparse doc values thanks to
 *       an iterator API.
 *   <li>In version 8.0, postings have been enhanced to record, for each block of doc ids, the (term
 *       freq, normalization factor) pairs that may trigger the maximum score of the block. This
 *       information is recorded alongside skip data in order to be able to skip blocks of doc ids
 *       if they may not produce high enough scores. Additionally doc values and norms has been
 *       extended with jump-tables to make access O(1) instead of O(n), where n is the number of
 *       elements to skip when advancing in the data.
 *   <li>In version 8.4, postings, positions, offsets and payload lengths have move to a more
 *       performant encoding that is vectorized.
 *   <li>In version 8.6, index sort serialization is delegated to the sorts themselves, to allow
 *       user-defined sorts to be used
 *   <li>In version 8.7, stored fields compression became adaptive to better handle documents with
 *       smaller stored fields.
 *   <li>In version 9.0, vector-valued fields were added.
 *   <li>In version 9.1, vector-valued fields were modified to add a graph hierarchy.
 *   <li>In version 9.2, docs of vector-valued fields were moved from .vem to .vec and encoded by
 *       IndexDISI. ordToDoc mappings was added to .vem.
 * </ul>
 *
 * <a id="Limitations"></a>
 *
 * <h3>Limitations</h3>
 *
 * <div>
 *
 * <p>Lucene uses a Java <code>int</code> to refer to document numbers, and the index file format
 * uses an <code>Int32</code> on-disk to store document numbers. This is a limitation of both the
 * index file format and the current implementation. Eventually these should be replaced with either
 * <code>UInt64</code> values, or better yet, {@link org.apache.lucene.store.DataOutput#writeVInt
 * VInt} values which have no limit. </div>
 */
package org.trypticon.luceneupgrader.lucene9.internal.lucene.backward_codecs.lucene94;
