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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.OpenBitSet;

final class SloppyPhraseScorer extends PhraseScorer {
  
  private final int slop;
  private final int numPostings;
  private final PhraseQueue pq; // for advancing min position
  
  private int end; // current largest phrase position  

  private boolean hasRpts; // flag indicating that there are repetitions (as checked in first candidate doc)
  private boolean checkedRpts; // flag to only check for repetitions in first candidate doc
  private boolean hasMultiTermRpts; //  
  private PhrasePositions[][] rptGroups; // in each group are PPs that repeats each other (i.e. same term), sorted by (query) offset 
  private PhrasePositions[] rptStack; // temporary stack for switching colliding repeating pps 
  
  SloppyPhraseScorer(Weight weight, PhraseQuery.PostingsAndFreq[] postings, Similarity similarity,
      int slop, byte[] norms) throws IOException {
    super(weight, postings, similarity, norms);
    this.slop = slop;
    this.numPostings = postings==null ? 0 : postings.length;
    pq = new PhraseQueue(postings.length);
  }

  @Override
  protected float phraseFreq() throws IOException {
    if (!initPhrasePositions()) {
      return 0.0f;
    }
    float freq = 0.0f;
    PhrasePositions pp = pq.pop();
    int matchLength = end - pp.position;
    int next = pq.top().position; 
    while (advancePP(pp)) {
      if (hasRpts && !advanceRpts(pp)) {
        break; // pps exhausted
      }
      if (pp.position > next) { // done minimizing current match-length 
        if (matchLength <= slop) {
          freq += getSimilarity().sloppyFreq(matchLength); // score match
        }      
        pq.add(pp);
        pp = pq.pop();
        next = pq.top().position;
        matchLength = end - pp.position;
      } else {
        int matchLength2 = end - pp.position;
        if (matchLength2 < matchLength) {
          matchLength = matchLength2;
        }
      }
    }
    if (matchLength <= slop) {
      freq += getSimilarity().sloppyFreq(matchLength); // score match
    }    
    return freq;
  }

  private boolean advancePP(PhrasePositions pp) throws IOException {
    if (!pp.nextPosition()) {
      return false;
    }
    if (pp.position > end) {
      end = pp.position;
    }
    return true;
  }
  

  private boolean advanceRpts(PhrasePositions pp) throws IOException {
    if (pp.rptGroup < 0) {
      return true; // not a repeater
    }
    PhrasePositions[] rg = rptGroups[pp.rptGroup];
    OpenBitSet bits = new OpenBitSet(rg.length); // for re-queuing after collisions are resolved
    int k0 = pp.rptInd;
    int k;
    while((k=collide(pp)) >= 0) {
      pp = lesser(pp, rg[k]); // always advance the lesser of the (only) two colliding pps
      if (!advancePP(pp)) {
        return false; // exhausted
      }
      if (k != k0) { // careful: mark only those currently in the queue
        bits.set(k); // mark that pp2 need to be re-queued
      }
    }
    // collisions resolved, now re-queue
    // empty (partially) the queue until seeing all pps advanced for resolving collisions
    int n = 0;
    while (bits.cardinality() > 0) {
      PhrasePositions pp2 = pq.pop();
      rptStack[n++] = pp2;
      if (pp2.rptGroup >= 0 && bits.get(pp2.rptInd)) {
        bits.clear(pp2.rptInd);
      }
    }
    // add back to queue
    for (int i=n-1; i>=0; i--) {
      pq.add(rptStack[i]);
    }
    return true;
  }

  private PhrasePositions lesser(PhrasePositions pp, PhrasePositions pp2) {
    if (pp.position < pp2.position ||
        (pp.position == pp2.position && pp.offset < pp2.offset)) {
      return pp;
    }
    return pp2;
  }

  private int collide(PhrasePositions pp) {
    int tpPos = tpPos(pp);
    PhrasePositions[] rg = rptGroups[pp.rptGroup];
    for (int i=0; i<rg.length; i++) {
      PhrasePositions pp2 = rg[i];
      if (pp2 != pp && tpPos(pp2) == tpPos) {
        return pp2.rptInd;
      }
    }
    return -1;
  }

  private boolean initPhrasePositions() throws IOException {
    end = Integer.MIN_VALUE;
    if (!checkedRpts) {
      return initFirstTime();
    }
    if (!hasRpts) {
      initSimple();
      return true; // PPs available
    }
    return initComplex();
  }
  
  private void initSimple() throws IOException {
    //System.err.println("initSimple: doc: "+min.doc);
    pq.clear();
    // position pps and build queue from list
    for (PhrasePositions pp=min,prev=null; prev!=max; pp=(prev=pp).next) {  // iterate cyclic list: done once handled max
      pp.firstPosition();
      if (pp.position > end) {
        end = pp.position;
      }
      pq.add(pp);
    }
  }
  
  private boolean initComplex() throws IOException {
    //System.err.println("initComplex: doc: "+min.doc);
    placeFirstPositions();
    if (!advanceRepeatGroups()) {
      return false; // PPs exhausted
    }
    fillQueue();
    return true; // PPs available
  }

  private void placeFirstPositions() throws IOException {
    for (PhrasePositions pp=min,prev=null; prev!=max; pp=(prev=pp).next) { // iterate cyclic list: done once handled max
      pp.firstPosition();
    }
  }

  private void fillQueue() {
    pq.clear();
    for (PhrasePositions pp=min,prev=null; prev!=max; pp=(prev=pp).next) {  // iterate cyclic list: done once handled max
      if (pp.position > end) {
        end = pp.position;
      }
      pq.add(pp);
    }
  }


  private boolean advanceRepeatGroups() throws IOException {
    for (PhrasePositions[] rg: rptGroups) { 
      if (hasMultiTermRpts) {
        // more involved, some may not collide
        int incr;
        for (int i=0; i<rg.length; i+=incr) {
          incr = 1;
          PhrasePositions pp = rg[i];
          int k;
          while((k=collide(pp)) >= 0) {
            PhrasePositions pp2 = lesser(pp, rg[k]);
            if (!advancePP(pp2)) {  // at initialization always advance pp with higher offset
              return false; // exhausted
            }
            if (pp2.rptInd < i) { // should not happen?
              incr = 0;
              break;
            }
          }
        }
      } else {
        // simpler, we know exactly how much to advance
        for (int j=1; j<rg.length; j++) {
          for (int k=0; k<j; k++) {
            if (!rg[j].nextPosition()) {
              return false; // PPs exhausted
            }
          }
        }
      }
    }
    return true; // PPs available
  }
  

  private boolean initFirstTime() throws IOException {
    //System.err.println("initFirstTime: doc: "+min.doc);
    checkedRpts = true;
    placeFirstPositions();

    LinkedHashMap<Term,Integer> rptTerms = repeatingTerms(); 
    hasRpts = !rptTerms.isEmpty();

    if (hasRpts) {
      rptStack = new PhrasePositions[numPostings]; // needed with repetitions
      ArrayList<ArrayList<PhrasePositions>> rgs = gatherRptGroups(rptTerms);
      sortRptGroups(rgs);
      if (!advanceRepeatGroups()) {
        return false; // PPs exhausted
      }
    }
    
    fillQueue();
    return true; // PPs available
  }

  private void sortRptGroups(ArrayList<ArrayList<PhrasePositions>> rgs) {
    rptGroups = new PhrasePositions[rgs.size()][];
    Comparator<PhrasePositions> cmprtr = new Comparator<PhrasePositions>() {
      public int compare(PhrasePositions pp1, PhrasePositions pp2) {
        return pp1.offset - pp2.offset;
      }
    };
    for (int i=0; i<rptGroups.length; i++) {
      PhrasePositions[] rg = rgs.get(i).toArray(new PhrasePositions[0]);
      Arrays.sort(rg, cmprtr);
      rptGroups[i] = rg;
      for (int j=0; j<rg.length; j++) {
        rg[j].rptInd = j; // we use this index for efficient re-queuing
      }
    }
  }

  private ArrayList<ArrayList<PhrasePositions>> gatherRptGroups(LinkedHashMap<Term,Integer> rptTerms) throws IOException {
    PhrasePositions[] rpp = repeatingPPs(rptTerms); 
    ArrayList<ArrayList<PhrasePositions>> res = new ArrayList<ArrayList<PhrasePositions>>();
    if (!hasMultiTermRpts) {
      // simpler - no multi-terms - can base on positions in first doc
      for (int i=0; i<rpp.length; i++) {
        PhrasePositions pp = rpp[i];
        if (pp.rptGroup >=0) continue; // already marked as a repetition
        int tpPos = tpPos(pp);
        for (int j=i+1; j<rpp.length; j++) {
          PhrasePositions pp2 = rpp[j];
          if (
              pp2.rptGroup >=0        // already marked as a repetition
              || pp2.offset == pp.offset // not a repetition: two PPs are originally in same offset in the query! 
              || tpPos(pp2) != tpPos) {  // not a repetition
            continue; 
          }
          // a repetition
          int g = pp.rptGroup;
          if (g < 0) {
            g = res.size();
            pp.rptGroup = g;  
            ArrayList<PhrasePositions> rl = new ArrayList<PhrasePositions>(2);
            rl.add(pp);
            res.add(rl); 
          }
          pp2.rptGroup = g;
          res.get(g).add(pp2);
        }
      }
    } else {
      // more involved - has multi-terms
      ArrayList<HashSet<PhrasePositions>> tmp = new ArrayList<HashSet<PhrasePositions>>();
      ArrayList<OpenBitSet> bb = ppTermsBitSets(rpp, rptTerms);
      unionTermGroups(bb);
      HashMap<Term,Integer> tg = termGroups(rptTerms, bb);
      HashSet<Integer> distinctGroupIDs = new HashSet<Integer>(tg.values());
      for (int i=0; i<distinctGroupIDs.size(); i++) {
        tmp.add(new HashSet<PhrasePositions>());
      }
      for (PhrasePositions pp : rpp) {
        for (Term t: pp.terms) {
          if (rptTerms.containsKey(t)) {
            int g = tg.get(t);
            tmp.get(g).add(pp);
            assert pp.rptGroup==-1 || pp.rptGroup==g;  
            pp.rptGroup = g;
          }
        }
      }
      for (HashSet<PhrasePositions> hs : tmp) {
        res.add(new ArrayList<PhrasePositions>(hs));
      }
    }
    return res;
  }

  private final int tpPos(PhrasePositions pp) {
    return pp.position + pp.offset;
  }

  private LinkedHashMap<Term,Integer> repeatingTerms() {
    LinkedHashMap<Term,Integer> tord = new LinkedHashMap<Term,Integer>();
    HashMap<Term,Integer> tcnt = new HashMap<Term,Integer>();
    for (PhrasePositions pp=min,prev=null; prev!=max; pp=(prev=pp).next) { // iterate cyclic list: done once handled max
      for (Term t : pp.terms) {
        Integer cnt0 = tcnt.get(t);
        Integer cnt = cnt0==null ? new Integer(1) : new Integer(1+cnt0.intValue());
        tcnt.put(t, cnt);
        if (cnt==2) {
          tord.put(t,tord.size());
        }
      }
    }
    return tord;
  }

  private PhrasePositions[] repeatingPPs(HashMap<Term,Integer> rptTerms) {
    ArrayList<PhrasePositions> rp = new ArrayList<PhrasePositions>(); 
    for (PhrasePositions pp=min,prev=null; prev!=max; pp=(prev=pp).next) { // iterate cyclic list: done once handled max
      for (Term t : pp.terms) {
        if (rptTerms.containsKey(t)) {
          rp.add(pp);
          hasMultiTermRpts |= (pp.terms.length > 1);
          break;
        }
      }
    }
    return rp.toArray(new PhrasePositions[0]);
  }
  
  private ArrayList<OpenBitSet> ppTermsBitSets(PhrasePositions[] rpp, HashMap<Term,Integer> tord) {
    ArrayList<OpenBitSet> bb = new ArrayList<OpenBitSet>(rpp.length);
    for (PhrasePositions pp : rpp) {
      OpenBitSet b = new OpenBitSet(tord.size());
      Integer ord;
      for (Term t: pp.terms) {
        if ((ord=tord.get(t))!=null) {
          b.set(ord);
        }
      }
      bb.add(b);
    }
    return bb;
  }
  
  private void unionTermGroups(ArrayList<OpenBitSet> bb) {
    int incr;
    for (int i=0; i<bb.size()-1; i+=incr) {
      incr = 1;
      int j = i+1;
      while (j<bb.size()) {
        if (bb.get(i).intersects(bb.get(j))) {
          bb.get(i).union(bb.get(j));
          bb.remove(j);
          incr = 0;
        } else {
          ++j;
        }
      }
    }
  }
  
  private HashMap<Term,Integer> termGroups(LinkedHashMap<Term,Integer> tord, ArrayList<OpenBitSet> bb) throws IOException {
    HashMap<Term,Integer> tg = new HashMap<Term,Integer>();
    Term[] t = tord.keySet().toArray(new Term[0]);
    for (int i=0; i<bb.size(); i++) { // i is the group no.
      DocIdSetIterator bits = bb.get(i).iterator();
      int ord;
      while ((ord=bits.nextDoc())!=NO_MORE_DOCS) {
        tg.put(t[ord],i);
      }
    }
    return tg;
  }
  
//  private void printQueue(PrintStream ps, PhrasePositions ext, String title) {
//    //if (min.doc != ?) return;
//    ps.println();
//    ps.println("---- "+title);
//    ps.println("EXT: "+ext);
//    PhrasePositions[] t = new PhrasePositions[pq.size()];
//    if (pq.size()>0) {
//      t[0] = pq.pop();
//      ps.println("  " + 0 + "  " + t[0]);
//      for (int i=1; i<t.length; i++) {
//        t[i] = pq.pop();
//        assert t[i-1].position <= t[i].position;
//        ps.println("  " + i + "  " + t[i]);
//      }
//      // add them back
//      for (int i=t.length-1; i>=0; i--) {
//        pq.add(t[i]);
//      }
//    }
//  }
  
}
