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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexFormatTooNewException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexFormatTooOldException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.BufferedChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.ChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;

public final class CodecUtil {
  private CodecUtil() {} // no instance

  public final static int CODEC_MAGIC = 0x3fd76c17;
  public final static int FOOTER_MAGIC = ~CODEC_MAGIC;

  public static void writeHeader(DataOutput out, String codec, int version)
    throws IOException {
    BytesRef bytes = new BytesRef(codec);
    if (bytes.length != codec.length() || bytes.length >= 128) {
      throw new IllegalArgumentException("codec must be simple ASCII, less than 128 characters in length [got " + codec + "]");
    }
    out.writeInt(CODEC_MAGIC);
    out.writeString(codec);
    out.writeInt(version);
  }

  public static int headerLength(String codec) {
    return 9+codec.length();
  }

  public static int checkHeader(DataInput in, String codec, int minVersion, int maxVersion)
    throws IOException {

    // Safety to guard against reading a bogus string:
    final int actualHeader = in.readInt();
    if (actualHeader != CODEC_MAGIC) {
      throw new CorruptIndexException("codec header mismatch: actual header=" + actualHeader + " vs expected header=" + CODEC_MAGIC + " (resource: " + in + ")");
    }
    return checkHeaderNoMagic(in, codec, minVersion, maxVersion);
  }


  public static int checkHeaderNoMagic(DataInput in, String codec, int minVersion, int maxVersion) throws IOException {
    final String actualCodec = in.readString();
    if (!actualCodec.equals(codec)) {
      throw new CorruptIndexException("codec mismatch: actual codec=" + actualCodec + " vs expected codec=" + codec + " (resource: " + in + ")");
    }

    final int actualVersion = in.readInt();
    if (actualVersion < minVersion) {
      throw new IndexFormatTooOldException(in, actualVersion, minVersion, maxVersion);
    }
    if (actualVersion > maxVersion) {
      throw new IndexFormatTooNewException(in, actualVersion, minVersion, maxVersion);
    }

    return actualVersion;
  }
  
  public static void writeFooter(IndexOutput out) throws IOException {
    out.writeInt(FOOTER_MAGIC);
    out.writeInt(0);
    out.writeLong(out.getChecksum());
  }
  
  public static int footerLength() {
    return 16;
  }
  

  public static long checkFooter(ChecksumIndexInput in) throws IOException {
    validateFooter(in);
    long actualChecksum = in.getChecksum();
    long expectedChecksum = in.readLong();
    if (expectedChecksum != actualChecksum) {
      throw new CorruptIndexException("checksum failed (hardware problem?) : expected=" + Long.toHexString(expectedChecksum) +  
                                                       " actual=" + Long.toHexString(actualChecksum) +
                                                       " (resource=" + in + ")");
    }
    if (in.getFilePointer() != in.length()) {
      throw new CorruptIndexException("did not read all bytes from file: read " + in.getFilePointer() + " vs size " + in.length() + " (resource: " + in + ")");
    }
    return actualChecksum;
  }
  

  public static long retrieveChecksum(IndexInput in) throws IOException {
    in.seek(in.length() - footerLength());
    validateFooter(in);
    return in.readLong();
  }
  
  private static void validateFooter(IndexInput in) throws IOException {
    final int magic = in.readInt();
    if (magic != FOOTER_MAGIC) {
      throw new CorruptIndexException("codec footer mismatch: actual footer=" + magic + " vs expected footer=" + FOOTER_MAGIC + " (resource: " + in + ")");
    }
    
    final int algorithmID = in.readInt();
    if (algorithmID != 0) {
      throw new CorruptIndexException("codec footer mismatch: unknown algorithmID: " + algorithmID);
    }
  }
  
  @Deprecated
  public static void checkEOF(IndexInput in) throws IOException {
    if (in.getFilePointer() != in.length()) {
      throw new CorruptIndexException("did not read all bytes from file: read " + in.getFilePointer() + " vs size " + in.length() + " (resource: " + in + ")");
    }
  }
  

  public static long checksumEntireFile(IndexInput input) throws IOException {
    IndexInput clone = input.clone();
    clone.seek(0);
    ChecksumIndexInput in = new BufferedChecksumIndexInput(clone);
    assert in.getFilePointer() == 0;
    in.seek(in.length() - footerLength());
    return checkFooter(in);
  }
}
