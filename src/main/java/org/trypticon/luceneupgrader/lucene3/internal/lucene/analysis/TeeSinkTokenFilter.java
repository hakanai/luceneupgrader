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
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.AttributeImpl;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.AttributeSource;

public final class TeeSinkTokenFilter extends TokenFilter {
  private final List<WeakReference<SinkTokenStream>> sinks = new LinkedList<WeakReference<SinkTokenStream>>();
  

  public TeeSinkTokenFilter(TokenStream input) {
    super(input);
  }


  public SinkTokenStream newSinkTokenStream() {
    return newSinkTokenStream(ACCEPT_ALL_FILTER);
  }
  

  public SinkTokenStream newSinkTokenStream(SinkFilter filter) {
    SinkTokenStream sink = new SinkTokenStream(this.cloneAttributes(), filter);
    this.sinks.add(new WeakReference<SinkTokenStream>(sink));
    return sink;
  }
  

  public void addSinkTokenStream(final SinkTokenStream sink) {
    // check that sink has correct factory
    if (!this.getAttributeFactory().equals(sink.getAttributeFactory())) {
      throw new IllegalArgumentException("The supplied sink is not compatible to this tee");
    }
    // add eventually missing attribute impls to the existing sink
    for (Iterator<AttributeImpl> it = this.cloneAttributes().getAttributeImplsIterator(); it.hasNext(); ) {
      sink.addAttributeImpl(it.next());
    }
    this.sinks.add(new WeakReference<SinkTokenStream>(sink));
  }
  

  public void consumeAllTokens() throws IOException {
    while (incrementToken()) {}
  }
  
  @Override
  public boolean incrementToken() throws IOException {
    if (input.incrementToken()) {
      // capture state lazily - maybe no SinkFilter accepts this state
      AttributeSource.State state = null;
      for (WeakReference<SinkTokenStream> ref : sinks) {
        final SinkTokenStream sink = ref.get();
        if (sink != null) {
          if (sink.accept(this)) {
            if (state == null) {
              state = this.captureState();
            }
            sink.addState(state);
          }
        }
      }
      return true;
    }
    
    return false;
  }
  
  @Override
  public final void end() throws IOException {
    super.end();
    AttributeSource.State finalState = captureState();
    for (WeakReference<SinkTokenStream> ref : sinks) {
      final SinkTokenStream sink = ref.get();
      if (sink != null) {
        sink.setFinalState(finalState);
      }
    }
  }
  

  public static abstract class SinkFilter {

    public abstract boolean accept(AttributeSource source);
    

    public void reset() throws IOException {
      // nothing to do; can be overridden
    }
  }
  

  public static final class SinkTokenStream extends TokenStream {
    private final List<AttributeSource.State> cachedStates = new LinkedList<AttributeSource.State>();
    private AttributeSource.State finalState;
    private Iterator<AttributeSource.State> it = null;
    private SinkFilter filter;
    
    private SinkTokenStream(AttributeSource source, SinkFilter filter) {
      super(source);
      this.filter = filter;
    }
    
    private boolean accept(AttributeSource source) {
      return filter.accept(source);
    }
    
    private void addState(AttributeSource.State state) {
      if (it != null) {
        throw new IllegalStateException("The tee must be consumed before sinks are consumed.");
      }
      cachedStates.add(state);
    }
    
    private void setFinalState(AttributeSource.State finalState) {
      this.finalState = finalState;
    }
    
    @Override
    public final boolean incrementToken() throws IOException {
      // lazy init the iterator
      if (it == null) {
        it = cachedStates.iterator();
      }
    
      if (!it.hasNext()) {
        return false;
      }
      
      AttributeSource.State state = it.next();
      restoreState(state);
      return true;
    }
  
    @Override
    public final void end() throws IOException {
      if (finalState != null) {
        restoreState(finalState);
      }
    }
    
    @Override
    public final void reset() {
      it = cachedStates.iterator();
    }
  }
    
  private static final SinkFilter ACCEPT_ALL_FILTER = new SinkFilter() {
    @Override
    public boolean accept(AttributeSource source) {
      return true;
    }
  };
  
}
