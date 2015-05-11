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

import org.apache.lucene.search.Query;
import org.apache.lucene.util.RamUsageEstimator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/* Holds buffered deletes, by docID, term or query for a
 * single segment. This is used to hold buffered pending
 * deletes against the to-be-flushed segment.  Once the
 * deletes are pushed (on flush in DocumentsWriter), these
 * deletes are converted to a FrozenDeletes instance. */

// NOTE: we are sync'd by BufferedDeletes, ie, all access to
// instances of this class is via sync'd methods on
// BufferedDeletes
class BufferedDeletes {

  /* Rough logic: del docIDs are List<Integer>.  Say list
     allocates ~2X size (2*POINTER).  Integer is OBJ_HEADER
     + int */
  final static int BYTES_PER_DEL_DOCID = 2*RamUsageEstimator.NUM_BYTES_OBJECT_REF + RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + RamUsageEstimator.NUM_BYTES_INT;

  final AtomicInteger numTermDeletes = new AtomicInteger();
  final Map<Term,Integer> terms = new HashMap<Term,Integer>();
  final Map<Query,Integer> queries = new HashMap<Query,Integer>();
  final List<Integer> docIDs = new ArrayList<Integer>();

  public static final Integer MAX_INT = Integer.MAX_VALUE;

  final AtomicLong bytesUsed = new AtomicLong();

  private final static boolean VERBOSE_DELETES = false;

  long gen;

  @Override
  public String toString() {
    if (VERBOSE_DELETES) {
      return "gen=" + gen + " numTerms=" + numTermDeletes + ", terms=" + terms
        + ", queries=" + queries + ", docIDs=" + docIDs + ", bytesUsed="
        + bytesUsed;
    } else {
      String s = "gen=" + gen;
      if (numTermDeletes.get() != 0) {
        s += " " + numTermDeletes.get() + " deleted terms (unique count=" + terms.size() + ")";
      }
      if (queries.size() != 0) {
        s += " " + queries.size() + " deleted queries";
      }
      if (docIDs.size() != 0) {
        s += " " + docIDs.size() + " deleted docIDs";
      }
      if (bytesUsed.get() != 0) {
        s += " bytesUsed=" + bytesUsed.get();
      }

      return s;
    }
  }

  void clear() {
    terms.clear();
    queries.clear();
    docIDs.clear();
    numTermDeletes.set(0);
    bytesUsed.set(0);
  }

  boolean any() {
    return terms.size() > 0 || docIDs.size() > 0 || queries.size() > 0;
  }
}
