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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Attribute;


@Deprecated
public interface TermAttribute extends Attribute {

  public String term();
  

  public void setTermBuffer(char[] buffer, int offset, int length);


  public void setTermBuffer(String buffer);


  public void setTermBuffer(String buffer, int offset, int length);
  

  public char[] termBuffer();


  public char[] resizeTermBuffer(int newSize);

  public int termLength();
  

  public void setTermLength(int length);
}
