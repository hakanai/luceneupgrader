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

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsEnum;

public abstract class Scorer extends DocsEnum {
  // TODO can we clean this up?
  protected final Weight weight;

  protected Scorer(Weight weight) {
    this.weight = weight;
  }


  public abstract float score() throws IOException;
  

  public Weight getWeight() {
    return weight;
  }
  
  public Collection<ChildScorer> getChildren() {
    return Collections.emptyList();
  }
  

  public static class ChildScorer {
    public final Scorer child;
    public final String relationship;
    
    public ChildScorer(Scorer child, String relationship) {
      this.child = child;
      this.relationship = relationship;
    }
  }
}
