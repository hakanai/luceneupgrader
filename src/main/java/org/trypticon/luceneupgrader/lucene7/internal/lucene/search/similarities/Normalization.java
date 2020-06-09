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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search.similarities;


import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Explanation;

public abstract class Normalization {
  
  public Normalization() {}

  public abstract float tfn(BasicStats stats, float tf, float len);
  
  public Explanation explain(BasicStats stats, float tf, float len) {
    return Explanation.match(
        tfn(stats, tf, len),
        getClass().getSimpleName() + ", computed from: ",
        Explanation.match(tf, "tf"),
        Explanation.match(stats.getAvgFieldLength(), "avgFieldLength"),
        Explanation.match(len, "len"));
  }

  public static final class NoNormalization extends Normalization {
    
    public NoNormalization() {}
    
    @Override
    public final float tfn(BasicStats stats, float tf, float len) {
      return tf;
    }

    @Override
    public final Explanation explain(BasicStats stats, float tf, float len) {
      return Explanation.match(1, "no normalization");
    }
    
    @Override
    public String toString() {
      return "";
    }
  }
  
  @Override
  public abstract String toString();
}
