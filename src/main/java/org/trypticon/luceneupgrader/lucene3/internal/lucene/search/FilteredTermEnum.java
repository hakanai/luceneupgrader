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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.TermEnum;

public abstract class FilteredTermEnum extends TermEnum {
    protected Term currentTerm = null;
    
    protected TermEnum actualEnum = null;
    
    public FilteredTermEnum() {}

    protected abstract boolean termCompare(Term term);
    
    public abstract float difference();

    protected abstract boolean endEnum();
    
    protected void setEnum(TermEnum actualEnum) throws IOException {
        this.actualEnum = actualEnum;
        // Find the first term that matches
        Term term = actualEnum.term();
        if (term != null && termCompare(term)) 
            currentTerm = term;
        else next();
    }
    
    @Override
    public int docFreq() {
        if (currentTerm == null) return -1;
        assert actualEnum != null;
        return actualEnum.docFreq();
    }
    
    @Override
    public boolean next() throws IOException {
        if (actualEnum == null) return false; // the actual enumerator is not initialized!
        currentTerm = null;
        while (currentTerm == null) {
            if (endEnum()) return false;
            if (actualEnum.next()) {
                Term term = actualEnum.term();
                if (termCompare(term)) {
                    currentTerm = term;
                    return true;
                }
            }
            else return false;
        }
        currentTerm = null;
        return false;
    }
    
    @Override
    public Term term() {
        return currentTerm;
    }
    
    @Override
    public void close() throws IOException {
        if (actualEnum != null) actualEnum.close();
        currentTerm = null;
        actualEnum = null;
    }
}
