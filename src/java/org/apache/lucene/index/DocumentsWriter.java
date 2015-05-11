package org.apache.lucene.index;

/**
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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMFile;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BitVector;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.ThreadInterruptedException;

import java.io.IOException;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;


/**
 * This class accepts multiple added documents and directly
 * writes a single segment file.  It does this more
 * efficiently than creating a single segment per document
 * (with DocumentWriter) and doing standard merges on those
 * segments.
 *
 * Each added document is passed to the {@code DocConsumer},
 * which in turn processes the document and interacts with
 * other consumers in the indexing chain.  Certain
 * consumers, like {@code StoredFieldsWriter} and {@code
 * TermVectorsTermsWriter}, digest a document and
 * immediately write bytes to the "doc store" files (ie,
 * they do not consume RAM per document, except while they
 * are processing the document).
 *
 * Other consumers, eg {@code FreqProxTermsWriter} and
 * {@code NormsWriter}, buffer bytes in RAM and flush only
 * when a new segment is produced.

 * Once we have used our allowed RAM buffer, or the number
 * of added docs is large enough (in the case we are
 * flushing by doc count instead of RAM usage), we create a
 * real segment and flush it to the Directory.
 *
 * Threads:
 *
 * Multiple threads are allowed into addDocument at once.
 * There is an initial synchronized call to getThreadState
 * which allocates a ThreadState for this thread.  The same
 * thread will get the same ThreadState over time (thread
 * affinity) so that if there are consistent patterns (for
 * example each thread is indexing a different content
 * source) then we make better use of RAM.  Then
 * processDocument is called on that ThreadState without
 * synchronization (most of the "heavy lifting" is in this
 * call).  Finally the synchronized "finishDocument" is
 * called to flush changes to the directory.
 *
 * When flush is called by IndexWriter we forcefully idle
 * all threads and flush only once they are all idle.  This
 * means you can call flush with a given thread even while
 * other threads are actively adding/deleting documents.
 *
 *
 * Exceptions:
 *
 * Because this class directly updates in-memory posting
 * lists, and flushes stored fields and term vectors
 * directly to files in the directory, there are certain
 * limited times when an exception can corrupt this state.
 * For example, a disk full while flushing stored fields
 * leaves this file in a corrupt state.  Or, an OOM
 * exception while appending to the in-memory posting lists
 * can corrupt that posting list.  We call such exceptions
 * "aborting exceptions".  In these cases we must call
 * abort() to discard all docs added since the last flush.
 *
 * All other exceptions ("non-aborting exceptions") can
 * still partially update the index structures.  These
 * updates are consistent, but, they represent only a part
 * of the document seen up until the exception was hit.
 * When this happens, we immediately mark the document as
 * deleted so that the document is always atomically ("all
 * or none") added to the index.
 */

final class DocumentsWriter {
  final AtomicLong bytesUsed = new AtomicLong(0);
  IndexWriter writer;
  Directory directory;

  String segment;                         // Current segment we are working on

  private int nextDocID;                  // Next docID to be added
  private int numDocs;                    // # of docs added, but not yet flushed

  // Max # ThreadState instances; if there are more threads
  // than this they share ThreadStates
  private DocumentsWriterThreadState[] threadStates = new DocumentsWriterThreadState[0];
  private final HashMap<Thread,DocumentsWriterThreadState> threadBindings = new HashMap<Thread,DocumentsWriterThreadState>();

  boolean bufferIsFull;                   // True when it's time to write segment
  private boolean aborting;               // True if an abort is pending

  PrintStream infoStream;
  int maxFieldLength = IndexWriter.DEFAULT_MAX_FIELD_LENGTH;
  Similarity similarity;

  // max # simultaneous threads; if there are more than
  // this, they wait for others to finish first
  private final int maxThreadStates;

  // Deletes for our still-in-RAM (to be flushed next) segment
  private BufferedDeletes pendingDeletes = new BufferedDeletes();
  
  static class DocState {
    DocumentsWriter docWriter;
    Analyzer analyzer;
    int maxFieldLength;
    PrintStream infoStream;
    Similarity similarity;
    int docID;
    Document doc;
    String maxTermPrefix;

    // Only called by asserts
    public boolean testPoint(String name) {
      return docWriter.writer.testPoint(name);
    }

    public void clear() {
      // don't hold onto doc nor analyzer, in case it is
      // largish:
      doc = null;
      analyzer = null;
    }
  }

  /** Consumer returns this on each doc.  This holds any
   *  state that must be flushed synchronized "in docID
   *  order".  We gather these and flush them in order. */
  abstract static class DocWriter {
    DocWriter next;
    int docID;
    abstract void finish() throws IOException;
    abstract void abort();
    abstract long sizeInBytes();

    void setNext(DocWriter next) {
      this.next = next;
    }
  }

  /**
   * Create and return a new DocWriterBuffer.
   */
  PerDocBuffer newPerDocBuffer() {
    return new PerDocBuffer();
  }

  /**
   * RAMFile buffer for DocWriters.
   */
  class PerDocBuffer extends RAMFile {
    
    /**
     * Allocate bytes used from shared pool.
     */
    @Override
    protected byte[] newBuffer(int size) {
      assert size == PER_DOC_BLOCK_SIZE;
      return perDocAllocator.getByteBlock();
    }
    
    /**
     * Recycle the bytes used.
     */
    synchronized void recycle() {
      if (buffers.size() > 0) {
        setLength(0);
        
        // Recycle the blocks
        perDocAllocator.recycleByteBlocks(buffers);
        buffers.clear();
        sizeInBytes = 0;
        
        assert numBuffers() == 0;
      }
    }
  }
  
  /**
   * The IndexingChain must define the {@code #getChain(DocumentsWriter)} method
   * which returns the DocConsumer that the DocumentsWriter calls to process the
   * documents. 
   */
  abstract static class IndexingChain {
    abstract DocConsumer getChain(DocumentsWriter documentsWriter);
  }
  
  static final IndexingChain defaultIndexingChain = new IndexingChain() {

    @Override
    DocConsumer getChain(DocumentsWriter documentsWriter) {
      /*
      This is the current indexing chain:

      DocConsumer / DocConsumerPerThread
        --> code: DocFieldProcessor / DocFieldProcessorPerThread
          --> DocFieldConsumer / DocFieldConsumerPerThread / DocFieldConsumerPerField
            --> code: DocFieldConsumers / DocFieldConsumersPerThread / DocFieldConsumersPerField
              --> code: DocInverter / DocInverterPerThread / DocInverterPerField
                --> InvertedDocConsumer / InvertedDocConsumerPerThread / InvertedDocConsumerPerField
                  --> code: TermsHash / TermsHashPerThread / TermsHashPerField
                    --> TermsHashConsumer / TermsHashConsumerPerThread / TermsHashConsumerPerField
                      --> code: FreqProxTermsWriter / FreqProxTermsWriterPerThread / FreqProxTermsWriterPerField
                      --> code: TermVectorsTermsWriter / TermVectorsTermsWriterPerThread / TermVectorsTermsWriterPerField
                --> InvertedDocEndConsumer / InvertedDocConsumerPerThread / InvertedDocConsumerPerField
                  --> code: NormsWriter / NormsWriterPerThread / NormsWriterPerField
              --> code: StoredFieldsWriter / StoredFieldsWriterPerThread / StoredFieldsWriterPerField
    */

    // Build up indexing chain:

      final TermsHashConsumer termVectorsWriter = new TermVectorsTermsWriter(documentsWriter);
      final TermsHashConsumer freqProxWriter = new FreqProxTermsWriter();

      final InvertedDocConsumer  termsHash = new TermsHash(documentsWriter, true, freqProxWriter,
                                                           new TermsHash(documentsWriter, false, termVectorsWriter, null));
      final NormsWriter normsWriter = new NormsWriter();
      final DocInverter docInverter = new DocInverter(termsHash, normsWriter);
      return new DocFieldProcessor(documentsWriter, docInverter);
    }
  };

  final DocConsumer consumer;

  // How much RAM we can use before flushing.  This is 0 if
  // we are flushing by doc count instead.

  private final IndexWriterConfig config;

  private final FieldInfos fieldInfos;

  private final BufferedDeletesStream bufferedDeletesStream;

  DocumentsWriter(IndexWriterConfig config, Directory directory, IndexWriter writer, FieldInfos fieldInfos, BufferedDeletesStream bufferedDeletesStream) throws IOException {
    this.directory = directory;
    this.writer = writer;
    this.similarity = config.getSimilarity();
    this.maxThreadStates = config.getMaxThreadStates();
    this.fieldInfos = fieldInfos;
    this.bufferedDeletesStream = bufferedDeletesStream;

    consumer = config.getIndexingChain().getChain(this);
    this.config = config;
  }

  public FieldInfos getFieldInfos() {
    return fieldInfos;
  }

  /** If non-null, various details of indexing are printed
   *  here. */
  synchronized void setInfoStream(PrintStream infoStream) {
    this.infoStream = infoStream;
    for (DocumentsWriterThreadState threadState : threadStates) {
      threadState.docState.infoStream = infoStream;
    }
  }

  synchronized void setMaxFieldLength(int maxFieldLength) {
    this.maxFieldLength = maxFieldLength;
    for (DocumentsWriterThreadState threadState : threadStates) {
      threadState.docState.maxFieldLength = maxFieldLength;
    }
  }

  synchronized void setSimilarity(Similarity similarity) {
    this.similarity = similarity;
    for (DocumentsWriterThreadState threadState : threadStates) {
      threadState.docState.similarity = similarity;
    }
  }

  /** Get current segment name we are writing. */
  synchronized String getSegment() {
    return segment;
  }

  /** Returns how many docs are currently buffered in RAM. */
  synchronized int getNumDocs() {
    return numDocs;
  }

  void message(String message) {
    if (infoStream != null) {
      writer.message("DW: " + message);
    }
  }

  synchronized void setAborting() {
    if (infoStream != null) {
      message("setAborting");
    }
    aborting = true;
  }

  /** Called if we hit an exception at a bad time (when
   *  updating the index files) and must discard all
   *  currently buffered docs.  This resets our state,
   *  discarding any docs added since last flush. */
  synchronized void abort() throws IOException {
    if (infoStream != null) {
      message("docWriter: abort");
    }

    boolean success = false;

    try {

      // Forcefully remove waiting ThreadStates from line
      try {
        waitQueue.abort();
      } catch (Throwable ignored) {
      }

      // Wait for all other threads to finish with
      // DocumentsWriter:
      try {
        waitIdle();
      } finally {
        if (infoStream != null) {
          message("docWriter: abort waitIdle done");
        }
        
        assert 0 == waitQueue.numWaiting: "waitQueue.numWaiting=" + waitQueue.numWaiting;
        waitQueue.waitingBytes = 0;
        
        pendingDeletes.clear();
        
        for (DocumentsWriterThreadState threadState : threadStates) {
          try {
            threadState.consumer.abort();
          } catch (Throwable ignored) {
          }
        }
          
        try {
          consumer.abort();
        } catch (Throwable ignored) {
        }
        
        // Reset all postings data
        doAfterFlush();
      }

      success = true;
    } finally {
      aborting = false;
      notifyAll();
      if (infoStream != null) {
        message("docWriter: done abort; success=" + success);
      }
    }
  }

  /** Reset after a flush */
  private void doAfterFlush() throws IOException {
    // All ThreadStates should be idle when we are called
    assert allThreadsIdle();
    threadBindings.clear();
    waitQueue.reset();
    segment = null;
    numDocs = 0;
    nextDocID = 0;
    bufferIsFull = false;
    for (DocumentsWriterThreadState threadState : threadStates) {
      threadState.doAfterFlush();
    }
  }

  private synchronized boolean allThreadsIdle() {
    for (DocumentsWriterThreadState threadState : threadStates) {
      if (!threadState.isIdle) {
        return false;
      }
    }
    return true;
  }

  synchronized boolean anyChanges() {
    return numDocs != 0 || pendingDeletes.any();
  }

  private void pushDeletes(SegmentInfo newSegment, SegmentInfos segmentInfos) {
    // Lock order: DW -> BD
    final long delGen = bufferedDeletesStream.getNextGen();
    if (pendingDeletes.any()) {
      if (segmentInfos.size() > 0 || newSegment != null) {
        final FrozenBufferedDeletes packet = new FrozenBufferedDeletes(pendingDeletes, delGen);
        if (infoStream != null) {
          message("flush: push buffered deletes startSize=" + pendingDeletes.bytesUsed.get() + " frozenSize=" + packet.bytesUsed);
        }
        bufferedDeletesStream.push(packet);
        if (infoStream != null) {
          message("flush: delGen=" + packet.gen);
        }
        if (newSegment != null) {
          newSegment.setBufferedDeletesGen(packet.gen);
        }
      } else {
        if (infoStream != null) {
          message("flush: drop buffered deletes: no segments");
        }
        // We can safely discard these deletes: since
        // there are no segments, the deletions cannot
        // affect anything.
      }
      pendingDeletes.clear();
    } else if (newSegment != null) {
      newSegment.setBufferedDeletesGen(delGen);
    }
  }

  /** Flush all pending docs to a new segment */
  // Lock order: IW -> DW
  synchronized SegmentInfo flush(IndexWriter writer, IndexFileDeleter deleter, MergePolicy mergePolicy, SegmentInfos segmentInfos) throws IOException {

    final long startTime = System.currentTimeMillis();

    // We change writer's segmentInfos:
    assert Thread.holdsLock(writer);

    waitIdle();

    if (numDocs == 0) {
      // nothing to do!
      if (infoStream != null) {
        message("flush: no docs; skipping");
      }
      // Lock order: IW -> DW -> BD
      pushDeletes(null, segmentInfos);
      return null;
    }

    if (aborting) {
      if (infoStream != null) {
        message("flush: skip because aborting is set");
      }
      return null;
    }

    boolean success = false;

    SegmentInfo newSegment;

    try {
      //System.out.println(Thread.currentThread().getName() + ": nw=" + waitQueue.numWaiting);
      assert nextDocID == numDocs: "nextDocID=" + nextDocID + " numDocs=" + numDocs;
      assert waitQueue.numWaiting == 0: "numWaiting=" + waitQueue.numWaiting;
      assert waitQueue.waitingBytes == 0;

      if (infoStream != null) {
        message("flush postings as segment " + segment + " numDocs=" + numDocs);
      }

      final SegmentWriteState flushState = new SegmentWriteState(infoStream, directory, segment, fieldInfos,
                                                                 numDocs, writer.getConfig().getTermIndexInterval(),
                                                                 pendingDeletes);
      // Apply delete-by-docID now (delete-byDocID only
      // happens when an exception is hit processing that
      // doc, eg if analyzer has some problem w/ the text):
      if (pendingDeletes.docIDs.size() > 0) {
        flushState.deletedDocs = new BitVector(numDocs);
        for(int delDocID : pendingDeletes.docIDs) {
          flushState.deletedDocs.set(delDocID);
        }
        pendingDeletes.bytesUsed.addAndGet(-pendingDeletes.docIDs.size() * BufferedDeletes.BYTES_PER_DEL_DOCID);
        pendingDeletes.docIDs.clear();
      }

      newSegment = new SegmentInfo(segment, numDocs, directory, false, true, fieldInfos.hasProx(), false);

      Collection<DocConsumerPerThread> threads = new HashSet<DocConsumerPerThread>();
      for (DocumentsWriterThreadState threadState : threadStates) {
        threads.add(threadState.consumer);
      }

      double startMBUsed = bytesUsed()/1024./1024.;

      consumer.flush(threads, flushState);

      newSegment.setHasVectors(flushState.hasVectors);

      if (infoStream != null) {
        message("new segment has " + (flushState.hasVectors ? "vectors" : "no vectors"));
        if (flushState.deletedDocs != null) {
          message("new segment has " + flushState.deletedDocs.count() + " deleted docs");
        }
        message("flushedFiles=" + newSegment.files());
      }

      if (mergePolicy.useCompoundFile(segmentInfos, newSegment)) {
        final String cfsFileName = IndexFileNames.segmentFileName(segment, IndexFileNames.COMPOUND_FILE_EXTENSION);

        if (infoStream != null) {
          message("flush: create compound file \"" + cfsFileName + "\"");
        }

        CompoundFileWriter cfsWriter = new CompoundFileWriter(directory, cfsFileName);
        for(String fileName : newSegment.files()) {
          cfsWriter.addFile(fileName);
        }
        cfsWriter.close();
        deleter.deleteNewFiles(newSegment.files());
        newSegment.setUseCompoundFile(true);
      }

      // Must write deleted docs after the CFS so we don't
      // slurp the del file into CFS:
      if (flushState.deletedDocs != null) {
        final int delCount = flushState.deletedDocs.count();
        assert delCount > 0;
        newSegment.setDelCount(delCount);
        newSegment.advanceDelGen();
        final String delFileName = newSegment.getDelFileName();
        if (infoStream != null) {
          message("flush: write " + delCount + " deletes to " + delFileName);
        }
        boolean success2 = false;
        try {
          // TODO: in the NRT case it'd be better to hand
          // this del vector over to the
          // shortly-to-be-opened SegmentReader and let it
          // carry the changes; there's no reason to use
          // filesystem as intermediary here.
          flushState.deletedDocs.write(directory, delFileName);
          success2 = true;
        } finally {
          if (!success2) {
            try {
              directory.deleteFile(delFileName);
            } catch (Throwable t) {
              // suppress this so we keep throwing the
              // original exception
            }
          }
        }
      }

      if (infoStream != null) {
        message("flush: segment=" + newSegment);
        final double newSegmentSizeNoStore = newSegment.sizeInBytes(false)/1024./1024.;
        final double newSegmentSize = newSegment.sizeInBytes(true)/1024./1024.;
        message("  ramUsed=" + nf.format(startMBUsed) + " MB" +
                " newFlushedSize=" + nf.format(newSegmentSize) + " MB" +
                " (" + nf.format(newSegmentSizeNoStore) + " MB w/o doc stores)" +
                " docs/MB=" + nf.format(numDocs / newSegmentSize) +
                " new/old=" + nf.format(100.0 * newSegmentSizeNoStore / startMBUsed) + "%");
      }

      success = true;
    } finally {
      notifyAll();
      if (!success) {
        if (segment != null) {
          deleter.refresh(segment);
        }
        abort();
      }
    }

    doAfterFlush();

    // Lock order: IW -> DW -> BD
    pushDeletes(newSegment, segmentInfos);
    if (infoStream != null) {
      message("flush time " + (System.currentTimeMillis()-startTime) + " msec");
    }

    return newSegment;
  }

  synchronized void close() {
    notifyAll();
  }

  public synchronized void waitIdle() {
    while (!allThreadsIdle()) {
      try {
        wait();
      } catch (InterruptedException ie) {
        throw new ThreadInterruptedException(ie);
      }
    }
  }

  private static class SkipDocWriter extends DocWriter {
    @Override
    void finish() {
    }
    @Override
    void abort() {
    }
    @Override
    long sizeInBytes() {
      return 0;
    }
  }
  final SkipDocWriter skipDocWriter = new SkipDocWriter();

  NumberFormat nf = NumberFormat.getInstance();

  /* Initial chunks size of the shared byte[] blocks used to
     store postings data */
  final static int BYTE_BLOCK_SHIFT = 15;
  final static int BYTE_BLOCK_SIZE = 1 << BYTE_BLOCK_SHIFT;
  final static int BYTE_BLOCK_MASK = BYTE_BLOCK_SIZE - 1;

  private class ByteBlockAllocator extends ByteBlockPool.Allocator {
    final int blockSize;

    ByteBlockAllocator(int blockSize) {
      this.blockSize = blockSize;
    }

    ArrayList<byte[]> freeByteBlocks = new ArrayList<byte[]>();
    
    /* Allocate another byte[] from the shared pool */
    @Override
    byte[] getByteBlock() {
      synchronized(DocumentsWriter.this) {
        final int size = freeByteBlocks.size();
        final byte[] b;
        if (0 == size) {
          b = new byte[blockSize];
          bytesUsed.addAndGet(blockSize);
        } else
          b = freeByteBlocks.remove(size-1);
        return b;
      }
    }

    /* Return byte[]'s to the pool */

    @Override
    void recycleByteBlocks(byte[][] blocks, int start, int end) {
      synchronized(DocumentsWriter.this) {
        for(int i=start;i<end;i++) {
          freeByteBlocks.add(blocks[i]);
          blocks[i] = null;
        }
      }
    }

    @Override
    void recycleByteBlocks(List<byte[]> blocks) {
      synchronized(DocumentsWriter.this) {
        final int size = blocks.size();
        for(int i=0;i<size;i++) {
          freeByteBlocks.add(blocks.get(i));
          blocks.set(i, null);
        }
      }
    }
  }

  /* Initial chunks size of the shared int[] blocks used to
     store postings data */
  final static int INT_BLOCK_SHIFT = 13;
  final static int INT_BLOCK_SIZE = 1 << INT_BLOCK_SHIFT;
  final static int INT_BLOCK_MASK = INT_BLOCK_SIZE - 1;

  private List<int[]> freeIntBlocks = new ArrayList<int[]>();

  /* Allocate another int[] from the shared pool */
  synchronized int[] getIntBlock() {
    final int size = freeIntBlocks.size();
    final int[] b;
    if (0 == size) {
      b = new int[INT_BLOCK_SIZE];
      bytesUsed.addAndGet(INT_BLOCK_SIZE*RamUsageEstimator.NUM_BYTES_INT);
    } else {
      b = freeIntBlocks.remove(size-1);
    }
    return b;
  }

  synchronized void bytesUsed(long numBytes) {
    bytesUsed.addAndGet(numBytes);
  }

  long bytesUsed() {
    return bytesUsed.get() + pendingDeletes.bytesUsed.get();
  }

  /* Return int[]s to the pool */
  synchronized void recycleIntBlocks(int[][] blocks, int start, int end) {
    for(int i=start;i<end;i++) {
      freeIntBlocks.add(blocks[i]);
      blocks[i] = null;
    }
  }

  ByteBlockAllocator byteBlockAllocator = new ByteBlockAllocator(BYTE_BLOCK_SIZE);

  final static int PER_DOC_BLOCK_SIZE = 1024;

  final ByteBlockAllocator perDocAllocator = new ByteBlockAllocator(PER_DOC_BLOCK_SIZE);


  /* Initial chunk size of the shared char[] blocks used to
     store term text */
  final static int CHAR_BLOCK_SHIFT = 14;
  final static int CHAR_BLOCK_SIZE = 1 << CHAR_BLOCK_SHIFT;
  final static int CHAR_BLOCK_MASK = CHAR_BLOCK_SIZE - 1;

  final static int MAX_TERM_LENGTH = CHAR_BLOCK_SIZE-1;

  private ArrayList<char[]> freeCharBlocks = new ArrayList<char[]>();

  /* Allocate another char[] from the shared pool */
  synchronized char[] getCharBlock() {
    final int size = freeCharBlocks.size();
    final char[] c;
    if (0 == size) {
      bytesUsed.addAndGet(CHAR_BLOCK_SIZE * RamUsageEstimator.NUM_BYTES_CHAR);
      c = new char[CHAR_BLOCK_SIZE];
    } else
      c = freeCharBlocks.remove(size-1);
    // We always track allocations of char blocks, for now,
    // because nothing that skips allocation tracking
    // (currently only term vectors) uses its own char
    // blocks.
    return c;
  }

  /* Return char[]s to the pool */
  synchronized void recycleCharBlocks(char[][] blocks, int numBlocks) {
    for(int i=0;i<numBlocks;i++) {
      freeCharBlocks.add(blocks[i]);
      blocks[i] = null;
    }
  }

  String toMB(long v) {
    return nf.format(v/1024./1024.);
  }

  /* We have four pools of RAM: Postings, byte blocks
   * (holds freq/prox posting data), char blocks (holds
   * characters in the term) and per-doc buffers (stored fields/term vectors).  
   * Different docs require varying amount of storage from 
   * these four classes.
   * 
   * For example, docs with many unique single-occurrence
   * short terms will use up the Postings RAM and hardly any
   * of the other two.  Whereas docs with very large terms
   * will use alot of char blocks RAM and relatively less of
   * the other two.  This method just frees allocations from
   * the pools once we are over-budget, which balances the
   * pools to match the current docs. */
  void balanceRAM() {

    final boolean doBalance;
    final long deletesRAMUsed;

    deletesRAMUsed = bufferedDeletesStream.bytesUsed();

    final long ramBufferSize;
    final double mb = config.getRAMBufferSizeMB();
    if (mb == IndexWriterConfig.DISABLE_AUTO_FLUSH) {
      ramBufferSize = IndexWriterConfig.DISABLE_AUTO_FLUSH;
    } else {
      ramBufferSize = (long) (mb*1024*1024);
    }

    synchronized(this) {
      if (ramBufferSize == IndexWriterConfig.DISABLE_AUTO_FLUSH || bufferIsFull) {
        return;
      }
    
      doBalance = bytesUsed() + deletesRAMUsed >= ramBufferSize;
    }

    if (doBalance) {

      if (infoStream != null) {
        message("  RAM: balance allocations: usedMB=" + toMB(bytesUsed()) +
                " vs trigger=" + toMB(ramBufferSize) +
                " deletesMB=" + toMB(deletesRAMUsed) +
                " byteBlockFree=" + toMB(byteBlockAllocator.freeByteBlocks.size()*BYTE_BLOCK_SIZE) +
                " perDocFree=" + toMB(perDocAllocator.freeByteBlocks.size()*PER_DOC_BLOCK_SIZE) +
                " charBlockFree=" + toMB(freeCharBlocks.size()*CHAR_BLOCK_SIZE*RamUsageEstimator.NUM_BYTES_CHAR));
      }

      final long startBytesUsed = bytesUsed() + deletesRAMUsed;

      int iter = 0;

      // We free equally from each pool in 32 KB
      // chunks until we are below our threshold
      // (freeLevel)

      boolean any = true;

      final long freeLevel = (long) (0.95 * ramBufferSize);

      while(bytesUsed()+deletesRAMUsed > freeLevel) {
      
        synchronized(this) {
          if (0 == perDocAllocator.freeByteBlocks.size() 
              && 0 == byteBlockAllocator.freeByteBlocks.size() 
              && 0 == freeCharBlocks.size() 
              && 0 == freeIntBlocks.size() 
              && !any) {
            // Nothing else to free -- must flush now.
            bufferIsFull = bytesUsed()+deletesRAMUsed > ramBufferSize;
            if (infoStream != null) {
              if (bytesUsed()+deletesRAMUsed > ramBufferSize) {
                message("    nothing to free; set bufferIsFull");
              } else {
                message("    nothing to free");
              }
            }
            break;
          }

          if ((0 == iter % 5) && byteBlockAllocator.freeByteBlocks.size() > 0) {
            byteBlockAllocator.freeByteBlocks.remove(byteBlockAllocator.freeByteBlocks.size()-1);
            bytesUsed.addAndGet(-BYTE_BLOCK_SIZE);
          }

          if ((1 == iter % 5) && freeCharBlocks.size() > 0) {
            freeCharBlocks.remove(freeCharBlocks.size()-1);
            bytesUsed.addAndGet(-CHAR_BLOCK_SIZE * RamUsageEstimator.NUM_BYTES_CHAR);
          }

          if ((2 == iter % 5) && freeIntBlocks.size() > 0) {
            freeIntBlocks.remove(freeIntBlocks.size()-1);
            bytesUsed.addAndGet(-INT_BLOCK_SIZE * RamUsageEstimator.NUM_BYTES_INT);
          }

          if ((3 == iter % 5) && perDocAllocator.freeByteBlocks.size() > 0) {
            // Remove upwards of 32 blocks (each block is 1K)
            for (int i = 0; i < 32; ++i) {
              perDocAllocator.freeByteBlocks.remove(perDocAllocator.freeByteBlocks.size() - 1);
              bytesUsed.addAndGet(-PER_DOC_BLOCK_SIZE);
              if (perDocAllocator.freeByteBlocks.size() == 0) {
                break;
              }
            }
          }
        }

        if ((4 == iter % 5) && any) {
          // Ask consumer to free any recycled state
          any = consumer.freeRAM();
        }

        iter++;
      }

      if (infoStream != null) {
        message("    after free: freedMB=" + nf.format((startBytesUsed-bytesUsed()-deletesRAMUsed)/1024./1024.) + " usedMB=" + nf.format((bytesUsed()+deletesRAMUsed)/1024./1024.));
      }
    }
  }

  final WaitQueue waitQueue = new WaitQueue();

  private class WaitQueue {
    DocWriter[] waiting;
    int nextWriteDocID;
    int nextWriteLoc;
    int numWaiting;
    long waitingBytes;

    public WaitQueue() {
      waiting = new DocWriter[10];
    }

    synchronized void reset() {
      // NOTE: nextWriteLoc doesn't need to be reset
      assert numWaiting == 0;
      assert waitingBytes == 0;
      nextWriteDocID = 0;
    }

    synchronized boolean doPause() {
      final double mb = config.getRAMBufferSizeMB();
      final long waitQueuePauseBytes;
      if (mb == IndexWriterConfig.DISABLE_AUTO_FLUSH) {
        waitQueuePauseBytes = 4*1024*1024;
      } else {
        waitQueuePauseBytes = (long) (mb*1024*1024*0.1);
      }
      return waitingBytes > waitQueuePauseBytes;
    }

    synchronized void abort() {
      int count = 0;
      for(int i=0;i<waiting.length;i++) {
        final DocWriter doc = waiting[i];
        if (doc != null) {
          doc.abort();
          waiting[i] = null;
          count++;
        }
      }
      waitingBytes = 0;
      assert count == numWaiting;
      numWaiting = 0;
    }

    private void writeDocument(DocWriter doc) throws IOException {
      assert doc == skipDocWriter || nextWriteDocID == doc.docID;
      boolean success = false;
      try {
        doc.finish();
        nextWriteDocID++;
        nextWriteLoc++;
        assert nextWriteLoc <= waiting.length;
        if (nextWriteLoc == waiting.length) {
          nextWriteLoc = 0;
        }
        success = true;
      } finally {
        if (!success) {
          setAborting();
        }
      }
    }

    synchronized public boolean add(DocWriter doc) throws IOException {

      assert doc.docID >= nextWriteDocID;

      if (doc.docID == nextWriteDocID) {
        writeDocument(doc);
        while(true) {
          doc = waiting[nextWriteLoc];
          if (doc != null) {
            numWaiting--;
            waiting[nextWriteLoc] = null;
            waitingBytes -= doc.sizeInBytes();
            writeDocument(doc);
          } else {
            break;
          }
        }
      } else {

        // I finished before documents that were added
        // before me.  This can easily happen when I am a
        // small doc and the docs before me were large, or,
        // just due to luck in the thread scheduling.  Just
        // add myself to the queue and when that large doc
        // finishes, it will flush me:
        int gap = doc.docID - nextWriteDocID;
        if (gap >= waiting.length) {
          // Grow queue
          DocWriter[] newArray = new DocWriter[ArrayUtil.oversize(gap, RamUsageEstimator.NUM_BYTES_OBJECT_REF)];
          assert nextWriteLoc >= 0;
          System.arraycopy(waiting, nextWriteLoc, newArray, 0, waiting.length-nextWriteLoc);
          System.arraycopy(waiting, 0, newArray, waiting.length-nextWriteLoc, nextWriteLoc);
          nextWriteLoc = 0;
          waiting = newArray;
          gap = doc.docID - nextWriteDocID;
        }

        int loc = nextWriteLoc + gap;
        if (loc >= waiting.length) {
          loc -= waiting.length;
        }

        // We should only wrap one time
        assert loc < waiting.length;

        // Nobody should be in my spot!
        assert waiting[loc] == null;
        waiting[loc] = doc;
        numWaiting++;
        waitingBytes += doc.sizeInBytes();
      }
      
      return doPause();
    }
  }
}
