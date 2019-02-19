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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs;

import java.util.Set;
import java.util.ServiceLoader; // javadocs

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexWriterConfig; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.NamedSPILoader;

public abstract class Codec implements NamedSPILoader.NamedSPI {

  private static final NamedSPILoader<Codec> loader =
    new NamedSPILoader<>(Codec.class);

  private final String name;

  protected Codec(String name) {
    NamedSPILoader.checkServiceName(name);
    this.name = name;
  }
  
  @Override
  public final String getName() {
    return name;
  }
  
  public abstract PostingsFormat postingsFormat();

  public abstract DocValuesFormat docValuesFormat();
  
  public abstract StoredFieldsFormat storedFieldsFormat();
  
  public abstract TermVectorsFormat termVectorsFormat();
  
  public abstract FieldInfosFormat fieldInfosFormat();
  
  public abstract SegmentInfoFormat segmentInfoFormat();
  
  public abstract NormsFormat normsFormat();

  public abstract LiveDocsFormat liveDocsFormat();
  
  public static Codec forName(String name) {
    if (loader == null) {
      throw new IllegalStateException("You called Codec.forName() before all Codecs could be initialized. "+
          "This likely happens if you call it from a Codec's ctor.");
    }
    return loader.lookup(name);
  }
  
  public static Set<String> availableCodecs() {
    if (loader == null) {
      throw new IllegalStateException("You called Codec.availableCodecs() before all Codecs could be initialized. "+
          "This likely happens if you call it from a Codec's ctor.");
    }
    return loader.availableServices();
  }
  

  public static void reloadCodecs(ClassLoader classloader) {
    loader.reload(classloader);
  }
  
  private static Codec defaultCodec = Codec.forName("Lucene410");
  

  // TODO: should we use this, or maybe a system property is better?
  public static Codec getDefault() {
    return defaultCodec;
  }
  

  public static void setDefault(Codec codec) {
    defaultCodec = codec;
  }

  @Override
  public String toString() {
    return name;
  }
}
