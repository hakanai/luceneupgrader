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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search.similarities;


import java.util.List;
import java.util.Locale;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.CollectionStatistics;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.TermStatistics;

public abstract class LMSimilarity extends SimilarityBase {
  protected final CollectionModel collectionModel;
  
  public LMSimilarity(CollectionModel collectionModel) {
    this.collectionModel = collectionModel;
  }
  
  public LMSimilarity() {
    this(new DefaultCollectionModel());
  }
  
  @Override
  protected BasicStats newStats(String field, double boost) {
    return new LMStats(field, boost);
  }

  @Override
  protected void fillBasicStats(BasicStats stats, CollectionStatistics collectionStats, TermStatistics termStats) {
    super.fillBasicStats(stats, collectionStats, termStats);
    LMStats lmStats = (LMStats) stats;
    lmStats.setCollectionProbability(collectionModel.computeProbability(stats));
  }

  @Override
  protected void explain(List<Explanation> subExpls, BasicStats stats,
      double freq, double docLen) {
    subExpls.add(Explanation.match((float) collectionModel.computeProbability(stats),
                                   "collection probability"));
  }
  
  public abstract String getName();
  
  @Override
  public String toString() {
    String coll = collectionModel.getName();
    if (coll != null) {
      return String.format(Locale.ROOT, "LM %s - %s", getName(), coll);
    } else {
      return String.format(Locale.ROOT, "LM %s", getName());
    }
  }

  public static class LMStats extends BasicStats {
    private double collectionProbability;
    
    public LMStats(String field, double boost) {
      super(field, boost);
    }
    
    public final double getCollectionProbability() {
      return collectionProbability;
    }
    
    public final void setCollectionProbability(double collectionProbability) {
      this.collectionProbability = collectionProbability;
    } 
  }
  
  public static interface CollectionModel {
    public double computeProbability(BasicStats stats);
    
    public String getName();
  }
  
  public static class DefaultCollectionModel implements CollectionModel {

    public DefaultCollectionModel() {}

    @Override
    public double computeProbability(BasicStats stats) {
      return (stats.getTotalTermFreq()+1D) / (stats.getNumberOfFieldTokens()+1D);
    }
    
    @Override
    public String getName() {
      return null;
    }
  }
}
