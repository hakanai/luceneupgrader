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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.util;


import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexWriter; // javadocs
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SegmentInfos; // javadocs
import java.io.Closeable;

public abstract class InfoStream implements Closeable {

  public static final InfoStream NO_OUTPUT = new NoOutput();
  private static final class NoOutput extends InfoStream {
    @Override
    public void message(String component, String message) {
      assert false: "message() should not be called when isEnabled returns false";
    }
    
    @Override
    public boolean isEnabled(String component) {
      return false;
    }

    @Override
    public void close() {}
  }
  
  public abstract void message(String component, String message);
  
  public abstract boolean isEnabled(String component);
  
  private static InfoStream defaultInfoStream = NO_OUTPUT;
  
  public static synchronized InfoStream getDefault() {
    return defaultInfoStream;
  }
  

  public static synchronized void setDefault(InfoStream infoStream) {
    if (infoStream == null) {
      throw new IllegalArgumentException("Cannot set InfoStream default implementation to null. "+
        "To disable logging use InfoStream.NO_OUTPUT");
    }
    defaultInfoStream = infoStream;
  }
}
