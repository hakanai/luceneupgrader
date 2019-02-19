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

import java.io.IOException;
import java.util.ServiceLoader;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.perfield.PerFieldPostingsFormat; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.NamedSPILoader;


public abstract class PostingsFormat implements NamedSPILoader.NamedSPI {

  private static final NamedSPILoader<PostingsFormat> loader =
    new NamedSPILoader<>(PostingsFormat.class);

  public static final PostingsFormat[] EMPTY = new PostingsFormat[0];


  private final String name;
  
  protected PostingsFormat(String name) {
    NamedSPILoader.checkServiceName(name);
    this.name = name;
  }

  @Override
  public final String getName() {
    return name;
  }
  
  public abstract FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException;


  public abstract FieldsProducer fieldsProducer(SegmentReadState state) throws IOException;

  @Override
  public String toString() {
    return "PostingsFormat(name=" + name + ")";
  }
  
  public static PostingsFormat forName(String name) {
    if (loader == null) {
      throw new IllegalStateException("You called PostingsFormat.forName() before all formats could be initialized. "+
          "This likely happens if you call it from a PostingsFormat's ctor.");
    }
    return loader.lookup(name);
  }
  
  public static Set<String> availablePostingsFormats() {
    if (loader == null) {
      throw new IllegalStateException("You called PostingsFormat.availablePostingsFormats() before all formats could be initialized. "+
          "This likely happens if you call it from a PostingsFormat's ctor.");
    }
    return loader.availableServices();
  }
  

  public static void reloadPostingsFormats(ClassLoader classloader) {
    loader.reload(classloader);
  }
}
