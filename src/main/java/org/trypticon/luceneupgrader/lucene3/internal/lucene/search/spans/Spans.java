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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search.spans;

import java.io.IOException;
import java.util.Collection;


public abstract class Spans {
  public abstract boolean next() throws IOException;


  public abstract boolean skipTo(int target) throws IOException;

  public abstract int doc();

  public abstract int start();

  public abstract int end();
  
  // TODO: Remove warning after API has been finalized
  public abstract Collection<byte[]> getPayload() throws IOException;

  public abstract boolean isPayloadAvailable();

}
