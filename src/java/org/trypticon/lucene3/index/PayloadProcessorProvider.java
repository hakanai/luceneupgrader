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

import org.trypticon.lucene3.store.Directory;

import java.io.IOException;

/**
 * Provides a {@code ReaderPayloadProcessor} to be used for a {@code Directory}.
 * This allows using different {@code ReaderPayloadProcessor}s for different
 * source {@code IndexReader}, for e.g. to perform different processing of payloads of
 * different directories.
 * <p>
 * <b>NOTE:</b> to avoid processing payloads of certain directories, you can
 * return <code>null</code> in {@code #getReaderProcessor}.
 * <p>
 * <b>NOTE:</b> it is possible that the same {@code ReaderPayloadProcessor} will be
 * requested for the same {@code Directory} concurrently. Therefore, to avoid
 * concurrency issues you should return different instances for different
 * threads. Usually, if your {@code ReaderPayloadProcessor} does not maintain state
 * this is not a problem. The merge code ensures that the
 * {@code ReaderPayloadProcessor} instance you return will be accessed by one
 * thread to obtain the {@code PayloadProcessor}s for different terms.
 * 
 * @lucene.experimental
 */
public abstract class PayloadProcessorProvider {

  /**
   * Returns a {@code PayloadProcessor} for a given {@code Term} which allows
   * processing the payloads of different terms differently. If you intent to
   * process all your payloads the same way, then you can ignore the given term.
   * <p>
   * <b>NOTE:</b> if you protect your {@code ReaderPayloadProcessor} from
   * concurrency issues, then you shouldn't worry about any such issues when
   * {@code PayloadProcessor}s are requested for different terms.
   */
  public static abstract class ReaderPayloadProcessor {

    /** Returns a {@code PayloadProcessor} for the given term. */
    public abstract PayloadProcessor getProcessor(Term term) throws IOException;
    
  }

  /**
   * @deprecated Use {@code ReaderPayloadProcessor} instead.
   */
  @Deprecated
  public static abstract class DirPayloadProcessor extends ReaderPayloadProcessor {}

  /**
   * Processes the given payload. One should call {@code #payloadLength()} to
   * get the length of the processed payload.
   * 
   * @lucene.experimental
   */
  public static abstract class PayloadProcessor {

    /** Returns the length of the payload that was returned by {@code #processPayload}. */
    public abstract int payloadLength() throws IOException;

    /**
     * Process the incoming payload and returns the resulting byte[]. Note that
     * a new array might be allocated if the given array is not big enough. The
     * length of the new payload data can be obtained via
     * {@code #payloadLength()}.
     */
    public abstract byte[] processPayload(byte[] payload, int start, int length) throws IOException;

  }

  /**
   * Returns a {@code ReaderPayloadProcessor} for the given {@code Directory},
   * through which {@code PayloadProcessor}s can be obtained for each
   * {@code Term}, or <code>null</code> if none should be used.
   * You should override this method, not {@code #getDirProcessor}.
   */
  public ReaderPayloadProcessor getReaderProcessor(IndexReader reader) throws IOException {
    return this.getDirProcessor(reader.directory());
  }

  /**
   * Returns a {@code DirPayloadProcessor} for the given {@code Directory},
   * through which {@code PayloadProcessor}s can be obtained for each
   * {@code Term}, or <code>null</code> if none should be used.
   * @deprecated Use {@code #getReaderProcessor} instead. You can still select by {@code Directory},
   * if you retrieve the underlying directory from {@code IndexReader#directory()}.
   */
  @Deprecated
  public DirPayloadProcessor getDirProcessor(Directory dir) throws IOException {
    throw new UnsupportedOperationException("You must either implement getReaderProcessor() or getDirProcessor().");
  }
  
}
