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

public abstract class BaseTermsEnum extends TermsEnum {

  private AttributeSource atts = null;
  
  protected BaseTermsEnum() {
    super();
  }

  @Override
  public TermState termState() throws IOException {
    return new TermState() {
      @Override
      public void copyFrom(TermState other) {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public boolean seekExact(BytesRef text) throws IOException {
    return seekCeil(text) == SeekStatus.FOUND;
  }

  @Override
  public void seekExact(BytesRef term, TermState state) throws IOException {
    if (!seekExact(term)) {
      throw new IllegalArgumentException("term=" + term + " does not exist");
    }
  }

  public AttributeSource attributes() {
    if (atts == null) {
      atts = new AttributeSource();
    }
    return atts;
  }
}
