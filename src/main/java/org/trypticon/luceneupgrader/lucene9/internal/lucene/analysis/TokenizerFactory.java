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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.analysis;

import java.util.Map;
import java.util.Set;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.AttributeFactory;

/**
 * Abstract parent class for analysis factories that create {@link Tokenizer} instances.
 *
 * @since 3.1
 */
public abstract class TokenizerFactory extends AbstractAnalysisFactory {

  /**
   * This static holder class prevents classloading deadlock by delaying init of factories until
   * needed.
   */
  private static final class Holder {
    private static final AnalysisSPILoader<TokenizerFactory> LOADER =
        new AnalysisSPILoader<>(TokenizerFactory.class);

    private Holder() {}

    static AnalysisSPILoader<TokenizerFactory> getLoader() {
      if (LOADER == null) {
        throw new IllegalStateException(
            "You tried to lookup a TokenizerFactory by name before all factories could be initialized. "
                + "This likely happens if you call TokenizerFactory#forName from a TokenizerFactory ctor.");
      }
      return LOADER;
    }
  }

  /** looks up a tokenizer by name from context classpath */
  public static TokenizerFactory forName(String name, Map<String, String> args) {
    return Holder.getLoader().newInstance(name, args);
  }

  /** looks up a tokenizer class by name from context classpath */
  public static Class<? extends TokenizerFactory> lookupClass(String name) {
    return Holder.getLoader().lookupClass(name);
  }

  /** returns a list of all available tokenizer names from context classpath */
  public static Set<String> availableTokenizers() {
    return Holder.getLoader().availableServices();
  }

  /** looks up a SPI name for the specified tokenizer factory */
  public static String findSPIName(Class<? extends TokenizerFactory> serviceClass) {
    try {
      return AnalysisSPILoader.lookupSPIName(serviceClass);
    } catch (NoSuchFieldException | IllegalAccessException | IllegalStateException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Reloads the factory list from the given {@link ClassLoader}. Changes to the factories are
   * visible after the method ends, all iterators ({@link #availableTokenizers()},...) stay
   * consistent.
   *
   * <p><b>NOTE:</b> Only new factories are added, existing ones are never removed or replaced.
   *
   * <p><em>This method is expensive and should only be called for discovery of new factories on the
   * given classpath/classloader!</em>
   */
  public static void reloadTokenizers(ClassLoader classloader) {
    Holder.getLoader().reload(classloader);
  }

  /** Default ctor for compatibility with SPI */
  protected TokenizerFactory() {
    super();
  }

  /** Initialize this factory via a set of key-value pairs. */
  protected TokenizerFactory(Map<String, String> args) {
    super(args);
  }

  /** Creates a TokenStream of the specified input using the default attribute factory. */
  public final Tokenizer create() {
    return create(TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY);
  }

  /** Creates a TokenStream of the specified input using the given AttributeFactory */
  public abstract Tokenizer create(AttributeFactory factory);
}
