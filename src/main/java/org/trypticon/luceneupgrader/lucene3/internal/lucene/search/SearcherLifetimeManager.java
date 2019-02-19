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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;        // javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.IOUtils;

public class SearcherLifetimeManager implements Closeable {

  static final double NANOS_PER_SEC = 1000000000.0;

  private static class SearcherTracker implements Comparable<SearcherTracker>, Closeable {
    public final IndexSearcher searcher;
    public final double recordTimeSec;
    public final long version;

    public SearcherTracker(IndexSearcher searcher) {
      this.searcher = searcher;
      version = searcher.getIndexReader().getVersion();
      searcher.getIndexReader().incRef();
      // Use nanoTime not currentTimeMillis since it [in
      // theory] reduces risk from clock shift
      recordTimeSec = System.nanoTime() / NANOS_PER_SEC;
    }

    // Newer searchers are sort before older ones:
    public int compareTo(SearcherTracker other) {
      // Be defensive: cannot subtract since it could
      // technically overflow long, though, we'd never hit
      // that in practice:
      if (recordTimeSec < other.recordTimeSec) {
        return 1;
      } else if (other.recordTimeSec < recordTimeSec) {
        return -1;
      } else {
        return 0;
      }
    }

    public synchronized void close() throws IOException {
      searcher.getIndexReader().decRef();
    }
  }

  private volatile boolean closed;

  // TODO: we could get by w/ just a "set"; need to have
  // Tracker hash by its version and have compareTo(Long)
  // compare to its version
  private final ConcurrentHashMap<Long,SearcherTracker> searchers = new ConcurrentHashMap<Long,SearcherTracker>();

  private void ensureOpen() {
    if (closed) {
      throw new AlreadyClosedException("this SearcherLifetimeManager instance is closed");
    }
  }


  public long record(IndexSearcher searcher) throws IOException {
    ensureOpen();
    // TODO: we don't have to use IR.getVersion to track;
    // could be risky (if it's buggy); we could get better
    // bug isolation if we assign our own private ID:
    final long version = searcher.getIndexReader().getVersion();
    SearcherTracker tracker = searchers.get(version);
    if (tracker == null) {
      //System.out.println("RECORD version=" + version + " ms=" + System.currentTimeMillis());
      tracker = new SearcherTracker(searcher);
      if (searchers.putIfAbsent(version, tracker) != null) {
        // Another thread beat us -- must decRef to undo
        // incRef done by SearcherTracker ctor:
        tracker.close();
      }
    } else if (tracker.searcher != searcher) {
      throw new IllegalArgumentException("the provided searcher has the same underlying reader version yet the searcher instance differs from before (new=" + searcher + " vs old=" + tracker.searcher);
    }

    return version;
  }


  public IndexSearcher acquire(long version) {
    ensureOpen();
    final SearcherTracker tracker = searchers.get(version);
    if (tracker != null &&
        tracker.searcher.getIndexReader().tryIncRef()) {
      return tracker.searcher;
    }

    return null;
  }


  public void release(IndexSearcher s) throws IOException {
    s.getIndexReader().decRef();
  }

  public interface Pruner {

    public boolean doPrune(double ageSec, IndexSearcher searcher);
  }


  public final static class PruneByAge implements Pruner {
    private final double maxAgeSec;

    public PruneByAge(double maxAgeSec) {
      if (maxAgeSec < 0) {
        throw new IllegalArgumentException("maxAgeSec must be > 0 (got " + maxAgeSec + ")");
      }
      this.maxAgeSec = maxAgeSec;
    }

    public boolean doPrune(double ageSec, IndexSearcher searcher) {
      return ageSec > maxAgeSec;
    }
  }


  public synchronized void prune(Pruner pruner) throws IOException {
    // Cannot just pass searchers.values() to ArrayList ctor
    // (not thread-safe since the values can change while
    // ArrayList is init'ing itself); must instead iterate
    // ourselves:
    final List<SearcherTracker> trackers = new ArrayList<SearcherTracker>();
    for(SearcherTracker tracker : searchers.values()) {
      trackers.add(tracker);
    }
    Collections.sort(trackers);
    double lastRecordTimeSec = 0.0;
    final double now = System.nanoTime()/NANOS_PER_SEC;
    for (SearcherTracker tracker: trackers) {
      final double ageSec;
      if (lastRecordTimeSec == 0.0) {
        ageSec = 0.0;
      } else {
        ageSec = now - lastRecordTimeSec;
      }
      // First tracker is always age 0.0 sec, since it's
      // still "live"; second tracker's age (= seconds since
      // it was "live") is now minus first tracker's
      // recordTime, etc:
      if (pruner.doPrune(ageSec, tracker.searcher)) {
        //System.out.println("PRUNE version=" + tracker.version + " age=" + ageSec + " ms=" + System.currentTimeMillis());
        searchers.remove(tracker.version);
        tracker.close();
      }
      lastRecordTimeSec = tracker.recordTimeSec;
    }
  }


  public synchronized void close() throws IOException {
    closed = true;
    final List<SearcherTracker> toClose = new ArrayList<SearcherTracker>(searchers.values());

    // Remove up front in case exc below, so we don't
    // over-decRef on double-close:
    for(SearcherTracker tracker : toClose) {
      searchers.remove(tracker.version);
    }

    IOUtils.close(toClose);

    // Make some effort to catch mis-use:
    if (searchers.size() != 0) {
      throw new IllegalStateException("another thread called record while this SearcherLifetimeManager instance was being closed; not all searchers were closed");
    }
  }
}
