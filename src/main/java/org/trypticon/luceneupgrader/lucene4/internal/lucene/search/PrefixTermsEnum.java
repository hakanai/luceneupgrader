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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FilteredTermsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.StringHelper;

public class PrefixTermsEnum extends FilteredTermsEnum {

  private final BytesRef prefixRef;

  public PrefixTermsEnum(TermsEnum tenum, BytesRef prefixText) {
    super(tenum);
    setInitialSeekTerm(this.prefixRef = prefixText);
  }

  @Override
  protected AcceptStatus accept(BytesRef term) {
    if (StringHelper.startsWith(term, prefixRef)) {
      return AcceptStatus.YES;
    } else {
      return AcceptStatus.END;
    }
  }
}
