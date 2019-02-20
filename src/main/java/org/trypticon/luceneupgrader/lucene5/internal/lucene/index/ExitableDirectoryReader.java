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


import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FilterLeafReader.FilterFields;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FilterLeafReader.FilterTerms;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FilterLeafReader.FilterTermsEnum;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.automaton.CompiledAutomaton;

import java.io.IOException;


public class ExitableDirectoryReader extends FilterDirectoryReader {
  
  private QueryTimeout queryTimeout;

  @SuppressWarnings("serial")
  public static class ExitingReaderException extends RuntimeException {

    ExitingReaderException(String msg) {
      super(msg);
    }
  }

  public static class ExitableSubReaderWrapper extends SubReaderWrapper {
    private QueryTimeout queryTimeout;

    public ExitableSubReaderWrapper(QueryTimeout queryTimeout) {
      this.queryTimeout = queryTimeout;
    }

    @Override
    public LeafReader wrap(LeafReader reader) {
      return new ExitableFilterAtomicReader(reader, queryTimeout);
    }
  }

  public static class ExitableFilterAtomicReader extends FilterLeafReader {

    private QueryTimeout queryTimeout;
    
    public ExitableFilterAtomicReader(LeafReader in, QueryTimeout queryTimeout) {
      super(in);
      this.queryTimeout = queryTimeout;
    }

    @Override
    public Fields fields() throws IOException {
      return new ExitableFields(super.fields(), queryTimeout);
    }
    
    @Override
    public Object getCoreCacheKey() {
      return in.getCoreCacheKey();  
    }
    
    @Override
    public Object getCombinedCoreAndDeletesKey() {
      return in.getCombinedCoreAndDeletesKey();
    }
    
  }

  public static class ExitableFields extends FilterFields {
    
    private QueryTimeout queryTimeout;

    public ExitableFields(Fields fields, QueryTimeout queryTimeout) {
      super(fields);
      this.queryTimeout = queryTimeout;
    }

    @Override
    public Terms terms(String field) throws IOException {
      Terms terms = in.terms(field);
      if (terms == null) {
        return null;
      }
      return new ExitableTerms(terms, queryTimeout);
    }
  }

  public static class ExitableTerms extends FilterTerms {

    private QueryTimeout queryTimeout;
    
    public ExitableTerms(Terms terms, QueryTimeout queryTimeout) {
      super(terms);
      this.queryTimeout = queryTimeout;
    }

    @Override
    public TermsEnum intersect(CompiledAutomaton compiled, BytesRef startTerm) throws IOException {
      return new ExitableTermsEnum(in.intersect(compiled, startTerm), queryTimeout);
    }

    @Override
    public TermsEnum iterator() throws IOException {
      return new ExitableTermsEnum(in.iterator(), queryTimeout);
    }
  }

  public static class ExitableTermsEnum extends FilterTermsEnum {

    private QueryTimeout queryTimeout;
    
    public ExitableTermsEnum(TermsEnum termsEnum, QueryTimeout queryTimeout) {
      super(termsEnum);
      this.queryTimeout = queryTimeout;
      checkAndThrow();
    }

    private void checkAndThrow() {
      if (queryTimeout.shouldExit()) {
        throw new ExitingReaderException("The request took too long to iterate over terms. Timeout: " 
            + queryTimeout.toString()
            + ", TermsEnum=" + in
        );
      } else if (Thread.interrupted()) {
        throw new ExitingReaderException("Interrupted while iterating over terms. TermsEnum=" + in);
      }
    }

    @Override
    public BytesRef next() throws IOException {
      // Before every iteration, check if the iteration should exit
      checkAndThrow();
      return in.next();
    }
  }

  public ExitableDirectoryReader(DirectoryReader in, QueryTimeout queryTimeout) throws IOException {
    super(in, new ExitableSubReaderWrapper(queryTimeout));
    this.queryTimeout = queryTimeout;
  }

  @Override
  protected DirectoryReader doWrapDirectoryReader(DirectoryReader in) throws IOException {
    return new ExitableDirectoryReader(in, queryTimeout);
  }

  public static DirectoryReader wrap(DirectoryReader in, QueryTimeout queryTimeout) throws IOException {
    return new ExitableDirectoryReader(in, queryTimeout);
  }

  @Override
  public String toString() {
    return "ExitableDirectoryReader(" + in.toString() + ")";
  }
}


