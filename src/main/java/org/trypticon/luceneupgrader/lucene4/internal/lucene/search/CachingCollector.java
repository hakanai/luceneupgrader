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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.RamUsageEstimator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class CachingCollector extends Collector {
  
  // Max out at 512K arrays
  private static final int MAX_ARRAY_SIZE = 512 * 1024;
  private static final int INITIAL_ARRAY_SIZE = 128;
  private final static int[] EMPTY_INT_ARRAY = new int[0];

  private static class SegStart {
    public final AtomicReaderContext readerContext;
    public final int end;

    public SegStart(AtomicReaderContext readerContext, int end) {
      this.readerContext = readerContext;
      this.end = end;
    }
  }
  
  private static final class CachedScorer extends Scorer {
    
    // NOTE: these members are package-private b/c that way accessing them from
    // the outer class does not incur access check by the JVM. The same
    // situation would be if they were defined in the outer class as private
    // members.
    int doc;
    float score;
    
    private CachedScorer() { super(null); }

    @Override
    public final float score() { return score; }
    
    @Override
    public final int advance(int target) { throw new UnsupportedOperationException(); }
    
    @Override
    public final int docID() { return doc; }
    
    @Override
    public final int freq() { throw new UnsupportedOperationException(); }
    
    @Override
    public final int nextDoc() { throw new UnsupportedOperationException(); }
    
    @Override
    public long cost() { return 1; }
    }

  // A CachingCollector which caches scores
  private static final class ScoreCachingCollector extends CachingCollector {

    private final CachedScorer cachedScorer;
    private final List<float[]> cachedScores;

    private Scorer scorer;
    private float[] curScores;

    ScoreCachingCollector(Collector other, double maxRAMMB) {
      super(other, maxRAMMB, true);

      cachedScorer = new CachedScorer();
      cachedScores = new ArrayList<>();
      curScores = new float[INITIAL_ARRAY_SIZE];
      cachedScores.add(curScores);
    }

    ScoreCachingCollector(Collector other, int maxDocsToCache) {
      super(other, maxDocsToCache);

      cachedScorer = new CachedScorer();
      cachedScores = new ArrayList<>();
      curScores = new float[INITIAL_ARRAY_SIZE];
      cachedScores.add(curScores);
    }
    
    @Override
    public void collect(int doc) throws IOException {

      if (curDocs == null) {
        // Cache was too large
        cachedScorer.score = scorer.score();
        cachedScorer.doc = doc;
        other.collect(doc);
        return;
      }

      // Allocate a bigger array or abort caching
      if (upto == curDocs.length) {
        base += upto;
        
        // Compute next array length - don't allocate too big arrays
        int nextLength = 8*curDocs.length;
        if (nextLength > MAX_ARRAY_SIZE) {
          nextLength = MAX_ARRAY_SIZE;
        }

        if (base + nextLength > maxDocsToCache) {
          // try to allocate a smaller array
          nextLength = maxDocsToCache - base;
          if (nextLength <= 0) {
            // Too many docs to collect -- clear cache
            curDocs = null;
            curScores = null;
            cachedSegs.clear();
            cachedDocs.clear();
            cachedScores.clear();
            cachedScorer.score = scorer.score();
            cachedScorer.doc = doc;
            other.collect(doc);
            return;
          }
        }
        
        curDocs = new int[nextLength];
        cachedDocs.add(curDocs);
        curScores = new float[nextLength];
        cachedScores.add(curScores);
        upto = 0;
      }
      
      curDocs[upto] = doc;
      cachedScorer.score = curScores[upto] = scorer.score();
      upto++;
      cachedScorer.doc = doc;
      other.collect(doc);
    }

    @Override
    public void replay(Collector other) throws IOException {
      replayInit(other);
      
      int curUpto = 0;
      int curBase = 0;
      int chunkUpto = 0;
      curDocs = EMPTY_INT_ARRAY;
      for (SegStart seg : cachedSegs) {
        other.setNextReader(seg.readerContext);
        other.setScorer(cachedScorer);
        while (curBase + curUpto < seg.end) {
          if (curUpto == curDocs.length) {
            curBase += curDocs.length;
            curDocs = cachedDocs.get(chunkUpto);
            curScores = cachedScores.get(chunkUpto);
            chunkUpto++;
            curUpto = 0;
          }
          cachedScorer.score = curScores[curUpto];
          cachedScorer.doc = curDocs[curUpto];
          other.collect(curDocs[curUpto++]);
        }
      }
    }

    @Override
    public void setScorer(Scorer scorer) throws IOException {
      this.scorer = scorer;
      other.setScorer(cachedScorer);
    }

    @Override
    public String toString() {
      if (isCached()) {
        return "CachingCollector (" + (base+upto) + " docs & scores cached)";
      } else {
        return "CachingCollector (cache was cleared)";
      }
    }

  }

  // A CachingCollector which does not cache scores
  private static final class NoScoreCachingCollector extends CachingCollector {
    
    NoScoreCachingCollector(Collector other, double maxRAMMB) {
     super(other, maxRAMMB, false);
    }

    NoScoreCachingCollector(Collector other, int maxDocsToCache) {
     super(other, maxDocsToCache);
    }

    @Override
    public void collect(int doc) throws IOException {

      if (curDocs == null) {
        // Cache was too large
        other.collect(doc);
        return;
      }

      // Allocate a bigger array or abort caching
      if (upto == curDocs.length) {
        base += upto;
        
        // Compute next array length - don't allocate too big arrays
        int nextLength = 8*curDocs.length;
        if (nextLength > MAX_ARRAY_SIZE) {
          nextLength = MAX_ARRAY_SIZE;
        }

        if (base + nextLength > maxDocsToCache) {
          // try to allocate a smaller array
          nextLength = maxDocsToCache - base;
          if (nextLength <= 0) {
            // Too many docs to collect -- clear cache
            curDocs = null;
            cachedSegs.clear();
            cachedDocs.clear();
            other.collect(doc);
            return;
          }
        }
        
        curDocs = new int[nextLength];
        cachedDocs.add(curDocs);
        upto = 0;
      }
      
      curDocs[upto] = doc;
      upto++;
      other.collect(doc);
    }

    @Override
    public void replay(Collector other) throws IOException {
      replayInit(other);
      
      int curUpto = 0;
      int curbase = 0;
      int chunkUpto = 0;
      curDocs = EMPTY_INT_ARRAY;
      for (SegStart seg : cachedSegs) {
        other.setNextReader(seg.readerContext);
        while (curbase + curUpto < seg.end) {
          if (curUpto == curDocs.length) {
            curbase += curDocs.length;
            curDocs = cachedDocs.get(chunkUpto);
            chunkUpto++;
            curUpto = 0;
          }
          other.collect(curDocs[curUpto++]);
        }
      }
    }

    @Override
    public void setScorer(Scorer scorer) throws IOException {
      other.setScorer(scorer);
    }

    @Override
    public String toString() {
      if (isCached()) {
        return "CachingCollector (" + (base+upto) + " docs cached)";
      } else {
        return "CachingCollector (cache was cleared)";
      }
    }

  }

  // TODO: would be nice if a collector defined a
  // needsScores() method so we can specialize / do checks
  // up front. This is only relevant for the ScoreCaching
  // version -- if the wrapped Collector does not need
  // scores, it can avoid cachedScorer entirely.
  protected final Collector other;
  
  protected final int maxDocsToCache;
  protected final List<SegStart> cachedSegs = new ArrayList<>();
  protected final List<int[]> cachedDocs;
  
  private AtomicReaderContext lastReaderContext;
  
  protected int[] curDocs;
  protected int upto;
  protected int base;
  protected int lastDocBase;
  
  public static CachingCollector create(final boolean acceptDocsOutOfOrder, boolean cacheScores, double maxRAMMB) {
    Collector other = new Collector() {
      @Override
      public boolean acceptsDocsOutOfOrder() {
        return acceptDocsOutOfOrder;
      }
      
      @Override
      public void setScorer(Scorer scorer) {}

      @Override
      public void collect(int doc) {}

      @Override
      public void setNextReader(AtomicReaderContext context) {}

    };
    return create(other, cacheScores, maxRAMMB);
  }

  public static CachingCollector create(Collector other, boolean cacheScores, double maxRAMMB) {
    return cacheScores ? new ScoreCachingCollector(other, maxRAMMB) : new NoScoreCachingCollector(other, maxRAMMB);
  }

  public static CachingCollector create(Collector other, boolean cacheScores, int maxDocsToCache) {
    return cacheScores ? new ScoreCachingCollector(other, maxDocsToCache) : new NoScoreCachingCollector(other, maxDocsToCache);
  }
  
  // Prevent extension from non-internal classes
  private CachingCollector(Collector other, double maxRAMMB, boolean cacheScores) {
    this.other = other;
    
    cachedDocs = new ArrayList<>();
    curDocs = new int[INITIAL_ARRAY_SIZE];
    cachedDocs.add(curDocs);

    int bytesPerDoc = RamUsageEstimator.NUM_BYTES_INT;
    if (cacheScores) {
      bytesPerDoc += RamUsageEstimator.NUM_BYTES_FLOAT;
    }
    maxDocsToCache = (int) ((maxRAMMB * 1024 * 1024) / bytesPerDoc);
  }

  private CachingCollector(Collector other, int maxDocsToCache) {
    this.other = other;

    cachedDocs = new ArrayList<>();
    curDocs = new int[INITIAL_ARRAY_SIZE];
    cachedDocs.add(curDocs);
    this.maxDocsToCache = maxDocsToCache;
  }
  
  @Override
  public boolean acceptsDocsOutOfOrder() {
    return other.acceptsDocsOutOfOrder();
  }

  public boolean isCached() {
    return curDocs != null;
  }

  @Override  
  public void setNextReader(AtomicReaderContext context) throws IOException {
    other.setNextReader(context);
    if (lastReaderContext != null) {
      cachedSegs.add(new SegStart(lastReaderContext, base+upto));
    }
    lastReaderContext = context;
  }

  void replayInit(Collector other) {
    if (!isCached()) {
      throw new IllegalStateException("cannot replay: cache was cleared because too much RAM was required");
    }
    
    if (!other.acceptsDocsOutOfOrder() && this.other.acceptsDocsOutOfOrder()) {
      throw new IllegalArgumentException(
          "cannot replay: given collector does not support "
              + "out-of-order collection, while the wrapped collector does. "
              + "Therefore cached documents may be out-of-order.");
    }
    
    //System.out.println("CC: replay totHits=" + (upto + base));
    if (lastReaderContext != null) {
      cachedSegs.add(new SegStart(lastReaderContext, base+upto));
      lastReaderContext = null;
    }
  }

  public abstract void replay(Collector other) throws IOException;
  
}
