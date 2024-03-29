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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.index;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.AttributeSource;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.BytesRefIterator;

public abstract class TermsEnum implements BytesRefIterator {

  protected TermsEnum() {
  }

  public abstract AttributeSource attributes();
  
  public enum SeekStatus {
    END,
    FOUND,
    NOT_FOUND
  };

  public abstract boolean seekExact(BytesRef text) throws IOException;

  public abstract SeekStatus seekCeil(BytesRef text) throws IOException;

  public abstract void seekExact(long ord) throws IOException;

  public abstract void seekExact(BytesRef term, TermState state) throws IOException;

  public abstract BytesRef term() throws IOException;

  public abstract long ord() throws IOException;

  public abstract int docFreq() throws IOException;

  public abstract long totalTermFreq() throws IOException;

  public final PostingsEnum postings(PostingsEnum reuse) throws IOException {
    return postings(reuse, PostingsEnum.FREQS);
  }

  public abstract PostingsEnum postings(PostingsEnum reuse, int flags) throws IOException;

  public abstract ImpactsEnum impacts(int flags) throws IOException;
  
  public abstract TermState termState() throws IOException;

  public static final TermsEnum EMPTY =
      new TermsEnum() {

        private AttributeSource atts = null;

        @Override
        public SeekStatus seekCeil(BytesRef term) {
          return SeekStatus.END;
        }

        @Override
        public void seekExact(long ord) {}

        @Override
        public BytesRef term() {
          throw new IllegalStateException("this method should never be called");
        }

        @Override
        public int docFreq() {
          throw new IllegalStateException("this method should never be called");
        }

        @Override
        public long totalTermFreq() {
          throw new IllegalStateException("this method should never be called");
        }

        @Override
        public long ord() {
          throw new IllegalStateException("this method should never be called");
        }

        @Override
        public PostingsEnum postings(PostingsEnum reuse, int flags) {
          throw new IllegalStateException("this method should never be called");
        }

        @Override
        public ImpactsEnum impacts(int flags) throws IOException {
          throw new IllegalStateException("this method should never be called");
        }

        @Override
        public BytesRef next() {
          return null;
        }

        @Override // make it synchronized here, to prevent double lazy init
        public synchronized AttributeSource attributes() {
          if (atts == null) {
            atts = new AttributeSource();
          }
          return atts;
        }

        @Override
        public boolean seekExact(BytesRef text) throws IOException {
          return seekCeil(text) == SeekStatus.FOUND;
        }

        @Override
        public TermState termState() {
          throw new IllegalStateException("this method should never be called");
        }

        @Override
        public void seekExact(BytesRef term, TermState state) {
          throw new IllegalStateException("this method should never be called");
        }
      };
}
