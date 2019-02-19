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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SegmentInfo;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.IOContext;

public abstract class CompoundFormat {

  public CompoundFormat() {
  }
  
  // TODO: this is very minimal. If we need more methods,
  // we can add 'producer' classes.
  
  public abstract Directory getCompoundReader(Directory dir, SegmentInfo si, IOContext context) throws IOException;
  
  public abstract void write(Directory dir, SegmentInfo si, IOContext context) throws IOException;
}
