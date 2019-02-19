package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;

import java.io.IOException;

public class CachingSpanFilter extends SpanFilter {
  private SpanFilter filter;

  private final CachingWrapperFilter.FilterCache<SpanFilterResult> cache;

  public CachingSpanFilter(SpanFilter filter) {
    this(filter, CachingWrapperFilter.DeletesMode.RECACHE);
  }

  public CachingSpanFilter(SpanFilter filter, CachingWrapperFilter.DeletesMode deletesMode) {
    this.filter = filter;
    if (deletesMode == CachingWrapperFilter.DeletesMode.DYNAMIC) {
      throw new IllegalArgumentException("DeletesMode.DYNAMIC is not supported");
    }
    this.cache = new CachingWrapperFilter.FilterCache<SpanFilterResult>(deletesMode) {
      @Override
      protected SpanFilterResult mergeDeletes(final IndexReader reader, final SpanFilterResult value) {
        throw new IllegalStateException("DeletesMode.DYNAMIC is not supported");
      }
    };
  }

  @Override
  public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
    SpanFilterResult result = getCachedResult(reader);
    return result != null ? result.getDocIdSet() : null;
  }
  
  // for testing
  int hitCount, missCount;

  private SpanFilterResult getCachedResult(IndexReader reader) throws IOException {

    final Object coreKey = reader.getCoreCacheKey();
    final Object delCoreKey = reader.hasDeletions() ? reader.getDeletesCacheKey() : coreKey;

    SpanFilterResult result = cache.get(reader, coreKey, delCoreKey);
    if (result != null) {
      hitCount++;
      return result;
    }

    missCount++;
    result = filter.bitSpans(reader);

    cache.put(coreKey, delCoreKey, result);
    return result;
  }


  @Override
  public SpanFilterResult bitSpans(IndexReader reader) throws IOException {
    return getCachedResult(reader);
  }

  @Override
  public String toString() {
    return "CachingSpanFilter("+filter+")";
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CachingSpanFilter)) return false;
    return this.filter.equals(((CachingSpanFilter)o).filter);
  }

  @Override
  public int hashCode() {
    return filter.hashCode() ^ 0x1117BF25;
  }
}
