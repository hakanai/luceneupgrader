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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.index;


import java.io.IOException;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.DataOutput;

public class CorruptIndexException extends IOException {

  private final String message;
  private final String resourceDescription;

  public CorruptIndexException(String message, DataInput input) {
    this(message, input, null);
  }

  public CorruptIndexException(String message, DataOutput output) {
    this(message, output, null);
  }
  
  public CorruptIndexException(String message, DataInput input, Throwable cause) {
    this(message, Objects.toString(input), cause);
  }

  public CorruptIndexException(String message, DataOutput output, Throwable cause) {
    this(message, Objects.toString(output), cause);
  }
  
  public CorruptIndexException(String message, String resourceDescription) {
    this(message, resourceDescription, null);
  }
  
  public CorruptIndexException(String message, String resourceDescription, Throwable cause) {
    super(Objects.toString(message) + " (resource=" + resourceDescription + ")", cause);
    this.resourceDescription = resourceDescription;
    this.message = message;
  }

  public String getResourceDescription() {
    return resourceDescription;
  }

  public String getOriginalMessage() {
    return message;
  }
}
