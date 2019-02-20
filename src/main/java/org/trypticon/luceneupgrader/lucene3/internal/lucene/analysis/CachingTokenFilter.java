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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.AttributeSource;


public final class CachingTokenFilter extends TokenFilter {
  private List<AttributeSource.State> cache = null;
  private Iterator<AttributeSource.State> iterator = null; 
  private AttributeSource.State finalState;
  
  public CachingTokenFilter(TokenStream input) {
    super(input);
  }
  
  @Override
  public final boolean incrementToken() throws IOException {
    if (cache == null) {
      // fill cache lazily
      cache = new LinkedList<AttributeSource.State>();
      fillCache();
      iterator = cache.iterator();
    }
    
    if (!iterator.hasNext()) {
      // the cache is exhausted, return false
      return false;
    }
    // Since the TokenFilter can be reset, the tokens need to be preserved as immutable.
    restoreState(iterator.next());
    return true;
  }
  
  @Override
  public final void end() throws IOException {
    if (finalState != null) {
      restoreState(finalState);
    }
  }

  @Override
  public void reset() throws IOException {
    if(cache != null) {
      iterator = cache.iterator();
    }
  }
  
  private void fillCache() throws IOException {
    while(input.incrementToken()) {
      cache.add(captureState());
    }
    // capture final state
    input.end();
    finalState = captureState();
  }

}
