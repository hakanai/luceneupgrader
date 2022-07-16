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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search;

public enum ScoreMode {
  
  COMPLETE(true, true),

  COMPLETE_NO_SCORES(true, false),

  TOP_SCORES(false, true),

  TOP_DOCS(false, false),

  TOP_DOCS_WITH_SCORES(false, true);

  private final boolean needsScores;
  private final boolean isExhaustive;

  ScoreMode(boolean isExhaustive, boolean needsScores) {
    this.isExhaustive = isExhaustive;
    this.needsScores = needsScores;
  }

  public boolean needsScores() {
    return needsScores;
  }

  public boolean isExhaustive() {
    return isExhaustive;
  }
}
