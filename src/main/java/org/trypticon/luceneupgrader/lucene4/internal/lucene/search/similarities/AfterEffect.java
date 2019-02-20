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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.Explanation;

public abstract class AfterEffect {
  
  public AfterEffect() {}

  public abstract float score(BasicStats stats, float tfn);
  
  public abstract Explanation explain(BasicStats stats, float tfn);

  public static final class NoAfterEffect extends AfterEffect {
    
    public NoAfterEffect() {}
    
    @Override
    public final float score(BasicStats stats, float tfn) {
      return 1f;
    }

    @Override
    public final Explanation explain(BasicStats stats, float tfn) {
      return new Explanation(1, "no aftereffect");
    }
    
    @Override
    public String toString() {
      return "";
    }
  }
  
  @Override
  public abstract String toString();
}
