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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.util;



import java.text.ParseException;
import java.util.Locale;

public final class Version {

  @Deprecated
  public static final Version LUCENE_4_0_0_ALPHA = new Version(4, 0, 0, 0);

  @Deprecated
  public static final Version LUCENE_4_0_0_BETA = new Version(4, 0, 0, 1);

  @Deprecated
  public static final Version LUCENE_4_0_0 = new Version(4, 0, 0, 2);
  
  @Deprecated
  public static final Version LUCENE_4_1_0 = new Version(4, 1, 0);

  @Deprecated
  public static final Version LUCENE_4_2_0 = new Version(4, 2, 0);

  @Deprecated
  public static final Version LUCENE_4_2_1 = new Version(4, 2, 1);

  @Deprecated
  public static final Version LUCENE_4_3_0 = new Version(4, 3, 0);

  @Deprecated
  public static final Version LUCENE_4_3_1 = new Version(4, 3, 1);

  @Deprecated
  public static final Version LUCENE_4_4_0 = new Version(4, 4, 0);

  @Deprecated
  public static final Version LUCENE_4_5_0 = new Version(4, 5, 0);

  @Deprecated
  public static final Version LUCENE_4_5_1 = new Version(4, 5, 1);

  @Deprecated
  public static final Version LUCENE_4_6_0 = new Version(4, 6, 0);

  @Deprecated
  public static final Version LUCENE_4_6_1 = new Version(4, 6, 1);
  
  @Deprecated
  public static final Version LUCENE_4_7_0 = new Version(4, 7, 0);

  @Deprecated
  public static final Version LUCENE_4_7_1 = new Version(4, 7, 1);

  @Deprecated
  public static final Version LUCENE_4_7_2 = new Version(4, 7, 2);
  
  @Deprecated
  public static final Version LUCENE_4_8_0 = new Version(4, 8, 0);

  @Deprecated
  public static final Version LUCENE_4_8_1 = new Version(4, 8, 1);

  @Deprecated
  public static final Version LUCENE_4_9_0 = new Version(4, 9, 0);
  
  @Deprecated
  public static final Version LUCENE_4_9_1 = new Version(4, 9, 1);
  
  @Deprecated
  public static final Version LUCENE_4_10_0 = new Version(4, 10, 0);

  @Deprecated
  public static final Version LUCENE_4_10_1 = new Version(4, 10, 1);

  @Deprecated
  public static final Version LUCENE_4_10_2 = new Version(4, 10, 2);

  @Deprecated
  public static final Version LUCENE_4_10_3 = new Version(4, 10, 3);

  @Deprecated
  public static final Version LUCENE_4_10_4 = new Version(4, 10, 4);


  @Deprecated
  public static final Version LUCENE_5_0_0 = new Version(5, 0, 0);

  @Deprecated
  public static final Version LUCENE_5_1_0 = new Version(5, 1, 0);

  @Deprecated
  public static final Version LUCENE_5_2_0 = new Version(5, 2, 0);

  @Deprecated
  public static final Version LUCENE_5_2_1 = new Version(5, 2, 1);

  @Deprecated
  public static final Version LUCENE_5_3_0 = new Version(5, 3, 0);

  @Deprecated
  public static final Version LUCENE_5_3_1 = new Version(5, 3, 1);

  @Deprecated
  public static final Version LUCENE_5_3_2 = new Version(5, 3, 2);

  @Deprecated
  public static final Version LUCENE_5_4_0 = new Version(5, 4, 0);

  @Deprecated
  public static final Version LUCENE_5_4_1 = new Version(5, 4, 1);

  @Deprecated
  public static final Version LUCENE_5_5_0 = new Version(5, 5, 0);

  @Deprecated
  public static final Version LUCENE_5_5_1 = new Version(5, 5, 1);

  @Deprecated
  public static final Version LUCENE_5_5_2 = new Version(5, 5, 2);

  public static final Version LUCENE_5_5_3 = new Version(5, 5, 3);

  // To add a new version:
  //  * Only add above this comment
  //  * If the new version is the newest, change LATEST below and deprecate the previous LATEST

  public static final Version LATEST = LUCENE_5_5_3;

  @Deprecated
  public static final Version LUCENE_CURRENT = LATEST;

  @Deprecated
  public static final Version LUCENE_4_0 = LUCENE_4_0_0_ALPHA;

  @Deprecated
  public static final Version LUCENE_4_1 = LUCENE_4_1_0;

  @Deprecated
  public static final Version LUCENE_4_2 = LUCENE_4_2_0;

  @Deprecated
  public static final Version LUCENE_4_3 = LUCENE_4_3_0;

  @Deprecated
  public static final Version LUCENE_4_4 = LUCENE_4_4_0;

  @Deprecated
  public static final Version LUCENE_4_5 = LUCENE_4_5_0;

  @Deprecated
  public static final Version LUCENE_4_6 = LUCENE_4_6_0;

  @Deprecated
  public static final Version LUCENE_4_7 = LUCENE_4_7_0;

  @Deprecated
  public static final Version LUCENE_4_8 = LUCENE_4_8_0;

  @Deprecated
  public static final Version LUCENE_4_9 = LUCENE_4_9_0;


  public static Version parse(String version) throws ParseException {

    StrictStringTokenizer tokens = new StrictStringTokenizer(version, '.');
    if (tokens.hasMoreTokens() == false) {
      throw new ParseException("Version is not in form major.minor.bugfix(.prerelease) (got: " + version + ")", 0);
    }

    int major;
    String token = tokens.nextToken();
    try {
      major = Integer.parseInt(token);
    } catch (NumberFormatException nfe) {
      ParseException p = new ParseException("Failed to parse major version from \"" + token + "\" (got: " + version + ")", 0);
      p.initCause(nfe);
      throw p;
    }

    if (tokens.hasMoreTokens() == false) {
      throw new ParseException("Version is not in form major.minor.bugfix(.prerelease) (got: " + version + ")", 0);
    }

    int minor;
    token = tokens.nextToken();
    try {
      minor = Integer.parseInt(token);
    } catch (NumberFormatException nfe) {
      ParseException p = new ParseException("Failed to parse minor version from \"" + token + "\" (got: " + version + ")", 0);
      p.initCause(nfe);
      throw p;
    }

    int bugfix = 0;
    int prerelease = 0;
    if (tokens.hasMoreTokens()) {

      token = tokens.nextToken();
      try {
        bugfix = Integer.parseInt(token);
      } catch (NumberFormatException nfe) {
        ParseException p = new ParseException("Failed to parse bugfix version from \"" + token + "\" (got: " + version + ")", 0);
        p.initCause(nfe);
        throw p;
      }

      if (tokens.hasMoreTokens()) {
        token = tokens.nextToken();
        try {
          prerelease = Integer.parseInt(token);
        } catch (NumberFormatException nfe) {
          ParseException p = new ParseException("Failed to parse prerelease version from \"" + token + "\" (got: " + version + ")", 0);
          p.initCause(nfe);
          throw p;
        }
        if (prerelease == 0) {
          throw new ParseException("Invalid value " + prerelease + " for prerelease; should be 1 or 2 (got: " + version + ")", 0);
        }

        if (tokens.hasMoreTokens()) {
          // Too many tokens!
          throw new ParseException("Version is not in form major.minor.bugfix(.prerelease) (got: " + version + ")", 0);
        }
      }
    }

    try {
      return new Version(major, minor, bugfix, prerelease);
    } catch (IllegalArgumentException iae) {
      ParseException pe = new ParseException("failed to parse version string \"" + version + "\": " + iae.getMessage(), 0);
      pe.initCause(iae);
      throw pe;
    }
  }

  public static Version parseLeniently(String version) throws ParseException {
    String versionOrig = version;
    version = version.toUpperCase(Locale.ROOT);
    switch (version) {
      case "LATEST":
      case "LUCENE_CURRENT":
        return LATEST;
      case "LUCENE_4_0_0":
        return LUCENE_4_0_0;
      case "LUCENE_4_0_0_ALPHA":
        return LUCENE_4_0_0_ALPHA;
      case "LUCENE_4_0_0_BETA":
        return LUCENE_4_0_0_BETA;
      default:
        version = version
          .replaceFirst("^LUCENE_(\\d+)_(\\d+)_(\\d+)$", "$1.$2.$3")
          .replaceFirst("^LUCENE_(\\d+)_(\\d+)$", "$1.$2.0")
          .replaceFirst("^LUCENE_(\\d)(\\d)$", "$1.$2.0");
        try {
          return parse(version);
        } catch (ParseException pe) {
          ParseException pe2 = new ParseException("failed to parse lenient version string \"" + versionOrig + "\": " + pe.getMessage(), 0);
          pe2.initCause(pe);
          throw pe2;
        }
    }
  }
  

  public static Version fromBits(int major, int minor, int bugfix) {
    return new Version(major, minor, bugfix);
  }

  public final int major;
  public final int minor;
  public final int bugfix;
  public final int prerelease;

  // stores the version pieces, with most significant pieces in high bits
  // ie:  | 1 byte | 1 byte | 1 byte |   2 bits   |
  //         major   minor    bugfix   prerelease
  private final int encodedValue;

  private Version(int major, int minor, int bugfix) {
    this(major, minor, bugfix, 0);
  }

  private Version(int major, int minor, int bugfix, int prerelease) {
    this.major = major;
    this.minor = minor;
    this.bugfix = bugfix;
    this.prerelease = prerelease;

    // NOTE: do not enforce major version so we remain future proof, except to
    // make sure it fits in the 8 bits we encode it into:
    if (major > 255 || major < 0) {
      throw new IllegalArgumentException("Illegal major version: " + major);
    }
    if (minor > 255 || minor < 0) {
      throw new IllegalArgumentException("Illegal minor version: " + minor);
    }
    if (bugfix > 255 || bugfix < 0) {
      throw new IllegalArgumentException("Illegal bugfix version: " + bugfix);
    }
    if (prerelease > 2 || prerelease < 0) {
      throw new IllegalArgumentException("Illegal prerelease version: " + prerelease);
    }
    if (prerelease != 0 && (minor != 0 || bugfix != 0)) {
      throw new IllegalArgumentException("Prerelease version only supported with major release (got prerelease: " + prerelease + ", minor: " + minor + ", bugfix: " + bugfix + ")");
    }

    encodedValue = major << 18 | minor << 10 | bugfix << 2 | prerelease;

    assert encodedIsValid();
  }

  public boolean onOrAfter(Version other) {
    return encodedValue >= other.encodedValue;
  }

  @Override
  public String toString() {
    if (prerelease == 0) {
      return "" + major + "." + minor + "." + bugfix;
    }
    return "" + major + "." + minor + "." + bugfix + "." + prerelease;
  }

  @Override
  public boolean equals(Object o) {
    return o != null && o instanceof Version && ((Version)o).encodedValue == encodedValue;
  }

  // Used only by assert:
  private boolean encodedIsValid() {
    assert major == ((encodedValue >>> 18) & 0xFF);
    assert minor == ((encodedValue >>> 10) & 0xFF);
    assert bugfix == ((encodedValue >>> 2) & 0xFF);
    assert prerelease == (encodedValue & 0x03);
    return true;
  }

  @Override
  public int hashCode() {
    return encodedValue;
  }
}
