package org.apache.lucene.index;

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

/**
 * Holds state for inverting all occurrences of a single
 * field in the document.  This class doesn't do anything
 * itself; instead, it forwards the tokens produced by
 * analysis to its own consumer
 * (InvertedDocConsumerPerField).  It also interacts with an
 * endConsumer (InvertedDocEndConsumerPerField).
 */

final class DocInverterPerField extends DocFieldConsumerPerField {

  final private DocInverterPerThread perThread;
  final private FieldInfo fieldInfo;
  final InvertedDocConsumerPerField consumer;
  final InvertedDocEndConsumerPerField endConsumer;
  final DocumentsWriter.DocState docState;
  final FieldInvertState fieldState;

  public DocInverterPerField(DocInverterPerThread perThread, FieldInfo fieldInfo) {
    this.perThread = perThread;
    this.fieldInfo = fieldInfo;
    docState = perThread.docState;
    fieldState = perThread.fieldState;
    this.consumer = perThread.consumer.addField(this, fieldInfo);
    this.endConsumer = perThread.endConsumer.addField(this, fieldInfo);
  }

  @Override
  void abort() {
    try {
      consumer.abort();
    } finally {
      endConsumer.abort();
    }
  }

  @Override
  public void close() {
    consumer.close();
  }

}
