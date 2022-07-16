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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search.similarities;


public class DistributionSPL extends Distribution {
  
  public DistributionSPL() {}

  @Override
  public final double score(BasicStats stats, double tfn, double lambda) {
    assert lambda != 1;

    // tfn/(tfn+1) -> 1 - 1/(tfn+1), guaranteed to be non decreasing when tfn increases
    double q = 1 - 1 / (tfn + 1);
    if (q == 1) {
      q = Math.nextDown(1.0);
    }

    double pow = Math.pow(lambda, q);
    if (pow == lambda) {
      // this can happen because of floating-point rounding
      // but then we return infinity when taking the log, so we enforce
      // that pow is different from lambda
      if (lambda < 1) {
        // x^y > x when x < 1 and y < 1
        pow = Math.nextUp(lambda);
      } else {
        // x^y < x when x > 1 and y < 1
        pow = Math.nextDown(lambda);
      }
    }

    return -Math.log((pow - lambda) / (1 - lambda));
  }
  
  @Override
  public String toString() {
    return "SPL";
  }
}
