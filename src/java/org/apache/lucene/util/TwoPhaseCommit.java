package org.apache.lucene.util;

import java.io.IOException;
import java.util.Map;

/**
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

/**
 * An interface for implementations that support 2-phase commit. You can use
 * {@code TwoPhaseCommitTool} to execute a 2-phase commit algorithm over several
 * {@code TwoPhaseCommit}s.
 * 
 * @lucene.experimental
 */
public interface TwoPhaseCommit {

  /**
   * Like {@code #commit()}, but takes an additional commit data to be included
   * w/ the commit.
   * <p>
   * <b>NOTE:</b> some implementations may not support any custom data to be
   * included w/ the commit and may discard it altogether. Consult the actual
   * implementation documentation for verifying if this is supported.
   * 
   *
   */
  void prepareCommit(Map<String, String> commitData) throws IOException;

  /**
   * Like {@code #commit()}, but takes an additional commit data to be included
   * w/ the commit.
   * 
   *
   *
   */
  void commit(Map<String, String> commitData) throws IOException;

}
