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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Counter;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ThreadInterruptedException;

import java.io.IOException;

public class TimeLimitingCollector extends Collector {


  @SuppressWarnings("serial")
  public static class TimeExceededException extends RuntimeException {
    private long timeAllowed;
    private long timeElapsed;
    private int lastDocCollected;
    private TimeExceededException(long timeAllowed, long timeElapsed, int lastDocCollected) {
      super("Elapsed time: " + timeElapsed + "Exceeded allowed search time: " + timeAllowed + " ms.");
      this.timeAllowed = timeAllowed;
      this.timeElapsed = timeElapsed;
      this.lastDocCollected = lastDocCollected;
    }
    public long getTimeAllowed() {
      return timeAllowed;
    }
    public long getTimeElapsed() {
      return timeElapsed;
    }
    public int getLastDocCollected() {
      return lastDocCollected;
    }
  }

  private long t0 = Long.MIN_VALUE;
  private long timeout = Long.MIN_VALUE;
  private Collector collector;
  private final Counter clock;
  private final long ticksAllowed;
  private boolean greedy = false;
  private int docBase;

  public TimeLimitingCollector(final Collector collector, Counter clock, final long ticksAllowed ) {
    this.collector = collector;
    this.clock = clock;
    this.ticksAllowed = ticksAllowed;
  }
  
  public void setBaseline(long clockTime) {
    t0 = clockTime;
    timeout = t0 + ticksAllowed;
  }
  
  public void setBaseline() {
    setBaseline(clock.get());
  }
  
  public boolean isGreedy() {
    return greedy;
  }

  public void setGreedy(boolean greedy) {
    this.greedy = greedy;
  }
  
  @Override
  public void collect(final int doc) throws IOException {
    final long time = clock.get();
    if (timeout < time) {
      if (greedy) {
        //System.out.println(this+"  greedy: before failing, collecting doc: "+(docBase + doc)+"  "+(time-t0));
        collector.collect(doc);
      }
      //System.out.println(this+"  failing on:  "+(docBase + doc)+"  "+(time-t0));
      throw new TimeExceededException( timeout-t0, time-t0, docBase + doc );
    }
    //System.out.println(this+"  collecting: "+(docBase + doc)+"  "+(time-t0));
    collector.collect(doc);
  }
  
  @Override
  public void setNextReader(IndexReader reader, int base) throws IOException {
    collector.setNextReader(reader, base);
    this.docBase = base;
    if (Long.MIN_VALUE == t0) {
      setBaseline();
    }
  }
  
  @Override
  public void setScorer(Scorer scorer) throws IOException {
    collector.setScorer(scorer);
  }

  @Override
  public boolean acceptsDocsOutOfOrder() {
    return collector.acceptsDocsOutOfOrder();
  }
  
  public void setCollector(Collector collector) {
    this.collector = collector;
  }


  public static Counter getGlobalCounter() {
    return TimerThreadHolder.THREAD.counter;
  }
  
  public static TimerThread getGlobalTimerThread() {
    return TimerThreadHolder.THREAD;
  }
  
  private static final class TimerThreadHolder {
    static final TimerThread THREAD;
    static {
      THREAD = new TimerThread(Counter.newCounter(true));
      THREAD.start();
    }
  }

  public static final class TimerThread extends Thread  {
    
    public static final String THREAD_NAME = "TimeLimitedCollector timer thread";
    public static final int DEFAULT_RESOLUTION = 20;
    // NOTE: we can avoid explicit synchronization here for several reasons:
    // * updates to volatile long variables are atomic
    // * only single thread modifies this value
    // * use of volatile keyword ensures that it does not reside in
    //   a register, but in main memory (so that changes are visible to
    //   other threads).
    // * visibility of changes does not need to be instantaneous, we can
    //   afford losing a tick or two.
    //
    // See section 17 of the Java Language Specification for details.
    private volatile long time = 0;
    private volatile boolean stop = false;
    private volatile long resolution;
    final Counter counter;
    
    public TimerThread(long resolution, Counter counter) {
      super(THREAD_NAME);
      this.resolution = resolution;
      this.counter = counter;
      this.setDaemon(true);
    }
    
    public TimerThread(Counter counter) {
      this(DEFAULT_RESOLUTION, counter);
    }

    @Override
    public void run() {
      while (!stop) {
        // TODO: Use System.nanoTime() when Lucene moves to Java SE 5.
        counter.addAndGet(resolution);
        try {
          Thread.sleep( resolution );
        } catch (InterruptedException ie) {
          throw new ThreadInterruptedException(ie);
        }
      }
    }

    public long getMilliseconds() {
      return time;
    }
    
    public void stopTimer() {
      stop = true;
    }
    
    public long getResolution() {
      return resolution;
    }
    
    public void setResolution(long resolution) {
      this.resolution = Math.max(resolution, 5); // 5 milliseconds is about the minimum reasonable time for a Object.wait(long) call.
    }
  }
  
}
