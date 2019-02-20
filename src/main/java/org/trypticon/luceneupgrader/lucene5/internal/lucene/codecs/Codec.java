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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs;


import java.util.Objects;
import java.util.Set;
import java.util.ServiceLoader; // javadocs

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexWriterConfig; // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.NamedSPILoader;

public abstract class Codec implements NamedSPILoader.NamedSPI {

  private static final class Holder {
    private static final NamedSPILoader<Codec> LOADER = new NamedSPILoader<>(Codec.class);
    
    private Holder() {}
    
    static NamedSPILoader<Codec> getLoader() {
      if (LOADER == null) {
        throw new IllegalStateException("You tried to lookup a Codec by name before all Codecs could be initialized. "+
          "This likely happens if you call Codec#forName from a Codec's ctor.");
      }
      return LOADER;
    }
    
    // TODO: should we use this, or maybe a system property is better?
    static Codec defaultCodec = LOADER.lookup("Lucene54");
  }

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
  
  public abstract CompoundFormat compoundFormat();
  
  public static Codec forName(String name) {
    return Holder.getLoader().lookup(name);
  }
  
  public static Set<String> availableCodecs() {
    return Holder.getLoader().availableServices();
  }
  

  public static void reloadCodecs(ClassLoader classloader) {
    Holder.getLoader().reload(classloader);
  }
    

  public static Codec getDefault() {
    if (Holder.defaultCodec == null) {
      throw new IllegalStateException("You tried to lookup the default Codec before all Codecs could be initialized. "+
        "This likely happens if you try to get it from a Codec's ctor.");
    }
    return Holder.defaultCodec;
  }
  

  public static void setDefault(Codec codec) {
    Holder.defaultCodec = Objects.requireNonNull(codec);
  }

  @Override
  public String toString() {
    return name;
  }
}
