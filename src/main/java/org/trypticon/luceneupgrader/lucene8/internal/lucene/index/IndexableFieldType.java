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


import java.util.Map;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.Analyzer; // javadocs

public interface IndexableFieldType {

  public boolean stored();
  
  // TODO: shouldn't we remove this?  Whether/how a field is
  // tokenized is an impl detail under Field?
  public boolean tokenized();

  public boolean storeTermVectors();

  public boolean storeTermVectorOffsets();

  public boolean storeTermVectorPositions();
  
  public boolean storeTermVectorPayloads();

  public boolean omitNorms();

  public IndexOptions indexOptions();

  public DocValuesType docValuesType();

  public int pointDimensionCount();

  public int pointIndexDimensionCount();

  public int pointNumBytes();

  public Map<String, String> getAttributes();
}
