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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.blocktree.BlockTreeTermsReader; // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.OrdTermState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TermState;

public class BlockTermState extends OrdTermState {
  public int docFreq;
  public long totalTermFreq;

  public int termBlockOrd;
  // TODO: update BTR to nuke this
  public long blockFilePointer;


  public boolean isRealTerm = true;

  protected BlockTermState() {
  }

  @Override
  public void copyFrom(TermState _other) {
    assert _other instanceof BlockTermState : "can not copy from " + _other.getClass().getName();
    BlockTermState other = (BlockTermState) _other;
    super.copyFrom(_other);
    docFreq = other.docFreq;
    totalTermFreq = other.totalTermFreq;
    termBlockOrd = other.termBlockOrd;
    blockFilePointer = other.blockFilePointer;
    isRealTerm = other.isRealTerm;
  }

  @Override
  public boolean isRealTerm() {
    return isRealTerm;
  }

  @Override
  public String toString() {
    return "docFreq=" + docFreq + " totalTermFreq=" + totalTermFreq + " termBlockOrd=" + termBlockOrd + " blockFP=" + blockFilePointer + " isRealTerm=" + isRealTerm;
  }
}
