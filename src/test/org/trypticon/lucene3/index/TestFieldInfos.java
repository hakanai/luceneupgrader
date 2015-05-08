package org.trypticon.lucene3.index;

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

import org.trypticon.lucene3.util.LuceneTestCase;
import org.trypticon.lucene3.document.Document;
import org.trypticon.lucene3.index.FieldInfo.IndexOptions;
import org.trypticon.lucene3.store.Directory;
import org.trypticon.lucene3.store.IndexOutput;

import java.io.IOException;

//import org.cnlp.utils.properties.ResourceBundleHelper;

public class TestFieldInfos extends LuceneTestCase {

  private Document testDoc = new Document();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    DocHelper.setupDoc(testDoc);
  }

  public void test() throws IOException {
    //Positive test of FieldInfos
    assertTrue(testDoc != null);
    FieldInfos fieldInfos = new FieldInfos();
    fieldInfos.add(testDoc);
    //Since the complement is stored as well in the fields map
    assertTrue(fieldInfos.size() == DocHelper.all.size()); //this is all b/c we are using the no-arg constructor
    Directory dir = newDirectory();
    String name = "testFile";
    IndexOutput output = dir.createOutput(name);
    assertTrue(output != null);
    //Use a RAMOutputStream
    
      fieldInfos.write(output);
      output.close();
      assertTrue(dir.fileLength(name) > 0);
      FieldInfos readIn = new FieldInfos(dir, name);
      assertTrue(fieldInfos.size() == readIn.size());
      FieldInfo info = readIn.fieldInfo("textField1");
      assertTrue(info != null);
      assertTrue(info.storeTermVector == false);
      assertTrue(info.omitNorms == false);

      info = readIn.fieldInfo("textField2");
      assertTrue(info != null);
      assertTrue(info.storeTermVector == true);
      assertTrue(info.omitNorms == false);

      info = readIn.fieldInfo("textField3");
      assertTrue(info != null);
      assertTrue(info.storeTermVector == false);
      assertTrue(info.omitNorms == true);

      info = readIn.fieldInfo("omitNorms");
      assertTrue(info != null);
      assertTrue(info.storeTermVector == false);
      assertTrue(info.omitNorms == true);

      dir.close();
  }
}
