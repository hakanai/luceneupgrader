package org.apache.lucene.document;

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

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Simple utility class providing static methods to
 *  compress and decompress binary data for stored fields.
 *  This class uses java.util.zip.Deflater and Inflater
 *  classes to compress and decompress.
 */

public class CompressionTools {

  // Export only static methods
  private CompressionTools() {}

  /** Decompress the byte array previously returned by
   *  compress */
  public static byte[] decompress(byte[] value) throws DataFormatException {
    // Create an expandable byte array to hold the decompressed data
    ByteArrayOutputStream bos = new ByteArrayOutputStream(value.length);

    Inflater decompressor = new Inflater();

    try {
      decompressor.setInput(value);

      // Decompress the data
      final byte[] buf = new byte[1024];
      while (!decompressor.finished()) {
        int count = decompressor.inflate(buf);
        bos.write(buf, 0, count);
      }
    } finally {  
      decompressor.end();
    }
    
    return bos.toByteArray();
  }

}
