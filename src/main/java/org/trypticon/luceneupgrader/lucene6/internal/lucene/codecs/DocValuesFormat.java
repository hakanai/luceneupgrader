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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs;


import java.io.IOException;
import java.util.ServiceLoader;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.NamedSPILoader;


public abstract class DocValuesFormat implements NamedSPILoader.NamedSPI {
  
  private static final class Holder {
    private static final NamedSPILoader<DocValuesFormat> LOADER = new NamedSPILoader<>(DocValuesFormat.class);
    
    private Holder() {}
    
    static NamedSPILoader<DocValuesFormat> getLoader() {
      if (LOADER == null) {
        throw new IllegalStateException("You tried to lookup a DocValuesFormat by name before all formats could be initialized. "+
          "This likely happens if you call DocValuesFormat#forName from a DocValuesFormat's ctor.");
      }
      return LOADER;
    }
  }
  

  private final String name;

  protected DocValuesFormat(String name) {
    NamedSPILoader.checkServiceName(name);
    this.name = name;
  }

  public abstract DocValuesConsumer fieldsConsumer(SegmentWriteState state) throws IOException;


  public abstract DocValuesProducer fieldsProducer(SegmentReadState state) throws IOException;

  @Override
  public final String getName() {
    return name;
  }
  
  @Override
  public String toString() {
    return "DocValuesFormat(name=" + name + ")";
  }
  
  public static DocValuesFormat forName(String name) {
    return Holder.getLoader().lookup(name);
  }
  
  public static Set<String> availableDocValuesFormats() {
    return Holder.getLoader().availableServices();
  }
  

  public static void reloadDocValuesFormats(ClassLoader classloader) {
    Holder.getLoader().reload(classloader);
  }
}
