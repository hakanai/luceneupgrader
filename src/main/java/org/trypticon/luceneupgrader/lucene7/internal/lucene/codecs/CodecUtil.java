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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexFormatTooNewException;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexFormatTooOldException;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.BufferedChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.ChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.StringHelper;


public final class CodecUtil {
  private CodecUtil() {} // no instance

  public final static int CODEC_MAGIC = 0x3fd76c17;
  public final static int FOOTER_MAGIC = ~CODEC_MAGIC;

  public static void writeHeader(DataOutput out, String codec, int version) throws IOException {
    BytesRef bytes = new BytesRef(codec);
    if (bytes.length != codec.length() || bytes.length >= 128) {
      throw new IllegalArgumentException("codec must be simple ASCII, less than 128 characters in length [got " + codec + "]");
    }
    out.writeInt(CODEC_MAGIC);
    out.writeString(codec);
    out.writeInt(version);
  }
  
  public static void writeIndexHeader(DataOutput out, String codec, int version, byte[] id, String suffix) throws IOException {
    if (id.length != StringHelper.ID_LENGTH) {
      throw new IllegalArgumentException("Invalid id: " + StringHelper.idToString(id));
    }
    writeHeader(out, codec, version);
    out.writeBytes(id, 0, id.length);
    BytesRef suffixBytes = new BytesRef(suffix);
    if (suffixBytes.length != suffix.length() || suffixBytes.length >= 256) {
      throw new IllegalArgumentException("suffix must be simple ASCII, less than 256 characters in length [got " + suffix + "]");
    }
    out.writeByte((byte) suffixBytes.length);
    out.writeBytes(suffixBytes.bytes, suffixBytes.offset, suffixBytes.length);
  }

  public static int headerLength(String codec) {
    return 9+codec.length();
  }
  
  public static int indexHeaderLength(String codec, String suffix) {
    return headerLength(codec) + StringHelper.ID_LENGTH + 1 + suffix.length();
  }

  public static int checkHeader(DataInput in, String codec, int minVersion, int maxVersion) throws IOException {
    // Safety to guard against reading a bogus string:
    final int actualHeader = in.readInt();
    if (actualHeader != CODEC_MAGIC) {
      throw new CorruptIndexException("codec header mismatch: actual header=" + actualHeader + " vs expected header=" + CODEC_MAGIC, in);
    }
    return checkHeaderNoMagic(in, codec, minVersion, maxVersion);
  }

  public static int checkHeaderNoMagic(DataInput in, String codec, int minVersion, int maxVersion) throws IOException {
    final String actualCodec = in.readString();
    if (!actualCodec.equals(codec)) {
      throw new CorruptIndexException("codec mismatch: actual codec=" + actualCodec + " vs expected codec=" + codec, in);
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
  
  public static int checkIndexHeader(DataInput in, String codec, int minVersion, int maxVersion, byte[] expectedID, String expectedSuffix) throws IOException {
    int version = checkHeader(in, codec, minVersion, maxVersion);
    checkIndexHeaderID(in, expectedID);
    checkIndexHeaderSuffix(in, expectedSuffix);
    return version;
  }

  public static void verifyAndCopyIndexHeader(IndexInput in, DataOutput out, byte[] expectedID) throws IOException {
    // make sure it's large enough to have a header and footer
    if (in.length() < footerLength() + headerLength("")) {
      throw new CorruptIndexException("compound sub-files must have a valid codec header and footer: file is too small (" + in.length() + " bytes)", in);
    }

    int actualHeader = in.readInt();
    if (actualHeader != CODEC_MAGIC) {
      throw new CorruptIndexException("compound sub-files must have a valid codec header and footer: codec header mismatch: actual header=" + actualHeader + " vs expected header=" + CodecUtil.CODEC_MAGIC, in);
    }

    // we can't verify these, so we pass-through:
    String codec = in.readString();
    int version = in.readInt();

    // verify id:
    checkIndexHeaderID(in, expectedID);

    // we can't verify extension either, so we pass-through:
    int suffixLength = in.readByte() & 0xFF;
    byte[] suffixBytes = new byte[suffixLength];
    in.readBytes(suffixBytes, 0, suffixLength);

    // now write the header we just verified
    out.writeInt(CodecUtil.CODEC_MAGIC);
    out.writeString(codec);
    out.writeInt(version);
    out.writeBytes(expectedID, 0, expectedID.length);
    out.writeByte((byte) suffixLength);
    out.writeBytes(suffixBytes, 0, suffixLength);
  }


  public static byte[] readIndexHeader(IndexInput in) throws IOException {
    in.seek(0);
    final int actualHeader = in.readInt();
    if (actualHeader != CODEC_MAGIC) {
      throw new CorruptIndexException("codec header mismatch: actual header=" + actualHeader + " vs expected header=" + CODEC_MAGIC, in);
    }
    String codec = in.readString();
    in.readInt();
    in.seek(in.getFilePointer() + StringHelper.ID_LENGTH);
    int suffixLength = in.readByte() & 0xFF;
    byte[] bytes = new byte[headerLength(codec) + StringHelper.ID_LENGTH + 1 + suffixLength];
    in.seek(0);
    in.readBytes(bytes, 0, bytes.length);
    return bytes;
  }

  public static byte[] readFooter(IndexInput in) throws IOException {
    if (in.length() < footerLength()) {
      throw new CorruptIndexException("misplaced codec footer (file truncated?): length=" + in.length() + " but footerLength==" + footerLength(), in);
    }
    in.seek(in.length() - footerLength());
    validateFooter(in);
    in.seek(in.length() - footerLength());
    byte[] bytes = new byte[footerLength()];
    in.readBytes(bytes, 0, bytes.length);
    return bytes;
  }
  
  public static byte[] checkIndexHeaderID(DataInput in, byte[] expectedID) throws IOException {
    byte id[] = new byte[StringHelper.ID_LENGTH];
    in.readBytes(id, 0, id.length);
    if (!Arrays.equals(id, expectedID)) {
      throw new CorruptIndexException("file mismatch, expected id=" + StringHelper.idToString(expectedID) 
                                                         + ", got=" + StringHelper.idToString(id), in);
    }
    return id;
  }
  
  public static String checkIndexHeaderSuffix(DataInput in, String expectedSuffix) throws IOException {
    int suffixLength = in.readByte() & 0xFF;
    byte suffixBytes[] = new byte[suffixLength];
    in.readBytes(suffixBytes, 0, suffixBytes.length);
    String suffix = new String(suffixBytes, 0, suffixBytes.length, StandardCharsets.UTF_8);
    if (!suffix.equals(expectedSuffix)) {
      throw new CorruptIndexException("file mismatch, expected suffix=" + expectedSuffix
                                                             + ", got=" + suffix, in);
    }
    return suffix;
  }
  
  public static void writeFooter(IndexOutput out) throws IOException {
    out.writeInt(FOOTER_MAGIC);
    out.writeInt(0);
    writeCRC(out);
  }
  
  public static int footerLength() {
    return 16;
  }
  
  public static long checkFooter(ChecksumIndexInput in) throws IOException {
    validateFooter(in);
    long actualChecksum = in.getChecksum();
    long expectedChecksum = readCRC(in);
    if (expectedChecksum != actualChecksum) {
      throw new CorruptIndexException("checksum failed (hardware problem?) : expected=" + Long.toHexString(expectedChecksum) +  
                                                       " actual=" + Long.toHexString(actualChecksum), in);
    }
    return actualChecksum;
  }
  
  public static void checkFooter(ChecksumIndexInput in, Throwable priorException) throws IOException {
    if (priorException == null) {
      checkFooter(in);
    } else {
      try {
        long remaining = in.length() - in.getFilePointer();
        if (remaining < footerLength()) {
          // corruption caused us to read into the checksum footer already: we can't proceed
          priorException.addSuppressed(new CorruptIndexException("checksum status indeterminate: remaining=" + remaining +
                                                                 ", please run checkindex for more details", in));
        } else {
          // otherwise, skip any unread bytes.
          in.skipBytes(remaining - footerLength());
          
          // now check the footer
          try {
            long checksum = checkFooter(in);
            priorException.addSuppressed(new CorruptIndexException("checksum passed (" + Long.toHexString(checksum) + 
                                                                   "). possibly transient resource issue, or a Lucene or JVM bug", in));
          } catch (CorruptIndexException t) {
            priorException.addSuppressed(t);
          }
        }
      } catch (Throwable t) {
        // catch-all for things that shouldn't go wrong (e.g. OOM during readInt) but could...
        priorException.addSuppressed(new CorruptIndexException("checksum status indeterminate: unexpected exception", in, t));
      }
      throw IOUtils.rethrowAlways(priorException);
    }
  }
  
  public static long retrieveChecksum(IndexInput in) throws IOException {
    if (in.length() < footerLength()) {
      throw new CorruptIndexException("misplaced codec footer (file truncated?): length=" + in.length() + " but footerLength==" + footerLength(), in);
    }
    in.seek(in.length() - footerLength());
    validateFooter(in);
    return readCRC(in);
  }
  
  private static void validateFooter(IndexInput in) throws IOException {
    long remaining = in.length() - in.getFilePointer();
    long expected = footerLength();
    if (remaining < expected) {
      throw new CorruptIndexException("misplaced codec footer (file truncated?): remaining=" + remaining + ", expected=" + expected + ", fp=" + in.getFilePointer(), in);
    } else if (remaining > expected) {
      throw new CorruptIndexException("misplaced codec footer (file extended?): remaining=" + remaining + ", expected=" + expected + ", fp=" + in.getFilePointer(), in);
    }
    
    final int magic = in.readInt();
    if (magic != FOOTER_MAGIC) {
      throw new CorruptIndexException("codec footer mismatch (file truncated?): actual footer=" + magic + " vs expected footer=" + FOOTER_MAGIC, in);
    }
    
    final int algorithmID = in.readInt();
    if (algorithmID != 0) {
      throw new CorruptIndexException("codec footer mismatch: unknown algorithmID: " + algorithmID, in);
    }
  }
  
  public static long checksumEntireFile(IndexInput input) throws IOException {
    IndexInput clone = input.clone();
    clone.seek(0);
    ChecksumIndexInput in = new BufferedChecksumIndexInput(clone);
    assert in.getFilePointer() == 0;
    if (in.length() < footerLength()) {
      throw new CorruptIndexException("misplaced codec footer (file truncated?): length=" + in.length() + " but footerLength==" + footerLength(), input);
    }
    in.seek(in.length() - footerLength());
    return checkFooter(in);
  }
  
  static long readCRC(IndexInput input) throws IOException {
    long value = input.readLong();
    if ((value & 0xFFFFFFFF00000000L) != 0) {
      throw new CorruptIndexException("Illegal CRC-32 checksum: " + value, input);
    }
    return value;
  }
  
  static void writeCRC(IndexOutput output) throws IOException {
    long value = output.getChecksum();
    if ((value & 0xFFFFFFFF00000000L) != 0) {
      throw new IllegalStateException("Illegal CRC-32 checksum: " + value + " (resource=" + output + ")");
    }
    output.writeLong(value);
  }
}
