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

/**
 * Finite state transducers
 *
 * <p>This package implements <a href="http://en.wikipedia.org/wiki/Finite_state_transducer">Finite
 * State Transducers</a> with the following characteristics:
 *
 * <ul>
 *   <li>Fast and low memory overhead construction of the minimal FST (but inputs must be provided
 *       in sorted order)
 *   <li>Low object overhead and quick deserialization (byte[] representation)
 *   <li>Pluggable {@link org.apache.lucene.util.fst.Outputs Outputs} representation
 *   <li>{@link org.apache.lucene.util.fst.Util#shortestPaths N-shortest-paths} search by weight
 *   <li>Enumerators ({@link org.apache.lucene.util.fst.IntsRefFSTEnum IntsRef} and {@link
 *       org.apache.lucene.util.fst.BytesRefFSTEnum BytesRef}) that behave like {@link
 *       java.util.SortedMap SortedMap} iterators
 * </ul>
 *
 * <p>FST Construction example:
 *
 * <pre class="prettyprint">
 *     // Input values (keys). These must be provided to Builder in Unicode code point (UTF8 or UTF32) sorted order.
 *     // Note that sorting by Java's String.compareTo, which is UTF16 sorted order, is not correct and can lead to
 *     // exceptions while building the FST:
 *     String inputValues[] = {"cat", "dog", "dogs"};
 *     long outputValues[] = {5, 7, 12};
 *
 *     PositiveIntOutputs outputs = PositiveIntOutputs.getSingleton();
 *     FSTCompiler&lt;Long&gt; fstCompiler = new FSTCompiler&lt;Long&gt;(INPUT_TYPE.BYTE1, outputs);
 *     BytesRefBuilder scratchBytes = new BytesRefBuilder();
 *     IntsRefBuilder scratchInts = new IntsRefBuilder();
 *     for (int i = 0; i &lt; inputValues.length; i++) {
 *       scratchBytes.copyChars(inputValues[i]);
 *       fstCompiler.add(Util.toIntsRef(scratchBytes.toBytesRef(), scratchInts), outputValues[i]);
 *     }
 *     FST&lt;Long&gt; fst = FST.fromFSTReader(fstCompiler.compile(), fstCompiler.getFSTReader());
 * </pre>
 *
 * Retrieval by key:
 *
 * <pre class="prettyprint">
 *     Long value = Util.get(fst, new BytesRef("dog"));
 *     System.out.println(value); // 7
 * </pre>
 *
 * Retrieval by value:
 *
 * <pre class="prettyprint">
 *     // Only works because outputs are also in sorted order
 *     IntsRef key = Util.getByOutput(fst, 12);
 *     System.out.println(Util.toBytesRef(key, scratchBytes).utf8ToString()); // dogs
 * </pre>
 *
 * Iterate over key-value pairs in sorted order:
 *
 * <pre class="prettyprint">
 *     // Like TermsEnum, this also supports seeking (advance)
 *     BytesRefFSTEnum&lt;Long&gt; iterator = new BytesRefFSTEnum&lt;Long&gt;(fst);
 *     while (iterator.next() != null) {
 *       InputOutput&lt;Long&gt; mapEntry = iterator.current();
 *       System.out.println(mapEntry.input.utf8ToString());
 *       System.out.println(mapEntry.output);
 *     }
 * </pre>
 *
 * N-shortest paths by weight:
 *
 * <pre class="prettyprint">
 *     Comparator&lt;Long&gt; comparator = new Comparator&lt;Long&gt;() {
 *       public int compare(Long left, Long right) {
 *         return left.compareTo(right);
 *       }
 *     };
 *     Arc&lt;Long&gt; firstArc = fst.getFirstArc(new Arc&lt;Long&gt;());
 *     Util.TopResults&lt;Long&gt; paths = Util.shortestPaths(fst, firstArc, fst.outputs.getNoOutput(), comparator, 3, true);
 *     System.out.println(Util.toBytesRef(paths.topN.get(0).input, scratchBytes).utf8ToString()); // cat
 *     System.out.println(paths.topN.get(0).output); // 5
 *     System.out.println(Util.toBytesRef(paths.topN.get(1).input, scratchBytes).utf8ToString()); // dog
 *     System.out.println(paths.topN.get(1).output); // 7
 * </pre>
 */
package org.trypticon.luceneupgrader.lucene9.internal.lucene.util.fst;
