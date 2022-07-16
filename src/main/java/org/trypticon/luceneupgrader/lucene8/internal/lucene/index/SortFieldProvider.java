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

package org.trypticon.luceneupgrader.lucene8.internal.lucene.index;

import java.io.IOException;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.SortField;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.NamedSPILoader;

public abstract class SortFieldProvider implements NamedSPILoader.NamedSPI {

  private static class Holder {
    private static final NamedSPILoader<SortFieldProvider> LOADER = new NamedSPILoader<>(SortFieldProvider.class);

    static NamedSPILoader<SortFieldProvider> getLoader() {
      if (LOADER == null) {
        throw new IllegalStateException("You tried to lookup a SortFieldProvider by name before all SortFieldProviders could be initialized. "+
            "This likely happens if you call SortFieldProvider#forName from a SortFieldProviders's ctor.");
      }
      return LOADER;
    }
  }

  public static SortFieldProvider forName(String name) {
    return Holder.getLoader().lookup(name);
  }

  public static Set<String> availableSortFieldProviders() {
    return Holder.getLoader().availableServices();
  }

  public static void reloadSortFieldProviders(ClassLoader classLoader) {
    Holder.getLoader().reload(classLoader);
  }

  public static void write(SortField sf, DataOutput output) throws IOException {
    IndexSorter sorter = sf.getIndexSorter();
    if (sorter == null) {
      throw new IllegalArgumentException("Cannot serialize sort field " + sf);
    }
    SortFieldProvider provider = SortFieldProvider.forName(sorter.getProviderName());
    provider.writeSortField(sf, output);
  }

  protected final String name;

  protected SortFieldProvider(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  public abstract SortField readSortField(DataInput in) throws IOException;

  public abstract void writeSortField(SortField sf, DataOutput out) throws IOException;

}
