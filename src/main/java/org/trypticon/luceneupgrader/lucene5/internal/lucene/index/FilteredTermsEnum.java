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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.index;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.AttributeSource;

public abstract class FilteredTermsEnum extends TermsEnum {

  private BytesRef initialSeekTerm;
  private boolean doSeek;

  protected BytesRef actualTerm;

  protected final TermsEnum tenum;


  protected static enum AcceptStatus {
    YES,
    YES_AND_SEEK,
    NO,
    NO_AND_SEEK,
    END
  };
  

  protected abstract AcceptStatus accept(BytesRef term) throws IOException;

  public FilteredTermsEnum(final TermsEnum tenum) {
    this(tenum, true);
  }

  public FilteredTermsEnum(final TermsEnum tenum, final boolean startWithSeek) {
    assert tenum != null;
    this.tenum = tenum;
    doSeek = startWithSeek;
  }

  protected final void setInitialSeekTerm(BytesRef term) {
    this.initialSeekTerm = term;
  }
  

  protected BytesRef nextSeekTerm(final BytesRef currentTerm) throws IOException {
    final BytesRef t = initialSeekTerm;
    initialSeekTerm = null;
    return t;
  }

  @Override
  public AttributeSource attributes() {
    return tenum.attributes();
  }
  
  @Override
  public BytesRef term() throws IOException {
    return tenum.term();
  }

  @Override
  public int docFreq() throws IOException {
    return tenum.docFreq();
  }

  @Override
  public long totalTermFreq() throws IOException {
    return tenum.totalTermFreq();
  }


  @Override
  public boolean seekExact(BytesRef term) throws IOException {
    throw new UnsupportedOperationException(getClass().getName()+" does not support seeking");
  }


  @Override
  public SeekStatus seekCeil(BytesRef term) throws IOException {
    throw new UnsupportedOperationException(getClass().getName()+" does not support seeking");
  }


  @Override
  public void seekExact(long ord) throws IOException {
    throw new UnsupportedOperationException(getClass().getName()+" does not support seeking");
  }

  @Override
  public long ord() throws IOException {
    return tenum.ord();
  }

  @Override
  public PostingsEnum postings(PostingsEnum reuse, int flags) throws IOException {
    return tenum.postings(reuse, flags);
  }
  

  @Override
  public void seekExact(BytesRef term, TermState state) throws IOException {
    throw new UnsupportedOperationException(getClass().getName()+" does not support seeking");
  }
  
  @Override
  public TermState termState() throws IOException {
    assert tenum != null;
    return tenum.termState();
  }

  @SuppressWarnings("fallthrough")
  @Override
  public BytesRef next() throws IOException {
    //System.out.println("FTE.next doSeek=" + doSeek);
    //new Throwable().printStackTrace(System.out);
    for (;;) {
      // Seek or forward the iterator
      if (doSeek) {
        doSeek = false;
        final BytesRef t = nextSeekTerm(actualTerm);
        //System.out.println("  seek to t=" + (t == null ? "null" : t.utf8ToString()) + " tenum=" + tenum);
        // Make sure we always seek forward:
        assert actualTerm == null || t == null || t.compareTo(actualTerm) > 0: "curTerm=" + actualTerm + " seekTerm=" + t;
        if (t == null || tenum.seekCeil(t) == SeekStatus.END) {
          // no more terms to seek to or enum exhausted
          //System.out.println("  return null");
          return null;
        }
        actualTerm = tenum.term();
        //System.out.println("  got term=" + actualTerm.utf8ToString());
      } else {
        actualTerm = tenum.next();
        if (actualTerm == null) {
          // enum exhausted
          return null;
        }
      }
      
      // check if term is accepted
      switch (accept(actualTerm)) {
        case YES_AND_SEEK:
          doSeek = true;
          // term accepted, but we need to seek so fall-through
        case YES:
          // term accepted
          return actualTerm;
        case NO_AND_SEEK:
          // invalid term, seek next time
          doSeek = true;
          break;
        case END:
          // we are supposed to end the enum
          return null;
        // NO: we just fall through and iterate again
      }
    }
  }

}
