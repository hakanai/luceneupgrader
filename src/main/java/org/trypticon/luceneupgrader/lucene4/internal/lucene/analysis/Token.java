package org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis;

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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.FlagsAttribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.PackedTokenAttributeImpl;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.PayloadAttribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsAndPositionsEnum; // for javadoc
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Attribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.AttributeFactory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.AttributeImpl;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.AttributeReflector;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;

/** 
  A Token is an occurrence of a term from the text of a field.  It consists of
  a term's text, the start and end offset of the term in the text of the field,
  and a type string.
  <p>
  The start and end offsets permit applications to re-associate a token with
  its source text, e.g., to display highlighted query terms in a document
  browser, or to show matching text fragments in a <abbr title="KeyWord In Context">KWIC</abbr>
  display, etc.
  <p>
  The type is a string, assigned by a lexical analyzer
  (a.k.a. tokenizer), naming the lexical or syntactic class that the token
  belongs to.  For example an end of sentence marker token might be implemented
  with type "eos".  The default token type is "word".  
  <p>
  A Token can optionally have metadata (a.k.a. payload) in the form of a variable
  length byte array. Use {@link DocsAndPositionsEnum#getPayload()} to retrieve the 
  payloads from the index.
  
  <br><br>
  
  <p><b>NOTE:</b> As of 2.9, Token implements all {@link Attribute} interfaces
  that are part of core Lucene and can be found in the {@code tokenattributes} subpackage.
  Even though it is not necessary to use Token anymore, with the new TokenStream API it can
  be used as convenience class that implements all {@link Attribute}s, which is especially useful
  to easily switch from the old to the new TokenStream API.
 
  A few things to note:
  <ul>
  <li>clear() initializes all of the fields to default values. This was changed in contrast to Lucene 2.4, but should affect no one.</li>
  <li>Because <code>TokenStreams</code> can be chained, one cannot assume that the <code>Token's</code> current type is correct.</li>
  <li>The startOffset and endOffset represent the start and offset in the source text, so be careful in adjusting them.</li>
  <li>When caching a reusable token, clone it. When injecting a cached token into a stream that can be reset, clone it again.</li>
  </ul>
  </p>
  <p>
  <b>Please note:</b> With Lucene 3.1, the <code>{@linkplain #toString toString()}</code> method had to be changed to match the
  {@link CharSequence} interface introduced by the interface {@link org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.CharTermAttribute}.
  This method now only prints the term text, no additional information anymore.
  </p>
  @deprecated This class is outdated and no longer used since Lucene 2.9. Nuke it finally!
*/
@Deprecated
public class Token extends PackedTokenAttributeImpl implements FlagsAttribute, PayloadAttribute {

  private int flags;
  private BytesRef payload;

  /** Constructs a Token will null text. */
  public Token() {
  }

  /** Constructs a Token with null text and start & end
   *  offsets.
   *  @param start start offset in the source text
   *  @param end end offset in the source text */
  public Token(int start, int end) {
    setOffset(start, end);
  }

  /** Constructs a Token with null text and start & end
   *  offsets plus the Token type.
   *  @param start start offset in the source text
   *  @param end end offset in the source text
   *  @param typ the lexical type of this Token */
  public Token(int start, int end, String typ) {
    setOffset(start, end);
    setType(typ);
  }

  /**
   * Constructs a Token with null text and start & end
   *  offsets plus flags. NOTE: flags is EXPERIMENTAL.
   *  @param start start offset in the source text
   *  @param end end offset in the source text
   *  @param flags The bits to set for this token
   */
  public Token(int start, int end, int flags) {
    setOffset(start, end);
    setFlags(flags);
  }

  /** Constructs a Token with the given term text, and start
   *  & end offsets.  The type defaults to "word."
   *  <b>NOTE:</b> for better indexing speed you should
   *  instead use the char[] termBuffer methods to set the
   *  term text.
   *  @param text term text
   *  @param start start offset in the source text
   *  @param end end offset in the source text
   */
  public Token(CharSequence text, int start, int end) {
    append(text);
    setOffset(start, end);
  }

  /** Constructs a Token with the given text, start and end
   *  offsets, & type.  <b>NOTE:</b> for better indexing
   *  speed you should instead use the char[] termBuffer
   *  methods to set the term text.
   *  @param text term text
   *  @param start start offset in the source text
   *  @param end end offset in the source text
   *  @param typ token type
   */
  public Token(String text, int start, int end, String typ) {
    append(text);
    setOffset(start, end);
    setType(typ);
  }

  /**
   *  Constructs a Token with the given text, start and end
   *  offsets, & type.  <b>NOTE:</b> for better indexing
   *  speed you should instead use the char[] termBuffer
   *  methods to set the term text.
   * @param text term text
   * @param start start offset in the source text
   * @param end end offset in the source text
   * @param flags token type bits
   */
  public Token(String text, int start, int end, int flags) {
    append(text);
    setOffset(start, end);
    setFlags(flags);
  }

  /**
   *  Constructs a Token with the given term buffer (offset
   *  & length), start and end
   *  offsets
   * @param startTermBuffer buffer containing term text
   * @param termBufferOffset the index in the buffer of the first character
   * @param termBufferLength number of valid characters in the buffer
   * @param start start offset in the source text
   * @param end end offset in the source text
   */
  public Token(char[] startTermBuffer, int termBufferOffset, int termBufferLength, int start, int end) {
    copyBuffer(startTermBuffer, termBufferOffset, termBufferLength);
    setOffset(start, end);
  }

  /**
   * {@inheritDoc}
   * @see FlagsAttribute
   */
  @Override
  public int getFlags() {
    return flags;
  }

  /**
   * {@inheritDoc}
   * @see FlagsAttribute
   */
  @Override
  public void setFlags(int flags) {
    this.flags = flags;
  }

  /**
   * {@inheritDoc}
   * @see PayloadAttribute
   */
  @Override
  public BytesRef getPayload() {
    return this.payload;
  }

  /**
   * {@inheritDoc}
   * @see PayloadAttribute
   */
  @Override
  public void setPayload(BytesRef payload) {
    this.payload = payload;
  }
  
  /** Resets the term text, payload, flags, positionIncrement, positionLength,
   * startOffset, endOffset and token type to default.
   */
  @Override
  public void clear() {
    super.clear();
    flags = 0;
    payload = null;
  }

  @Override
  public Token clone() {
    Token t = (Token)super.clone();
    // Do a deep clone
    if (payload != null) {
      t.payload = payload.clone();
    }
    return t;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this)
      return true;

    if (obj instanceof Token) {
      final Token other = (Token) obj;
      return (
        flags == other.flags &&
        (payload == null ? other.payload == null : payload.equals(other.payload)) &&
        super.equals(obj)
      );
    } else
      return false;
  }

  @Override
  public int hashCode() {
    int code = super.hashCode();
    code = code * 31 + flags;
    if (payload != null) {
      code = code * 31 + payload.hashCode();
    }
    return code;
  }

  /** Shorthand for calling {@link #clear},
   *  {@link #copyBuffer(char[], int, int)},
   *  {@link #setOffset},
   *  {@link #setType}
   *  @return this Token instance */
  public Token reinit(char[] newTermBuffer, int newTermOffset, int newTermLength, int newStartOffset, int newEndOffset, String newType) {
    clear();
    copyBuffer(newTermBuffer, newTermOffset, newTermLength);
    setOffset(newStartOffset, newEndOffset);
    setType(newType);
    return this;
  }

  /** Shorthand for calling {@link #clear},
   *  {@link #copyBuffer(char[], int, int)},
   *  {@link #setOffset},
   *  {@link #setType} on Token.DEFAULT_TYPE
   *  @return this Token instance */
  public Token reinit(char[] newTermBuffer, int newTermOffset, int newTermLength, int newStartOffset, int newEndOffset) {
    clear();
    copyBuffer(newTermBuffer, newTermOffset, newTermLength);
    setOffset(newStartOffset, newEndOffset);
    return this;
  }

  /** Shorthand for calling {@link #clear},
   *  {@link #append(CharSequence)},
   *  {@link #setOffset},
   *  {@link #setType}
   *  @return this Token instance */
  public Token reinit(String newTerm, int newStartOffset, int newEndOffset, String newType) {
    clear();
    append(newTerm);
    setOffset(newStartOffset, newEndOffset);
    setType(newType);
    return this;
  }

  /** Shorthand for calling {@link #clear},
   *  {@link #append(CharSequence, int, int)},
   *  {@link #setOffset},
   *  {@link #setType}
   *  @return this Token instance */
  public Token reinit(String newTerm, int newTermOffset, int newTermLength, int newStartOffset, int newEndOffset, String newType) {
    clear();
    append(newTerm, newTermOffset, newTermOffset + newTermLength);
    setOffset(newStartOffset, newEndOffset);
    setType(newType);
    return this;
  }

  /** Shorthand for calling {@link #clear},
   *  {@link #append(CharSequence)},
   *  {@link #setOffset},
   *  {@link #setType} on Token.DEFAULT_TYPE
   *  @return this Token instance */
  public Token reinit(String newTerm, int newStartOffset, int newEndOffset) {
    clear();
    append(newTerm);
    setOffset(newStartOffset, newEndOffset);
    return this;
  }

  /** Shorthand for calling {@link #clear},
   *  {@link #append(CharSequence, int, int)},
   *  {@link #setOffset},
   *  {@link #setType} on Token.DEFAULT_TYPE
   *  @return this Token instance */
  public Token reinit(String newTerm, int newTermOffset, int newTermLength, int newStartOffset, int newEndOffset) {
    clear();
    append(newTerm, newTermOffset, newTermOffset + newTermLength);
    setOffset(newStartOffset, newEndOffset);
    return this;
  }

  /**
   * Copy the prototype token's fields into this one. Note: Payloads are shared.
   * @param prototype source Token to copy fields from
   */
  public void reinit(Token prototype) {
    // this is a bad hack to emulate no cloning of payload!
    prototype.copyToWithoutPayloadClone(this);
  }

  private void copyToWithoutPayloadClone(AttributeImpl target) {
    super.copyTo(target);
    ((FlagsAttribute) target).setFlags(flags);
    ((PayloadAttribute) target).setPayload(payload);
  }
  
  /**
   * Copy the prototype token's fields into this one, with a different term. Note: Payloads are shared.
   * @param prototype existing Token
   * @param newTerm new term text
   */
  public void reinit(Token prototype, String newTerm) {
    reinit(prototype);
    setEmpty().append(newTerm);
  }

  /**
   * Copy the prototype token's fields into this one, with a different term. Note: Payloads are shared.
   * @param prototype existing Token
   * @param newTermBuffer buffer containing new term text
   * @param offset the index in the buffer of the first character
   * @param length number of valid characters in the buffer
   */
  public void reinit(Token prototype, char[] newTermBuffer, int offset, int length) {
    reinit(prototype);
    copyBuffer(newTermBuffer, offset, length);
  }

  @Override
  public void copyTo(AttributeImpl target) {
    super.copyTo(target);
    ((FlagsAttribute) target).setFlags(flags);
    ((PayloadAttribute) target).setPayload((payload == null) ? null : payload.clone());
  }

  @Override
  public void reflectWith(AttributeReflector reflector) {
    super.reflectWith(reflector);
    reflector.reflect(FlagsAttribute.class, "flags", flags);
    reflector.reflect(PayloadAttribute.class, "payload", payload);
  }

  /** Convenience factory that returns <code>Token</code> as implementation for the basic
   * attributes and return the default impl (with &quot;Impl&quot; appended) for all other
   * attributes.
   * @since 3.0
   */
  public static final AttributeFactory TOKEN_ATTRIBUTE_FACTORY =
      AttributeFactory.getStaticImplementation(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY, Token.class);
}
