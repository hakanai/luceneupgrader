package org.trypticon.luceneupgrader.lucene4.internal.lucene.util;
public class SmallFloat {
  
  private SmallFloat() {}


  public static byte floatToByte(float f, int numMantissaBits, int zeroExp) {
    // Adjustment from a float zero exponent to our zero exponent,
    // shifted over to our exponent position.
    int fzero = (63-zeroExp)<<numMantissaBits;
    int bits = Float.floatToRawIntBits(f);
    int smallfloat = bits >> (24-numMantissaBits);
    if (smallfloat <= fzero) {
      return (bits<=0) ?
        (byte)0   // negative numbers and zero both map to 0 byte
       :(byte)1;  // underflow is mapped to smallest non-zero number.
    } else if (smallfloat >= fzero + 0x100) {
      return -1;  // overflow maps to largest number
    } else {
      return (byte)(smallfloat - fzero);
    }
  }

  public static float byteToFloat(byte b, int numMantissaBits, int zeroExp) {
    // on Java1.5 & 1.6 JVMs, prebuilding a decoding array and doing a lookup
    // is only a little bit faster (anywhere from 0% to 7%)
    if (b == 0) return 0.0f;
    int bits = (b&0xff) << (24-numMantissaBits);
    bits += (63-zeroExp) << 24;
    return Float.intBitsToFloat(bits);
  }


  //
  // Some specializations of the generic functions follow.
  // The generic functions are just as fast with current (1.5)
  // -server JVMs, but still slower with client JVMs.
  //


  public static byte floatToByte315(float f) {
    int bits = Float.floatToRawIntBits(f);
    int smallfloat = bits >> (24-3);
    if (smallfloat <= ((63-15)<<3)) {
      return (bits<=0) ? (byte)0 : (byte)1;
    }
    if (smallfloat >= ((63-15)<<3) + 0x100) {
      return -1;
    }
    return (byte)(smallfloat - ((63-15)<<3));
 }

  public static float byte315ToFloat(byte b) {
    // on Java1.5 & 1.6 JVMs, prebuilding a decoding array and doing a lookup
    // is only a little bit faster (anywhere from 0% to 7%)
    if (b == 0) return 0.0f;
    int bits = (b&0xff) << (24-3);
    bits += (63-15) << 24;
    return Float.intBitsToFloat(bits);
  }



  public static byte floatToByte52(float f) {
    int bits = Float.floatToRawIntBits(f);
    int smallfloat = bits >> (24-5);
    if (smallfloat <= (63-2)<<5) {
      return (bits<=0) ? (byte)0 : (byte)1;
    }
    if (smallfloat >= ((63-2)<<5) + 0x100) {
      return -1;
    }
    return (byte)(smallfloat - ((63-2)<<5));
  }

  public static float byte52ToFloat(byte b) {
    // on Java1.5 & 1.6 JVMs, prebuilding a decoding array and doing a lookup
    // is only a little bit faster (anywhere from 0% to 7%)
    if (b == 0) return 0.0f;
    int bits = (b&0xff) << (24-5);
    bits += (63-2) << 24;
    return Float.intBitsToFloat(bits);
  }
}
