package org.trypticon.luceneupgrader.lucene4.internal.lucene.util;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.FieldCache;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.FieldCache.CacheEntry;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.AlreadyClosedException;

public final class FieldCacheSanityChecker {

  public FieldCacheSanityChecker() {
    /* NOOP */
  }



  public static Insanity[] checkSanity(FieldCache cache) {
    return checkSanity(cache.getCacheEntries());
  }


  public static Insanity[] checkSanity(CacheEntry... cacheEntries) {
    FieldCacheSanityChecker sanityChecker = new FieldCacheSanityChecker();
    return sanityChecker.check(cacheEntries);
  }


  public Insanity[] check(CacheEntry... cacheEntries) {
    if (null == cacheEntries || 0 == cacheEntries.length) 
      return new Insanity[0];

    // the indirect mapping lets MapOfSet dedup identical valIds for us
    //
    // maps the (valId) identityhashCode of cache values to 
    // sets of CacheEntry instances
    final MapOfSets<Integer, CacheEntry> valIdToItems = new MapOfSets<>(new HashMap<Integer, Set<CacheEntry>>(17));
    // maps ReaderField keys to Sets of ValueIds
    final MapOfSets<ReaderField, Integer> readerFieldToValIds = new MapOfSets<>(new HashMap<ReaderField, Set<Integer>>(17));
    //

    // any keys that we know result in more then one valId
    final Set<ReaderField> valMismatchKeys = new HashSet<>();

    // iterate over all the cacheEntries to get the mappings we'll need
    for (int i = 0; i < cacheEntries.length; i++) {
      final CacheEntry item = cacheEntries[i];
      final Object val = item.getValue();

      // It's OK to have dup entries, where one is eg
      // float[] and the other is the Bits (from
      // getDocWithField())
      if (val != null && "BitsEntry".equals(val.getClass().getSimpleName())) {
        continue;
      }

      if (val instanceof FieldCache.CreationPlaceholder)
        continue;

      final ReaderField rf = new ReaderField(item.getReaderKey(), 
                                            item.getFieldName());

      final Integer valId = Integer.valueOf(System.identityHashCode(val));

      // indirect mapping, so the MapOfSet will dedup identical valIds for us
      valIdToItems.put(valId, item);
      if (1 < readerFieldToValIds.put(rf, valId)) {
        valMismatchKeys.add(rf);
      }
    }

    final List<Insanity> insanity = new ArrayList<>(valMismatchKeys.size() * 3);

    insanity.addAll(checkValueMismatch(valIdToItems, 
                                       readerFieldToValIds, 
                                       valMismatchKeys));
    insanity.addAll(checkSubreaders(valIdToItems, 
                                    readerFieldToValIds));
                    
    return insanity.toArray(new Insanity[insanity.size()]);
  }


  private Collection<Insanity> checkValueMismatch(MapOfSets<Integer, CacheEntry> valIdToItems,
                                        MapOfSets<ReaderField, Integer> readerFieldToValIds,
                                        Set<ReaderField> valMismatchKeys) {

    final List<Insanity> insanity = new ArrayList<>(valMismatchKeys.size() * 3);

    if (! valMismatchKeys.isEmpty() ) { 
      // we have multiple values for some ReaderFields

      final Map<ReaderField, Set<Integer>> rfMap = readerFieldToValIds.getMap();
      final Map<Integer, Set<CacheEntry>> valMap = valIdToItems.getMap();
      for (final ReaderField rf : valMismatchKeys) {
        final List<CacheEntry> badEntries = new ArrayList<>(valMismatchKeys.size() * 2);
        for(final Integer value: rfMap.get(rf)) {
          for (final CacheEntry cacheEntry : valMap.get(value)) {
            badEntries.add(cacheEntry);
          }
        }

        CacheEntry[] badness = new CacheEntry[badEntries.size()];
        badness = badEntries.toArray(badness);

        insanity.add(new Insanity(InsanityType.VALUEMISMATCH,
                                  "Multiple distinct value objects for " + 
                                  rf.toString(), badness));
      }
    }
    return insanity;
  }


  private Collection<Insanity> checkSubreaders( MapOfSets<Integer, CacheEntry>  valIdToItems,
                                      MapOfSets<ReaderField, Integer> readerFieldToValIds) {

    final List<Insanity> insanity = new ArrayList<>(23);

    Map<ReaderField, Set<ReaderField>> badChildren = new HashMap<>(17);
    MapOfSets<ReaderField, ReaderField> badKids = new MapOfSets<>(badChildren); // wrapper

    Map<Integer, Set<CacheEntry>> viToItemSets = valIdToItems.getMap();
    Map<ReaderField, Set<Integer>> rfToValIdSets = readerFieldToValIds.getMap();

    Set<ReaderField> seen = new HashSet<>(17);

    Set<ReaderField> readerFields = rfToValIdSets.keySet();
    for (final ReaderField rf : readerFields) {
      
      if (seen.contains(rf)) continue;

      List<Object> kids = getAllDescendantReaderKeys(rf.readerKey);
      for (Object kidKey : kids) {
        ReaderField kid = new ReaderField(kidKey, rf.fieldName);
        
        if (badChildren.containsKey(kid)) {
          // we've already process this kid as RF and found other problems
          // track those problems as our own
          badKids.put(rf, kid);
          badKids.putAll(rf, badChildren.get(kid));
          badChildren.remove(kid);
          
        } else if (rfToValIdSets.containsKey(kid)) {
          // we have cache entries for the kid
          badKids.put(rf, kid);
        }
        seen.add(kid);
      }
      seen.add(rf);
    }

    // every mapping in badKids represents an Insanity
    for (final ReaderField parent : badChildren.keySet()) {
      Set<ReaderField> kids = badChildren.get(parent);

      List<CacheEntry> badEntries = new ArrayList<>(kids.size() * 2);

      // put parent entr(ies) in first
      {
        for (final Integer value  : rfToValIdSets.get(parent)) {
          badEntries.addAll(viToItemSets.get(value));
        }
      }

      // now the entries for the descendants
      for (final ReaderField kid : kids) {
        for (final Integer value : rfToValIdSets.get(kid)) {
          badEntries.addAll(viToItemSets.get(value));
        }
      }

      CacheEntry[] badness = new CacheEntry[badEntries.size()];
      badness = badEntries.toArray(badness);

      insanity.add(new Insanity(InsanityType.SUBREADER,
                                "Found caches for descendants of " + 
                                parent.toString(),
                                badness));
    }

    return insanity;

  }

  private List<Object> getAllDescendantReaderKeys(Object seed) {
    List<Object> all = new ArrayList<>(17); // will grow as we iter
    all.add(seed);
    for (int i = 0; i < all.size(); i++) {
      final Object obj = all.get(i);
      // TODO: We don't check closed readers here (as getTopReaderContext
      // throws AlreadyClosedException), what should we do? Reflection?
      if (obj instanceof IndexReader) {
        try {
          final List<IndexReaderContext> childs =
            ((IndexReader) obj).getContext().children();
          if (childs != null) { // it is composite reader
            for (final IndexReaderContext ctx : childs) {
              all.add(ctx.reader().getCoreCacheKey());
            }
          }
        } catch (AlreadyClosedException ace) {
          // ignore this reader
        }
      }
    }
    // need to skip the first, because it was the seed
    return all.subList(1, all.size());
  }

  private final static class ReaderField {
    public final Object readerKey;
    public final String fieldName;
    public ReaderField(Object readerKey, String fieldName) {
      this.readerKey = readerKey;
      this.fieldName = fieldName;
    }
    @Override
    public int hashCode() {
      return System.identityHashCode(readerKey) * fieldName.hashCode();
    }
    @Override
    public boolean equals(Object that) {
      if (! (that instanceof ReaderField)) return false;

      ReaderField other = (ReaderField) that;
      return (this.readerKey == other.readerKey &&
              this.fieldName.equals(other.fieldName));
    }
    @Override
    public String toString() {
      return readerKey.toString() + "+" + fieldName;
    }
  }

  public final static class Insanity {
    private final InsanityType type;
    private final String msg;
    private final CacheEntry[] entries;
    public Insanity(InsanityType type, String msg, CacheEntry... entries) {
      if (null == type) {
        throw new IllegalArgumentException
          ("Insanity requires non-null InsanityType");
      }
      if (null == entries || 0 == entries.length) {
        throw new IllegalArgumentException
          ("Insanity requires non-null/non-empty CacheEntry[]");
      }
      this.type = type;
      this.msg = msg;
      this.entries = entries;
      
    }
    public InsanityType getType() { return type; }
    public String getMsg() { return msg; }
    public CacheEntry[] getCacheEntries() { return entries; }
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder();
      buf.append(getType()).append(": ");

      String m = getMsg();
      if (null != m) buf.append(m);

      buf.append('\n');

      CacheEntry[] ce = getCacheEntries();
      for (int i = 0; i < ce.length; i++) {
        buf.append('\t').append(ce[i].toString()).append('\n');
      }

      return buf.toString();
    }
  }

  public final static class InsanityType {
    private final String label;
    private InsanityType(final String label) {
      this.label = label;
    }
    @Override
    public String toString() { return label; }

    public final static InsanityType SUBREADER
      = new InsanityType("SUBREADER");

    public final static InsanityType VALUEMISMATCH
      = new InsanityType("VALUEMISMATCH");

    public final static InsanityType EXPECTED
      = new InsanityType("EXPECTED");
  }
  
  
}
