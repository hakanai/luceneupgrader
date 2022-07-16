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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search;

import java.util.Objects;

public final class TotalHits {

  public enum Relation {
    EQUAL_TO,
    GREATER_THAN_OR_EQUAL_TO
  }

  public final long value;

  public final Relation relation;

  public TotalHits(long value, Relation relation) {
    if (value < 0) {
      throw new IllegalArgumentException("value must be >= 0, got " + value);
    }
    this.value = value;
    this.relation = Objects.requireNonNull(relation);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TotalHits totalHits = (TotalHits) o;
    return value == totalHits.value && relation == totalHits.relation;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, relation);
  }

  @Override
  public String toString() {
    return value + (relation == Relation.EQUAL_TO ? "" : "+") + " hits";
  }

}
