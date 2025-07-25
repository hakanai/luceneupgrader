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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.DocumentsWriterPerThread.FlushedSegment;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IOConsumer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.InfoStream;

/**
 * This class accepts multiple added documents and directly writes segment files.
 *
 * <p>Each added document is passed to the indexing chain, which in turn processes the document into
 * the different codec formats. Some formats write bytes to files immediately, e.g. stored fields
 * and term vectors, while others are buffered by the indexing chain and written only on flush.
 *
 * <p>Once we have used our allowed RAM buffer, or the number of added docs is large enough (in the
 * case we are flushing by doc count instead of RAM usage), we create a real segment and flush it to
 * the Directory.
 *
 * <p>Threads:
 *
 * <p>Multiple threads are allowed into addDocument at once. There is an initial synchronized call
 * to {@link DocumentsWriterFlushControl#obtainAndLock()} which allocates a DWPT for this indexing
 * thread. The same thread will not necessarily get the same DWPT over time. Then updateDocuments is
 * called on that DWPT without synchronization (most of the "heavy lifting" is in this call). Once a
 * DWPT fills up enough RAM or hold enough documents in memory the DWPT is checked out for flush and
 * all changes are written to the directory. Each DWPT corresponds to one segment being written.
 *
 * <p>When flush is called by IndexWriter we check out all DWPTs that are associated with the
 * current {@link DocumentsWriterDeleteQueue} out of the {@link DocumentsWriterPerThreadPool} and
 * write them to disk. The flush process can piggyback on incoming indexing threads or even block
 * them from adding documents if flushing can't keep up with new documents being added. Unless the
 * stall control kicks in to block indexing threads flushes are happening concurrently to actual
 * index requests.
 *
 * <p>Exceptions:
 *
 * <p>Because this class directly updates in-memory posting lists, and flushes stored fields and
 * term vectors directly to files in the directory, there are certain limited times when an
 * exception can corrupt this state. For example, a disk full while flushing stored fields leaves
 * this file in a corrupt state. Or, an OOM exception while appending to the in-memory posting lists
 * can corrupt that posting list. We call such exceptions "aborting exceptions". In these cases we
 * must call abort() to discard all docs added since the last flush.
 *
 * <p>All other exceptions ("non-aborting exceptions") can still partially update the index
 * structures. These updates are consistent, but, they represent only a part of the document seen up
 * until the exception was hit. When this happens, we immediately mark the document as deleted so
 * that the document is always atomically ("all or none") added to the index.
 */
final class DocumentsWriter implements Closeable, Accountable {
  private final AtomicLong pendingNumDocs;

  private final FlushNotifications flushNotifications;

  private volatile boolean closed;

  private final InfoStream infoStream;

  private final LiveIndexWriterConfig config;

  private final AtomicInteger numDocsInRAM = new AtomicInteger(0);

  // TODO: cut over to BytesRefHash in BufferedDeletes
  volatile DocumentsWriterDeleteQueue deleteQueue;
  private final DocumentsWriterFlushQueue ticketQueue = new DocumentsWriterFlushQueue();
  /*
   * we preserve changes during a full flush since IW might not check out before
   * we release all changes. NRT Readers otherwise suddenly return true from
   * isCurrent while there are actually changes currently committed. See also
   * #anyChanges() & #flushAllThreads
   */
  private volatile boolean pendingChangesInCurrentFullFlush;

  final DocumentsWriterPerThreadPool perThreadPool;
  final DocumentsWriterFlushControl flushControl;

  DocumentsWriter(
      FlushNotifications flushNotifications,
      int indexCreatedVersionMajor,
      AtomicLong pendingNumDocs,
      boolean enableTestPoints,
      Supplier<String> segmentNameSupplier,
      LiveIndexWriterConfig config,
      Directory directoryOrig,
      Directory directory,
      FieldInfos.FieldNumbers globalFieldNumberMap) {
    this.config = config;
    this.infoStream = config.getInfoStream();
    this.deleteQueue = new DocumentsWriterDeleteQueue(infoStream);
    this.perThreadPool =
        new DocumentsWriterPerThreadPool(
            () -> {
              final FieldInfos.Builder infos = new FieldInfos.Builder(globalFieldNumberMap);
              return new DocumentsWriterPerThread(
                  indexCreatedVersionMajor,
                  segmentNameSupplier.get(),
                  directoryOrig,
                  directory,
                  config,
                  deleteQueue,
                  infos,
                  pendingNumDocs,
                  enableTestPoints);
            });
    this.pendingNumDocs = pendingNumDocs;
    flushControl = new DocumentsWriterFlushControl(this, config);
    this.flushNotifications = flushNotifications;
  }

  long deleteQueries(final Query... queries) throws IOException {
    return applyDeleteOrUpdate(q -> q.addDelete(queries));
  }

  long deleteTerms(final Term... terms) throws IOException {
    return applyDeleteOrUpdate(q -> q.addDelete(terms));
  }

  long updateDocValues(DocValuesUpdate... updates) throws IOException {
    return applyDeleteOrUpdate(q -> q.addDocValuesUpdates(updates));
  }

  private synchronized long applyDeleteOrUpdate(ToLongFunction<DocumentsWriterDeleteQueue> function)
      throws IOException {
    // This method is synchronized to make sure we don't replace the deleteQueue while applying this
    // update / delete
    // otherwise we might lose an update / delete if this happens concurrently to a full flush.
    final DocumentsWriterDeleteQueue deleteQueue = this.deleteQueue;
    long seqNo = function.applyAsLong(deleteQueue);
    flushControl.doOnDelete();
    if (applyAllDeletes()) {
      seqNo = -seqNo;
    }
    return seqNo;
  }

  /** If buffered deletes are using too much heap, resolve them and write disk and return true. */
  private boolean applyAllDeletes() throws IOException {
    final DocumentsWriterDeleteQueue deleteQueue = this.deleteQueue;

    // Check the applyAllDeletes flag first. This helps exit early most of the time without checking
    // isFullFlush(), which takes a lock and introduces contention on small documents that are quick
    // to index.
    if (flushControl.getApplyAllDeletes()
        && flushControl.isFullFlush() == false
        // never apply deletes during full flush this breaks happens before relationship.
        && deleteQueue.isOpen()
        // if it's closed then it's already fully applied and we have a new delete queue
        && flushControl.getAndResetApplyAllDeletes()) {
      if (ticketQueue.addTicket(() -> maybeFreezeGlobalBuffer(deleteQueue)) != null) {
        flushNotifications.onDeletesApplied(); // apply deletes event forces a purge
        return true;
      }
    }
    return false;
  }

  void purgeFlushTickets(boolean forced, IOConsumer<DocumentsWriterFlushQueue.FlushTicket> consumer)
      throws IOException {
    if (forced) {
      ticketQueue.forcePurge(consumer);
    } else {
      ticketQueue.tryPurge(consumer);
    }
  }

  /** Returns how many docs are currently buffered in RAM. */
  int getNumDocs() {
    return numDocsInRAM.get();
  }

  private void ensureOpen() throws AlreadyClosedException {
    if (closed) {
      throw new AlreadyClosedException("this DocumentsWriter is closed");
    }
  }

  /**
   * Called if we hit an exception at a bad time (when updating the index files) and must discard
   * all currently buffered docs. This resets our state, discarding any docs added since last flush.
   */
  synchronized void abort() throws IOException {
    boolean success = false;
    try {
      deleteQueue.clear();
      if (infoStream.isEnabled("DW")) {
        infoStream.message("DW", "abort");
      }
      for (final DocumentsWriterPerThread perThread : perThreadPool.filterAndLock(x -> true)) {
        try {
          abortDocumentsWriterPerThread(perThread);
        } finally {
          perThread.unlock();
        }
      }
      flushControl.abortPendingFlushes();
      flushControl.waitForFlush();
      assert perThreadPool.size() == 0
          : "There are still active DWPT in the pool: " + perThreadPool.size();
      success = true;
    } finally {
      if (success) {
        assert flushControl.getFlushingBytes() == 0
            : "flushingBytes has unexpected value 0 != " + flushControl.getFlushingBytes();
        assert flushControl.netBytes() == 0
            : "netBytes has unexpected value 0 != " + flushControl.netBytes();
      }
      if (infoStream.isEnabled("DW")) {
        infoStream.message("DW", "done abort success=" + success);
      }
    }
  }

  boolean flushOneDWPT() throws IOException {
    if (infoStream.isEnabled("DW")) {
      infoStream.message("DW", "startFlushOneDWPT");
    }
    if (maybeFlush() == false) {
      DocumentsWriterPerThread documentsWriterPerThread =
          flushControl.checkoutLargestNonPendingWriter();
      if (documentsWriterPerThread != null) {
        doFlush(documentsWriterPerThread);
        return true;
      }
      return false;
    }
    return true;
  }

  /**
   * Locks all currently active DWPT and aborts them. The returned Closeable should be closed once
   * the locks for the aborted DWPTs can be released.
   */
  synchronized Closeable lockAndAbortAll() throws IOException {
    if (infoStream.isEnabled("DW")) {
      infoStream.message("DW", "lockAndAbortAll");
    }
    // Make sure we move all pending tickets into the flush queue:
    ticketQueue.forcePurge(
        ticket -> {
          if (ticket.getFlushedSegment() != null) {
            pendingNumDocs.addAndGet(-ticket.getFlushedSegment().segmentInfo.info.maxDoc());
          }
        });
    List<DocumentsWriterPerThread> writers = new ArrayList<>();
    AtomicBoolean released = new AtomicBoolean(false);
    final Closeable release =
        () -> {
          // we return this closure to unlock all writers once done
          // or if hit an exception below in the try block.
          // we can't assign this later otherwise the ref can't be final
          if (released.compareAndSet(false, true)) { // only once
            if (infoStream.isEnabled("DW")) {
              infoStream.message("DW", "unlockAllAbortedThread");
            }
            perThreadPool.unlockNewWriters();
            for (DocumentsWriterPerThread writer : writers) {
              writer.unlock();
            }
          }
        };
    try {
      deleteQueue.clear();
      perThreadPool.lockNewWriters();
      writers.addAll(perThreadPool.filterAndLock(x -> true));
      for (final DocumentsWriterPerThread perThread : writers) {
        assert perThread.isHeldByCurrentThread();
        abortDocumentsWriterPerThread(perThread);
      }
      deleteQueue.clear();

      // jump over any possible in flight ops:
      deleteQueue.skipSequenceNumbers(perThreadPool.size() + 1);

      flushControl.abortPendingFlushes();
      flushControl.waitForFlush();
      if (infoStream.isEnabled("DW")) {
        infoStream.message("DW", "finished lockAndAbortAll success=true");
      }
      return release;
    } catch (Throwable t) {
      if (infoStream.isEnabled("DW")) {
        infoStream.message("DW", "finished lockAndAbortAll success=false");
      }
      try {
        // if something happens here we unlock all states again
        release.close();
      } catch (Throwable t1) {
        t.addSuppressed(t1);
      }
      throw t;
    }
  }

  /** Returns how many documents were aborted. */
  private void abortDocumentsWriterPerThread(final DocumentsWriterPerThread perThread)
      throws IOException {
    assert perThread.isHeldByCurrentThread();
    try {
      subtractFlushedNumDocs(perThread.getNumDocsInRAM());
      perThread.abort();
    } finally {
      flushControl.doOnAbort(perThread);
    }
  }

  /** returns the maximum sequence number for all previously completed operations */
  long getMaxCompletedSequenceNumber() {
    return deleteQueue.getMaxCompletedSeqNo();
  }

  boolean anyChanges() {
    /*
     * changes are either in a DWPT or in the deleteQueue.
     * yet if we currently flush deletes and / or dwpt there
     * could be a window where all changes are in the ticket queue
     * before they are published to the IW. ie we need to check if the
     * ticket queue has any tickets.
     */
    boolean anyChanges =
        numDocsInRAM.get() != 0
            || anyDeletions()
            || ticketQueue.hasTickets()
            || pendingChangesInCurrentFullFlush;
    if (infoStream.isEnabled("DW") && anyChanges) {
      infoStream.message(
          "DW",
          "anyChanges? numDocsInRam="
              + numDocsInRAM.get()
              + " deletes="
              + anyDeletions()
              + " hasTickets:"
              + ticketQueue.hasTickets()
              + " pendingChangesInFullFlush: "
              + pendingChangesInCurrentFullFlush);
    }
    return anyChanges;
  }

  int getBufferedDeleteTermsSize() {
    return deleteQueue.getBufferedUpdatesTermsSize();
  }

  boolean anyDeletions() {
    return deleteQueue.anyChanges();
  }

  @Override
  public void close() throws IOException {
    closed = true;
    IOUtils.close(flushControl, perThreadPool);
  }

  private boolean preUpdate() throws IOException {
    ensureOpen();
    boolean hasEvents = false;
    while (flushControl.anyStalledThreads()
        || (config.checkPendingFlushOnUpdate && flushControl.numQueuedFlushes() > 0)) {
      // Help out flushing any queued DWPTs so we can un-stall:
      // Try pickup pending threads here if possible
      // no need to loop over the next pending flushes... doFlush will take care of this
      hasEvents |= maybeFlush();
      flushControl.waitIfStalled(); // block if stalled
    }
    return hasEvents;
  }

  private boolean postUpdate(DocumentsWriterPerThread flushingDWPT, boolean hasEvents)
      throws IOException {
    hasEvents |= applyAllDeletes();
    if (flushingDWPT != null) {
      doFlush(flushingDWPT);
      hasEvents = true;
    } else if (config.checkPendingFlushOnUpdate) {
      hasEvents |= maybeFlush();
    }
    return hasEvents;
  }

  long updateDocuments(
      final Iterable<? extends Iterable<? extends IndexableField>> docs,
      final DocumentsWriterDeleteQueue.Node<?> delNode)
      throws IOException {
    boolean hasEvents = preUpdate();

    final DocumentsWriterPerThread dwpt = flushControl.obtainAndLock();
    final DocumentsWriterPerThread flushingDWPT;
    long seqNo;

    try {
      // This must happen after we've pulled the DWPT because IW.close
      // waits for all DWPT to be released:
      ensureOpen();
      try {
        seqNo =
            dwpt.updateDocuments(docs, delNode, flushNotifications, numDocsInRAM::incrementAndGet);
      } finally {
        if (dwpt.isAborted()) {
          flushControl.doOnAbort(dwpt);
        }
      }
      flushingDWPT = flushControl.doAfterDocument(dwpt);
    } finally {
      // If a flush is occurring, we don't want to allow this dwpt to be reused
      // If it is aborted, we shouldn't allow it to be reused
      // If the deleteQueue is advanced, this means the maximum seqNo has been set and it cannot be
      // reused
      synchronized (flushControl) {
        if (dwpt.isFlushPending() || dwpt.isAborted() || dwpt.isQueueAdvanced()) {
          dwpt.unlock();
        } else {
          perThreadPool.marksAsFreeAndUnlock(dwpt);
        }
      }
      assert dwpt.isHeldByCurrentThread() == false : "we didn't release the dwpt even on abort";
    }

    if (postUpdate(flushingDWPT, hasEvents)) {
      seqNo = -seqNo;
    }
    return seqNo;
  }

  private boolean maybeFlush() throws IOException {
    final DocumentsWriterPerThread flushingDWPT = flushControl.nextPendingFlush();
    if (flushingDWPT != null) {
      doFlush(flushingDWPT);
      return true;
    }
    return false;
  }

  private void doFlush(DocumentsWriterPerThread flushingDWPT) throws IOException {
    assert flushingDWPT != null : "Flushing DWPT must not be null";
    do {
      assert flushingDWPT.hasFlushed() == false;
      boolean success = false;
      DocumentsWriterFlushQueue.FlushTicket ticket = null;
      try {
        assert currentFullFlushDelQueue == null
                || flushingDWPT.deleteQueue == currentFullFlushDelQueue
            : "expected: "
                + currentFullFlushDelQueue
                + " but was: "
                + flushingDWPT.deleteQueue
                + " "
                + flushControl.isFullFlush();
        /*
         * Since with DWPT the flush process is concurrent and several DWPT
         * could flush at the same time we must maintain the order of the
         * flushes before we can apply the flushed segment and the frozen global
         * deletes it is buffering. The reason for this is that the global
         * deletes mark a certain point in time where we took a DWPT out of
         * rotation and freeze the global deletes.
         *
         * Example: A flush 'A' starts and freezes the global deletes, then
         * flush 'B' starts and freezes all deletes occurred since 'A' has
         * started. if 'B' finishes before 'A' we need to wait until 'A' is done
         * otherwise the deletes frozen by 'B' are not applied to 'A' and we
         * might miss to deletes documents in 'A'.
         */
        try {
          assert assertTicketQueueModification(flushingDWPT.deleteQueue);
          final DocumentsWriterPerThread dwpt = flushingDWPT;
          // Each flush is assigned a ticket in the order they acquire the ticketQueue lock
          ticket =
              ticketQueue.addTicket(
                  () -> new DocumentsWriterFlushQueue.FlushTicket(dwpt.prepareFlush(), true));
          final int flushingDocsInRam = flushingDWPT.getNumDocsInRAM();
          boolean dwptSuccess = false;
          try {
            // flush concurrently without locking
            final FlushedSegment newSegment = flushingDWPT.flush(flushNotifications);
            ticketQueue.addSegment(ticket, newSegment);
            dwptSuccess = true;
          } finally {
            subtractFlushedNumDocs(flushingDocsInRam);
            if (flushingDWPT.pendingFilesToDelete().isEmpty() == false) {
              Set<String> files = flushingDWPT.pendingFilesToDelete();
              flushNotifications.deleteUnusedFiles(files);
            }
            if (dwptSuccess == false) {
              flushNotifications.flushFailed(flushingDWPT.getSegmentInfo());
            }
          }
          // flush was successful once we reached this point - new seg. has been assigned to the
          // ticket!
          success = true;
        } finally {
          if (!success && ticket != null) {
            // In the case of a failure make sure we are making progress and
            // apply all the deletes since the segment flush failed since the flush
            // ticket could hold global deletes see FlushTicket#canPublish()
            ticketQueue.markTicketFailed(ticket);
          }
        }
        /*
         * Now we are done and try to flush the ticket queue if the head of the
         * queue has already finished the flush.
         */
        if (ticketQueue.getTicketCount() >= perThreadPool.size()) {
          // This means there is a backlog: the one
          // thread in innerPurge can't keep up with all
          // other threads flushing segments.  In this case
          // we forcefully stall the producers.
          flushNotifications.onTicketBacklog();
        }
      } finally {
        flushControl.doAfterFlush(flushingDWPT);
      }
    } while ((flushingDWPT = flushControl.nextPendingFlush()) != null);
    flushNotifications.afterSegmentsFlushed();
  }

  synchronized long getNextSequenceNumber() {
    // this must be synced otherwise the delete queue might change concurrently
    return deleteQueue.getNextSequenceNumber();
  }

  synchronized long resetDeleteQueue(int maxNumPendingOps) {
    final DocumentsWriterDeleteQueue newQueue = deleteQueue.advanceQueue(maxNumPendingOps);
    assert deleteQueue.isAdvanced();
    assert newQueue.isAdvanced() == false;
    assert deleteQueue.getLastSequenceNumber() <= newQueue.getLastSequenceNumber();
    assert deleteQueue.getMaxSeqNo() <= newQueue.getLastSequenceNumber()
        : "maxSeqNo: " + deleteQueue.getMaxSeqNo() + " vs. " + newQueue.getLastSequenceNumber();
    long oldMaxSeqNo = deleteQueue.getMaxSeqNo();
    deleteQueue = newQueue;
    return oldMaxSeqNo;
  }

  interface FlushNotifications { // TODO maybe we find a better name for this?

    /**
     * Called when files were written to disk that are not used anymore. It's the implementation's
     * responsibility to clean these files up
     */
    void deleteUnusedFiles(Collection<String> files);

    /** Called when a segment failed to flush. */
    void flushFailed(SegmentInfo info);

    /** Called after one or more segments were flushed to disk. */
    void afterSegmentsFlushed() throws IOException;

    /**
     * Should be called if a flush or an indexing operation caused a tragic / unrecoverable event.
     */
    void onTragicEvent(Throwable event, String message);

    /** Called once deletes have been applied either after a flush or on a deletes call */
    void onDeletesApplied();

    /**
     * Called once the DocumentsWriter ticket queue has a backlog. This means there is an inner
     * thread that tries to publish flushed segments but can't keep up with the other threads
     * flushing new segments. This likely requires other thread to forcefully purge the buffer to
     * help publishing. This can't be done in-place since we might hold index writer locks when this
     * is called. The caller must ensure that the purge happens without an index writer lock being
     * held.
     *
     * @see DocumentsWriter#purgeFlushTickets(boolean, IOConsumer)
     */
    void onTicketBacklog();
  }

  void subtractFlushedNumDocs(int numFlushed) {
    int oldValue = numDocsInRAM.get();
    while (numDocsInRAM.compareAndSet(oldValue, oldValue - numFlushed) == false) {
      oldValue = numDocsInRAM.get();
    }
    assert numDocsInRAM.get() >= 0;
  }

  // for asserts
  private volatile DocumentsWriterDeleteQueue currentFullFlushDelQueue = null;

  // for asserts
  private synchronized boolean setFlushingDeleteQueue(DocumentsWriterDeleteQueue session) {
    assert currentFullFlushDelQueue == null || currentFullFlushDelQueue.isOpen() == false
        : "Can not replace a full flush queue if the queue is not closed";
    currentFullFlushDelQueue = session;
    return true;
  }

  private boolean assertTicketQueueModification(DocumentsWriterDeleteQueue deleteQueue) {
    // assign it then we don't need to sync on DW
    DocumentsWriterDeleteQueue currentFullFlushDelQueue = this.currentFullFlushDelQueue;
    assert currentFullFlushDelQueue == null || currentFullFlushDelQueue == deleteQueue
        : "only modifications from the current flushing queue are permitted while doing a full flush";
    return true;
  }

  /*
   * FlushAllThreads is synced by IW fullFlushLock. Flushing all threads is a
   * two stage operation; the caller must ensure (in try/finally) that finishFlush
   * is called after this method, to release the flush lock in DWFlushControl
   */
  long flushAllThreads() throws IOException {
    final DocumentsWriterDeleteQueue flushingDeleteQueue;
    if (infoStream.isEnabled("DW")) {
      infoStream.message("DW", "startFullFlush");
    }

    long seqNo;
    synchronized (this) {
      pendingChangesInCurrentFullFlush = anyChanges();
      flushingDeleteQueue = deleteQueue;
      /* Cutover to a new delete queue.  This must be synced on the flush control
       * otherwise a new DWPT could sneak into the loop with an already flushing
       * delete queue */
      seqNo = flushControl.markForFullFlush(); // swaps this.deleteQueue synced on FlushControl
      assert setFlushingDeleteQueue(flushingDeleteQueue);
    }
    assert currentFullFlushDelQueue != null;
    assert currentFullFlushDelQueue != deleteQueue;

    boolean anythingFlushed = false;
    try {
      anythingFlushed |= maybeFlush();
      // If a concurrent flush is still in flight wait for it
      flushControl.waitForFlush();
      if (anythingFlushed == false
          && flushingDeleteQueue.anyChanges()) { // apply deletes if we did not flush any document
        if (infoStream.isEnabled("DW")) {
          infoStream.message(
              "DW", Thread.currentThread().getName() + ": flush naked frozen global deletes");
        }
        assert assertTicketQueueModification(flushingDeleteQueue);
        ticketQueue.addTicket(() -> maybeFreezeGlobalBuffer(flushingDeleteQueue));
      }
      // we can't assert that we don't have any tickets in the queue since we might add a
      // DocumentsWriterDeleteQueue
      // concurrently if we have very small ram buffers this happens quite frequently
      assert !flushingDeleteQueue.anyChanges();
    } finally {
      assert flushingDeleteQueue == currentFullFlushDelQueue;
      flushingDeleteQueue
          .close(); // all DWPT have been processed and this queue has been fully flushed to the
      // ticket-queue
    }
    if (anythingFlushed) {
      return -seqNo;
    } else {
      return seqNo;
    }
  }

  private DocumentsWriterFlushQueue.FlushTicket maybeFreezeGlobalBuffer(
      DocumentsWriterDeleteQueue deleteQueue) {
    FrozenBufferedUpdates frozenBufferedUpdates = deleteQueue.maybeFreezeGlobalBuffer();
    if (frozenBufferedUpdates != null) {
      // no need to publish anything if we don't have any frozen updates
      return new DocumentsWriterFlushQueue.FlushTicket(frozenBufferedUpdates, false);
    }
    return null;
  }

  void finishFullFlush(boolean success) throws IOException {
    try {
      if (infoStream.isEnabled("DW")) {
        infoStream.message(
            "DW", Thread.currentThread().getName() + " finishFullFlush success=" + success);
      }
      assert setFlushingDeleteQueue(null);
      if (success) {
        // Release the flush lock
        flushControl.finishFullFlush();
      } else {
        flushControl.abortFullFlushes();
      }
    } finally {
      pendingChangesInCurrentFullFlush = false;
      applyAllDeletes(); // make sure we do execute this since we block applying deletes during full
      // flush
    }
  }

  @Override
  public long ramBytesUsed() {
    return flushControl.ramBytesUsed();
  }

  /**
   * Returns the number of bytes currently being flushed
   *
   * <p>This is a subset of the value returned by {@link #ramBytesUsed()}
   */
  long getFlushingBytes() {
    return flushControl.getFlushingBytes();
  }
}
