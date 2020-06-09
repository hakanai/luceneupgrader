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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes;


import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.AttributeImpl;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.AttributeReflector;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;

public class PayloadAttributeImpl extends AttributeImpl implements PayloadAttribute, Cloneable {
  private BytesRef payload;  
  
  public PayloadAttributeImpl() {}
  
  public PayloadAttributeImpl(BytesRef payload) {
    this.payload = payload;
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
    payload = null;
  }

  @Override
  public PayloadAttributeImpl clone()  {
    PayloadAttributeImpl clone = (PayloadAttributeImpl) super.clone();
    if (payload != null) {
      clone.payload = BytesRef.deepCopyOf(payload);
    }
    return clone;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    
    if (other instanceof PayloadAttribute) {
      PayloadAttributeImpl o = (PayloadAttributeImpl) other;
      if (o.payload == null || payload == null) {
        return o.payload == null && payload == null;
      }
      
      return o.payload.equals(payload);
    }
    
    return false;
  }

  @Override
  public int hashCode() {
    return (payload == null) ? 0 : payload.hashCode();
  }

  @Override
  public void copyTo(AttributeImpl target) {
    PayloadAttribute t = (PayloadAttribute) target;
    t.setPayload((payload == null) ? null : BytesRef.deepCopyOf(payload));
  }  

  @Override
  public void reflectWith(AttributeReflector reflector) {
    reflector.reflect(PayloadAttribute.class, "payload", payload);
  }
}
