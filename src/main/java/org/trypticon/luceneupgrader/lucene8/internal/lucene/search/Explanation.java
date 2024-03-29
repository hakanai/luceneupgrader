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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Explanation {

  public static Explanation match(Number value, String description, Collection<Explanation> details) {
    return new Explanation(true, value, description, details);
  }

  public static Explanation match(Number value, String description, Explanation... details) {
    return new Explanation(true, value, description, Arrays.asList(details));
  }

  public static Explanation noMatch(String description, Collection<Explanation> details) {
    return new Explanation(false, 0f, description, details);
  }

  public static Explanation noMatch(String description, Explanation... details) {
    return new Explanation(false, 0f, description, Arrays.asList(details));
  }

  private final boolean match;                          // whether the document matched
  private final Number value;                            // the value of this node
  private final String description;                     // what it represents
  private final List<Explanation> details;              // sub-explanations

  private Explanation(boolean match, Number value, String description, Collection<Explanation> details) {
    this.match = match;
    this.value = Objects.requireNonNull(value);
    this.description = Objects.requireNonNull(description);
    this.details = Collections.unmodifiableList(new ArrayList<>(details));
    for (Explanation detail : details) {
      Objects.requireNonNull(detail);
    }
  }

  public boolean isMatch() {
    return match;
  }
  
  public Number getValue() { return value; }

  public String getDescription() { return description; }

  private String getSummary() {
    return getValue() + " = " + getDescription();
  }
  
  public Explanation[] getDetails() {
    return details.toArray(new Explanation[0]);
  }

  @Override
  public String toString() {
    return toString(0);
  }

  private String toString(int depth) {
    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      buffer.append("  ");
    }
    buffer.append(getSummary());
    buffer.append("\n");

    Explanation[] details = getDetails();
    for (int i = 0 ; i < details.length; i++) {
      buffer.append(details[i].toString(depth+1));
    }

    return buffer.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Explanation that = (Explanation) o;
    return match == that.match &&
        Objects.equals(value, that.value) &&
        Objects.equals(description, that.description) &&
        Objects.equals(details, that.details);
  }

  @Override
  public int hashCode() {
    return Objects.hash(match, value, description, details);
  }

}
