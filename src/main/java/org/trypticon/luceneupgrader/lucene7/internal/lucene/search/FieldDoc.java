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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search;


import java.util.Arrays;

public class FieldDoc extends ScoreDoc {

  public Object[] fields;

  public FieldDoc(int doc, float score) {
    super(doc, score);
  }

  public FieldDoc(int doc, float score, Object[] fields) {
    super(doc, score);
    this.fields = fields;
  }
  
  public FieldDoc(int doc, float score, Object[] fields, int shardIndex) {
    super(doc, score, shardIndex);
    this.fields = fields;
  }
  
  // A convenience method for debugging.
  @Override
  public String toString() {
    // super.toString returns the doc and score information, so just add the
    // fields information
    StringBuilder sb = new StringBuilder(super.toString());
    sb.append(" fields=");
    sb.append(Arrays.toString(fields));
    return sb.toString();
  }
}
