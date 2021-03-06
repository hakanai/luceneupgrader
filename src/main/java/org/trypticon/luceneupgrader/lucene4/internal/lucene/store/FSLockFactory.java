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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.store;

import java.io.File;


public abstract class FSLockFactory extends LockFactory {

  protected File lockDir = null;

  protected final void setLockDir(File lockDir) {
    if (this.lockDir != null)
      throw new IllegalStateException("You can set the lock directory for this factory only once.");
    this.lockDir = lockDir;
  }
  
  public File getLockDir() {
    return lockDir;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "@" + lockDir;
  }
  
}
