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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.DataInput;

public class IndexFormatTooOldException extends CorruptIndexException {


  public IndexFormatTooOldException(String resourceDesc, String version) {
    super("Format version is not supported (resource: " + resourceDesc + "): " +
        version + ". This version of Lucene only supports indexes created with release 3.0 and later.");
    assert resourceDesc != null;
  }


  public IndexFormatTooOldException(DataInput in, String version) {
    this(in.toString(), version);
  }
  

  public IndexFormatTooOldException(String resourceDesc, int version, int minVersion, int maxVersion) {
    super("Format version is not supported (resource: " + resourceDesc + "): " +
        version + " (needs to be between " + minVersion + " and " + maxVersion +
    "). This version of Lucene only supports indexes created with release 3.0 and later.");
    assert resourceDesc != null;
  }


  public IndexFormatTooOldException(DataInput in, int version, int minVersion, int maxVersion) {
    this(in.toString(), version, minVersion, maxVersion);
  }
}
