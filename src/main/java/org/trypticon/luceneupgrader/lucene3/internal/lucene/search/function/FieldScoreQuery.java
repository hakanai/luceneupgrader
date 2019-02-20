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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search.function;

public class FieldScoreQuery extends ValueSourceQuery {

  public static class Type {
    
    public static final Type BYTE = new Type("byte");

    public static final Type SHORT = new Type("short");

    public static final Type INT = new Type("int");

    public static final Type FLOAT = new Type("float");

    private String typeName;
    private Type (String name) {
      this.typeName = name;
    }
    @Override
    public String toString() {
      return getClass().getName()+"::"+typeName;
    }
  }
  
  public FieldScoreQuery(String field, Type type) {
    super(getValueSource(field,type));
  }

  // create the appropriate (cached) field value source.  
  private static ValueSource getValueSource(String field, Type type) {
    if (type == Type.BYTE) {
      return new ByteFieldSource(field);
    }
    if (type == Type.SHORT) {
      return new ShortFieldSource(field);
    }
    if (type == Type.INT) {
      return new IntFieldSource(field);
    }
    if (type == Type.FLOAT) {
      return new FloatFieldSource(field);
    }
    throw new IllegalArgumentException(type+" is not a known Field Score Query Type!");
  }

}
