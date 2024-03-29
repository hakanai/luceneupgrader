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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.util;



public interface  Bits {
  boolean get(int index);
  
  int length();

  Bits[] EMPTY_ARRAY = new Bits[0];
  
  class MatchAllBits implements Bits {
    final int len;
    
    public MatchAllBits(int len) {
      this.len = len;
    }

    @Override
    public boolean get(int index) {
      return true;
    }

    @Override
    public int length() {
      return len;
    }
  }

  class MatchNoBits implements Bits {
    final int len;
    
    public MatchNoBits(int len) {
      this.len = len;
    }

    @Override
    public boolean get(int index) {
      return false;
    }

    @Override
    public int length() {
      return len;
    }
  }
}
