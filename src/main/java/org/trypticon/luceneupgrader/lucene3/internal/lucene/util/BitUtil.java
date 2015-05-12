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

package org.trypticon.luceneupgrader.lucene3.internal.lucene.util; // from org.apache.solr.util rev 555343

/**  A variety of high efficiency bit twiddling routines.
 * @lucene.internal
 */
public final class BitUtil {

  private BitUtil() {} // no instance

  /* python code to generate ntzTable
  def ntz(val):
    if val==0: return 8
    i=0
    while (val&0x01)==0:
      i = i+1
      val >>= 1
    return i
  print ','.join([ str(ntz(i)) for i in range(256) ])
  ***/


  /** table of number of leading zeros in a byte */
  public static final byte[] nlzTable = {8,7,6,6,5,5,5,5,4,4,4,4,4,4,4,4,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};

  /** Returns the number of leading zero bits.
   */
  public static int nlz(long x) {
   int n = 0;
   // do the first step as a long
   int y = (int)(x>>>32);
   if (y==0) {n+=32; y = (int)(x); }
   if ((y & 0xFFFF0000) == 0) { n+=16; y<<=16; }
   if ((y & 0xFF000000) == 0) { n+=8; y<<=8; }
   return n + nlzTable[y >>> 24];
 /* implementation without table:
   if ((y & 0xF0000000) == 0) { n+=4; y<<=4; }
   if ((y & 0xC0000000) == 0) { n+=2; y<<=2; }
   if ((y & 0x80000000) == 0) { n+=1; y<<=1; }
   if ((y & 0x80000000) == 0) { n+=1;}
   return n;
  */
  }


}
