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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.FlagsAttribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.PackedTokenAttributeImpl;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.PayloadAttribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsAndPositionsEnum; // for javadoc
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Attribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.AttributeFactory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.AttributeImpl;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.AttributeReflector;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;

@Deprecated
public class Token extends PackedTokenAttributeImpl implements FlagsAttribute, PayloadAttribute {

  private int flags;
  private BytesRef payload;

  public Token() {
  }


  public Token(int start, int end) {
    setOffset(start, end);
  }


  public Token(int start, int end, String typ) {
    setOffset(start, end);
    setType(typ);
  }

  public Token(int start, int end, int flags) {
    setOffset(start, end);
    setFlags(flags);
  }


  public Token(CharSequence text, int start, int end) {
    append(text);
    setOffset(start, end);
  }


  public Token(String text, int start, int end, String typ) {
    append(text);
    setOffset(start, end);
    setType(typ);
  }

  public Token(String text, int start, int end, int flags) {
    append(text);
    setOffset(start, end);
    setFlags(flags);
  }

  public Token(char[] startTermBuffer, int termBufferOffset, int termBufferLength, int start, int end) {
    copyBuffer(startTermBuffer, termBufferOffset, termBufferLength);
    setOffset(start, end);
  }

  @Override
  public int getFlags() {
    return flags;
  }

  @Override
  public void setFlags(int flags) {
    this.flags = flags;
  }

  @Override
  public BytesRef getPayload() {
    return this.payload;
  }

  @Override
  public void setPayload(BytesRef payload) {
    this.payload = payload;
  }
  

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


  public Token reinit(char[] newTermBuffer, int newTermOffset, int newTermLength, int newStartOffset, int newEndOffset, String newType) {
    clear();
    copyBuffer(newTermBuffer, newTermOffset, newTermLength);
    setOffset(newStartOffset, newEndOffset);
    setType(newType);
    return this;
  }


  public Token reinit(char[] newTermBuffer, int newTermOffset, int newTermLength, int newStartOffset, int newEndOffset) {
    clear();
    copyBuffer(newTermBuffer, newTermOffset, newTermLength);
    setOffset(newStartOffset, newEndOffset);
    return this;
  }


  public Token reinit(String newTerm, int newStartOffset, int newEndOffset, String newType) {
    clear();
    append(newTerm);
    setOffset(newStartOffset, newEndOffset);
    setType(newType);
    return this;
  }


  public Token reinit(String newTerm, int newTermOffset, int newTermLength, int newStartOffset, int newEndOffset, String newType) {
    clear();
    append(newTerm, newTermOffset, newTermOffset + newTermLength);
    setOffset(newStartOffset, newEndOffset);
    setType(newType);
    return this;
  }


  public Token reinit(String newTerm, int newStartOffset, int newEndOffset) {
    clear();
    append(newTerm);
    setOffset(newStartOffset, newEndOffset);
    return this;
  }


  public Token reinit(String newTerm, int newTermOffset, int newTermLength, int newStartOffset, int newEndOffset) {
    clear();
    append(newTerm, newTermOffset, newTermOffset + newTermLength);
    setOffset(newStartOffset, newEndOffset);
    return this;
  }

  public void reinit(Token prototype) {
    // this is a bad hack to emulate no cloning of payload!
    prototype.copyToWithoutPayloadClone(this);
  }

  private void copyToWithoutPayloadClone(AttributeImpl target) {
    super.copyTo(target);
    ((FlagsAttribute) target).setFlags(flags);
    ((PayloadAttribute) target).setPayload(payload);
  }
  
  public void reinit(Token prototype, String newTerm) {
    reinit(prototype);
    setEmpty().append(newTerm);
  }

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


  public static final AttributeFactory TOKEN_ATTRIBUTE_FACTORY =
      AttributeFactory.getStaticImplementation(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY, Token.class);
}
