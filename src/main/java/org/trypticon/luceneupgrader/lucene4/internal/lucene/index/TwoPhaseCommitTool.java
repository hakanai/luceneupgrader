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

public final class TwoPhaseCommitTool {
  
  private TwoPhaseCommitTool() {}

  public static class PrepareCommitFailException extends IOException {

    public PrepareCommitFailException(Throwable cause, TwoPhaseCommit obj) {
      super("prepareCommit() failed on " + obj, cause);
    }
  }

  public static class CommitFailException extends IOException {

    public CommitFailException(Throwable cause, TwoPhaseCommit obj) {
      super("commit() failed on " + obj, cause);
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
