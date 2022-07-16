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

package org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.tokenattributes.OffsetAttribute;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.AttributeSource;

public abstract class GraphTokenFilter extends TokenFilter {

  private final Deque<Token> tokenPool = new ArrayDeque<>();
  private final List<Token> currentGraph = new ArrayList<>();

  public static final int MAX_GRAPH_STACK_SIZE = 1000;

  public static final int MAX_TOKEN_CACHE_SIZE = 100;

  private Token baseToken;
  private int graphDepth;
  private int graphPos;
  private int trailingPositions = -1;
  private int finalOffsets = -1;

  private int stackSize;
  private int cacheSize;

  private final PositionIncrementAttribute posIncAtt;
  private final OffsetAttribute offsetAtt;

  public GraphTokenFilter(TokenStream input) {
    super(input);
    this.posIncAtt = input.addAttribute(PositionIncrementAttribute.class);
    this.offsetAtt = input.addAttribute(OffsetAttribute.class);
  }

  protected final boolean incrementBaseToken() throws IOException {
    stackSize = 0;
    graphDepth = 0;
    graphPos = 0;
    Token oldBase = baseToken;
    baseToken = nextTokenInStream(baseToken);
    if (baseToken == null) {
      return false;
    }
    currentGraph.clear();
    currentGraph.add(baseToken);
    baseToken.attSource.copyTo(this);
    recycleToken(oldBase);
    return true;
  }

  protected final boolean incrementGraphToken() throws IOException {
    if (graphPos < graphDepth) {
      graphPos++;
      currentGraph.get(graphPos).attSource.copyTo(this);
      return true;
    }
    Token token = nextTokenInGraph(currentGraph.get(graphDepth));
    if (token == null) {
      return false;
    }
    graphDepth++;
    graphPos++;
    currentGraph.add(graphDepth, token);
    token.attSource.copyTo(this);
    return true;
  }

  protected final boolean incrementGraph() throws IOException {
    if (baseToken == null) {
      return false;
    }
    graphPos = 0;
    for (int i = graphDepth; i >= 1; i--) {
      if (lastInStack(currentGraph.get(i)) == false) {
        currentGraph.set(i, nextTokenInStream(currentGraph.get(i)));
        for (int j = i + 1; j < graphDepth; j++) {
          currentGraph.set(j, nextTokenInGraph(currentGraph.get(j)));
        }
        if (stackSize++ > MAX_GRAPH_STACK_SIZE) {
          throw new IllegalStateException("Too many graph paths (> " + MAX_GRAPH_STACK_SIZE + ")");
        }
        currentGraph.get(0).attSource.copyTo(this);
        graphDepth = i;
        return true;
      }
    }
    return false;
  }

  public int getTrailingPositions() {
    return trailingPositions;
  }

  @Override
  public void end() throws IOException {
    if (trailingPositions == -1) {
      input.end();
      trailingPositions = posIncAtt.getPositionIncrement();
      finalOffsets = offsetAtt.endOffset();
    }
    else {
      endAttributes();
      this.posIncAtt.setPositionIncrement(trailingPositions);
      this.offsetAtt.setOffset(finalOffsets, finalOffsets);
    }
  }

  @Override
  public void reset() throws IOException {
    input.reset();
    // new attributes can be added between reset() calls, so we can't reuse
    // token objects from a previous run
    tokenPool.clear();
    cacheSize = 0;
    graphDepth = 0;
    trailingPositions = -1;
    finalOffsets = -1;
    baseToken = null;
  }

  int cachedTokenCount() {
    return cacheSize;
  }

  private Token newToken() {
    if (tokenPool.size() == 0) {
      cacheSize++;
      if (cacheSize > MAX_TOKEN_CACHE_SIZE) {
        throw new IllegalStateException("Too many cached tokens (> " + MAX_TOKEN_CACHE_SIZE + ")");
      }
      return new Token(this.cloneAttributes());
    }
    Token token = tokenPool.removeFirst();
    token.reset(input);
    return token;
  }

  private void recycleToken(Token token) {
    if (token == null)
      return;
    token.nextToken = null;
    tokenPool.add(token);
  }

  private Token nextTokenInGraph(Token token) throws IOException {
    int remaining = token.length();
    do {
      token = nextTokenInStream(token);
      if (token == null) {
        return null;
      }
      remaining -= token.posInc();
    } while (remaining > 0);
    return token;
  }

  // check if the next token in the tokenstream is at the same position as this one
  private boolean lastInStack(Token token) throws IOException {
    Token next = nextTokenInStream(token);
    return next == null || next.posInc() != 0;
  }

  private Token nextTokenInStream(Token token) throws IOException {
    if (token != null && token.nextToken != null) {
      return token.nextToken;
    }
    if (this.trailingPositions != -1) {
      // already hit the end
      return null;
    }
    if (input.incrementToken() == false) {
      input.end();
      trailingPositions = posIncAtt.getPositionIncrement();
      finalOffsets = offsetAtt.endOffset();
      return null;
    }
    if (token == null) {
      return newToken();
    }
    token.nextToken = newToken();
    return token.nextToken;
  }

  private static class Token {

    final AttributeSource attSource;
    final PositionIncrementAttribute posIncAtt;
    final PositionLengthAttribute lengthAtt;
    Token nextToken;

    Token(AttributeSource attSource) {
      this.attSource = attSource;
      this.posIncAtt = attSource.addAttribute(PositionIncrementAttribute.class);
      boolean hasLengthAtt = attSource.hasAttribute(PositionLengthAttribute.class);
      this.lengthAtt = hasLengthAtt ? attSource.addAttribute(PositionLengthAttribute.class) : null;
    }

    int posInc() {
      return this.posIncAtt.getPositionIncrement();
    }

    int length() {
      if (this.lengthAtt == null) {
        return 1;
      }
      return this.lengthAtt.getPositionLength();
    }

    void reset(AttributeSource attSource) {
      attSource.copyTo(this.attSource);
      this.nextToken = null;
    }

    @Override
    public String toString() {
      return attSource.toString();
    }
  }

}
