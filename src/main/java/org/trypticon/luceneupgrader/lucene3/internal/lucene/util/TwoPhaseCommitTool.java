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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.util;

import java.io.IOException;
import java.util.Map;

public final class TwoPhaseCommitTool {

  public static final class TwoPhaseCommitWrapper implements TwoPhaseCommit {

    private final TwoPhaseCommit tpc;
    private  final Map<String, String> commitData;

    public TwoPhaseCommitWrapper(TwoPhaseCommit tpc, Map<String, String> commitData) {
      this.tpc = tpc;
      this.commitData = commitData;
    }

    public void prepareCommit() throws IOException {
      prepareCommit(commitData);
    }

    public void prepareCommit(Map<String, String> commitData) throws IOException {
      tpc.prepareCommit(this.commitData);
    }

    public void commit() throws IOException {
      commit(commitData);
    }

    public void commit(Map<String, String> commitData) throws IOException {
      tpc.commit(this.commitData);
    }

    public void rollback() throws IOException {
      tpc.rollback();
    }
  }
  
  public static class PrepareCommitFailException extends IOException {
    
    public PrepareCommitFailException(Throwable cause, TwoPhaseCommit obj) {
      super("prepareCommit() failed on " + obj);
      initCause(cause);
    }
    
  }

  public static class CommitFailException extends IOException {
    
    public CommitFailException(Throwable cause, TwoPhaseCommit obj) {
      super("commit() failed on " + obj);
      initCause(cause);
    }
    
  }

  private static void rollback(TwoPhaseCommit... objects) {
    for (TwoPhaseCommit tpc : objects) {
      // ignore any exception that occurs during rollback - we want to ensure
      // all objects are rolled-back.
      if (tpc != null) {
        try {
          tpc.rollback();
        } catch (Throwable t) {}
      }
    }
  }

  public static void execute(TwoPhaseCommit... objects)
      throws PrepareCommitFailException, CommitFailException {
    TwoPhaseCommit tpc = null;
    try {
      // first, all should successfully prepareCommit()
      for (int i = 0; i < objects.length; i++) {
        tpc = objects[i];
        if (tpc != null) {
          tpc.prepareCommit();
        }
      }
    } catch (Throwable t) {
      // first object that fails results in rollback all of them and
      // throwing an exception.
      rollback(objects);
      throw new PrepareCommitFailException(t, tpc);
    }
    
    // If all successfully prepareCommit(), attempt the actual commit()
    try {
      for (int i = 0; i < objects.length; i++) {
        tpc = objects[i];
        if (tpc != null) {
          tpc.commit();
        }
      }
    } catch (Throwable t) {
      // first object that fails results in rollback all of them and
      // throwing an exception.
      rollback(objects);
      throw new CommitFailException(t, tpc);
    }
  }

}
