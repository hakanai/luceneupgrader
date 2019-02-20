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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.queryParser.QueryParser; // for javadoc


public abstract class FilteringTokenFilter extends TokenFilter {

  private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
  private boolean enablePositionIncrements; // no init needed, as ctor enforces setting value!
  private boolean first = true; // only used when not preserving gaps

  public FilteringTokenFilter(boolean enablePositionIncrements, TokenStream input){
    super(input);
    this.enablePositionIncrements = enablePositionIncrements;
  }

  protected abstract boolean accept() throws IOException;

  @Override
  public final boolean incrementToken() throws IOException {
    if (enablePositionIncrements) {
      int skippedPositions = 0;
      while (input.incrementToken()) {
        if (accept()) {
          if (skippedPositions != 0) {
            posIncrAtt.setPositionIncrement(posIncrAtt.getPositionIncrement() + skippedPositions);
          }
          return true;
        }
        skippedPositions += posIncrAtt.getPositionIncrement();
      }
    } else {
      while (input.incrementToken()) {
        if (accept()) {
          if (first) {
            // first token having posinc=0 is illegal.
            if (posIncrAtt.getPositionIncrement() == 0) {
              posIncrAtt.setPositionIncrement(1);
            }
            first = false;
          }
          return true;
        }
      }
    }
    // reached EOS -- return false
    return false;
  }

  @Override
  public void reset() throws IOException {
    super.reset();
    first = true;
  }


  public boolean getEnablePositionIncrements() {
    return enablePositionIncrements;
  }


  public void setEnablePositionIncrements(boolean enable) {
    this.enablePositionIncrements = enable;
  }
}
