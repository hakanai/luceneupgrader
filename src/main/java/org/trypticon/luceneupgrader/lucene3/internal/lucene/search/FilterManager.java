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

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ThreadInterruptedException;

@Deprecated
public class FilterManager {

  protected static FilterManager manager;
  
  protected static final int  DEFAULT_CACHE_CLEAN_SIZE = 100;
  protected static final long DEFAULT_CACHE_SLEEP_TIME = 1000 * 60 * 10;

  protected Map<Integer,FilterItem>           cache;
  protected int           cacheCleanSize;
  protected long          cleanSleepTime;
  protected FilterCleaner filterCleaner;

  public synchronized static FilterManager getInstance() {
    if (manager == null) {
      manager = new FilterManager();
    }
    return manager;
  }

  protected FilterManager() {
    cache            = new HashMap<Integer,FilterItem>();
    cacheCleanSize   = DEFAULT_CACHE_CLEAN_SIZE; // Let the cache get to 100 items
    cleanSleepTime   = DEFAULT_CACHE_SLEEP_TIME; // 10 minutes between cleanings

    filterCleaner   = new FilterCleaner();
    Thread fcThread = new Thread(filterCleaner);
    // set to be a Daemon so it doesn't have to be stopped
    fcThread.setDaemon(true);
    fcThread.start();
  }
  
  public void setCacheSize(int cacheCleanSize) {
    this.cacheCleanSize = cacheCleanSize;
  }

  public void setCleanThreadSleepTime(long cleanSleepTime) {
    this.cleanSleepTime  = cleanSleepTime;
  }

  public Filter getFilter(Filter filter) {
    synchronized(cache) {
      FilterItem fi = null;
      fi = cache.get(Integer.valueOf(filter.hashCode()));
      if (fi != null) {
        fi.timestamp = new Date().getTime();
        return fi.filter;
      }
      cache.put(Integer.valueOf(filter.hashCode()), new FilterItem(filter));
      return filter;
    }
  }

  protected class FilterItem {
    public Filter filter;
    public long   timestamp;

    public FilterItem (Filter filter) {        
      this.filter = filter;
      this.timestamp = new Date().getTime();
    }
  }


  protected class FilterCleaner implements Runnable  {

    private boolean running = true;
    private TreeSet<Map.Entry<Integer,FilterItem>> sortedFilterItems;

    public FilterCleaner() {
      sortedFilterItems = new TreeSet<Map.Entry<Integer,FilterItem>>(new Comparator<Map.Entry<Integer,FilterItem>>() {
        public int compare(Map.Entry<Integer,FilterItem> a, Map.Entry<Integer,FilterItem> b) {
            FilterItem fia = a.getValue();
            FilterItem fib = b.getValue();
            if ( fia.timestamp == fib.timestamp ) {
              return 0;
            }
            // smaller timestamp first
            if ( fia.timestamp < fib.timestamp ) {
              return -1;
            }
            // larger timestamp last
            return 1;
          
        }
      });
    }

    public void run () {
      while (running) {

        // sort items from oldest to newest 
        // we delete the oldest filters 
        if (cache.size() > cacheCleanSize) {
          // empty the temporary set
          sortedFilterItems.clear();
          synchronized (cache) {
            sortedFilterItems.addAll(cache.entrySet());
            Iterator<Map.Entry<Integer,FilterItem>> it = sortedFilterItems.iterator();
            int numToDelete = (int) ((cache.size() - cacheCleanSize) * 1.5);
            int counter = 0;
            // loop over the set and delete all of the cache entries not used in a while
            while (it.hasNext() && counter++ < numToDelete) {
              Map.Entry<Integer,FilterItem> entry = it.next();
              cache.remove(entry.getKey());
            }
          }
          // empty the set so we don't tie up the memory
          sortedFilterItems.clear();
        }
        // take a nap
        try {
          Thread.sleep(cleanSleepTime);
        } catch (InterruptedException ie) {
          throw new ThreadInterruptedException(ie);
        }
      }
    }
  }
}
