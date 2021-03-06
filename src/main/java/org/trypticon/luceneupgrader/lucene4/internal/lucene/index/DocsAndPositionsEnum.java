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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;

public abstract class DocsAndPositionsEnum extends DocsEnum {
  
  public static final int FLAG_OFFSETS = 0x1;

  public static final int FLAG_PAYLOADS = 0x2;

  protected DocsAndPositionsEnum() {
  }


  public abstract int nextPosition() throws IOException;

  public abstract int startOffset() throws IOException;

  public abstract int endOffset() throws IOException;


  public abstract BytesRef getPayload() throws IOException;
}
