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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.index;

import java.io.IOException;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.DataInput;

public class IndexFormatTooOldException extends IOException {

  private final String resourceDescription;
  private final String reason;
  private final Integer version;
  private final Integer minVersion;
  private final Integer maxVersion;



  public IndexFormatTooOldException(String resourceDescription, String reason) {
    super("Format version is not supported (resource " + resourceDescription + "): " +
        reason + ". This version of Lucene only supports indexes created with release 5.0 and later.");
    this.resourceDescription = resourceDescription;
    this.reason = reason;
    this.version = null;
    this.minVersion = null;
    this.maxVersion = null;

  }


  public IndexFormatTooOldException(DataInput in, String reason) {
    this(Objects.toString(in), reason);
  }


  public IndexFormatTooOldException(String resourceDescription, int version, int minVersion, int maxVersion) {
    super("Format version is not supported (resource " + resourceDescription + "): " +
        version + " (needs to be between " + minVersion + " and " + maxVersion +
        "). This version of Lucene only supports indexes created with release 5.0 and later.");
    this.resourceDescription = resourceDescription;
    this.version = version;
    this.minVersion = minVersion;
    this.maxVersion = maxVersion;
    this.reason = null;
  }


  public IndexFormatTooOldException(DataInput in, int version, int minVersion, int maxVersion) {
    this(Objects.toString(in), version, minVersion, maxVersion);
  }

  public String getResourceDescription() {
    return resourceDescription;
  }

  public String getReason() {
    return reason;
  }

  public Integer getVersion() {
    return version;
  }

  public Integer getMaxVersion() {
    return maxVersion;
  }

  public Integer getMinVersion() {
    return minVersion;
  }
}
