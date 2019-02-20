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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexWriterConfig.OpenMode;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.IOUtils;

public class PersistentSnapshotDeletionPolicy extends SnapshotDeletionPolicy {

  public static final String SNAPSHOTS_PREFIX = "snapshots_";
  private static final int VERSION_START = 0;
  private static final int VERSION_CURRENT = VERSION_START;
  private static final String CODEC_NAME = "snapshots";

  // The index writer which maintains the snapshots metadata
  private long nextWriteGen;

  private final Directory dir;

  public PersistentSnapshotDeletionPolicy(IndexDeletionPolicy primary,
      Directory dir) throws IOException {
    this(primary, dir, OpenMode.CREATE_OR_APPEND);
  }

  public PersistentSnapshotDeletionPolicy(IndexDeletionPolicy primary,
      Directory dir, OpenMode mode) throws IOException {
    super(primary);

    this.dir = dir;

    if (mode == OpenMode.CREATE) {
      clearPriorSnapshots();
    }

    loadPriorSnapshots();

    if (mode == OpenMode.APPEND && nextWriteGen == 0) {
      throw new IllegalStateException("no snapshots stored in this directory");
    }
  }

  @Override
  public synchronized IndexCommit snapshot() throws IOException {
    IndexCommit ic = super.snapshot();
    boolean success = false;
    try {
      persist();
      success = true;
    } finally {
      if (!success) {
        try {
          super.release(ic);
        } catch (Exception e) {
          // Suppress so we keep throwing original exception
        }
      }
    }
    return ic;
  }

  @Override
  public synchronized void release(IndexCommit commit) throws IOException {
    super.release(commit);
    boolean success = false;
    try {
      persist();
      success = true;
    } finally {
      if (!success) {
        try {
          incRef(commit);
        } catch (Exception e) {
          // Suppress so we keep throwing original exception
        }
      }
    }
  }

  public synchronized void release(long gen) throws IOException {
    super.releaseGen(gen);
    persist();
  }

  synchronized private void persist() throws IOException {
    String fileName = SNAPSHOTS_PREFIX + nextWriteGen;
    IndexOutput out = dir.createOutput(fileName, IOContext.DEFAULT);
    boolean success = false;
    try {
      CodecUtil.writeHeader(out, CODEC_NAME, VERSION_CURRENT);   
      out.writeVInt(refCounts.size());
      for(Entry<Long,Integer> ent : refCounts.entrySet()) {
        out.writeVLong(ent.getKey());
        out.writeVInt(ent.getValue());
      }
      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(out);
        IOUtils.deleteFilesIgnoringExceptions(dir, fileName);
      } else {
        IOUtils.close(out);
      }
    }

    dir.sync(Collections.singletonList(fileName));
    
    if (nextWriteGen > 0) {
      String lastSaveFile = SNAPSHOTS_PREFIX + (nextWriteGen-1);
      // exception OK: likely it didn't exist
      IOUtils.deleteFilesIgnoringExceptions(dir, lastSaveFile);
    }

    nextWriteGen++;
  }

  private synchronized void clearPriorSnapshots() throws IOException {
    for(String file : dir.listAll()) {
      if (file.startsWith(SNAPSHOTS_PREFIX)) {
        dir.deleteFile(file);
      }
    }
  }

  public String getLastSaveFile() {
    if (nextWriteGen == 0) {
      return null;
    } else {
      return SNAPSHOTS_PREFIX + (nextWriteGen-1);
    }
  }

  private synchronized void loadPriorSnapshots() throws IOException {
    long genLoaded = -1;
    IOException ioe = null;
    List<String> snapshotFiles = new ArrayList<>();
    for(String file : dir.listAll()) {
      if (file.startsWith(SNAPSHOTS_PREFIX)) {
        long gen = Long.parseLong(file.substring(SNAPSHOTS_PREFIX.length()));
        if (genLoaded == -1 || gen > genLoaded) {
          snapshotFiles.add(file);
          Map<Long,Integer> m = new HashMap<>();
          IndexInput in = dir.openInput(file, IOContext.DEFAULT);
          try {
            CodecUtil.checkHeader(in, CODEC_NAME, VERSION_START, VERSION_START);
            int count = in.readVInt();
            for(int i=0;i<count;i++) {
              long commitGen = in.readVLong();
              int refCount = in.readVInt();
              m.put(commitGen, refCount);
            }
          } catch (IOException ioe2) {
            // Save first exception & throw in the end
            if (ioe == null) {
              ioe = ioe2;
            }
          } finally {
            in.close();
          }

          genLoaded = gen;
          refCounts.clear();
          refCounts.putAll(m);
        }
      }
    }

    if (genLoaded == -1) {
      // Nothing was loaded...
      if (ioe != null) {
        // ... not for lack of trying:
        throw ioe;
      }
    } else { 
      if (snapshotFiles.size() > 1) {
        // Remove any broken / old snapshot files:
        String curFileName = SNAPSHOTS_PREFIX + genLoaded;
        for(String file : snapshotFiles) {
          if (!curFileName.equals(file)) {
            IOUtils.deleteFilesIgnoringExceptions(dir, file);
          }
        }
      }
      nextWriteGen = 1+genLoaded;
    }
  }
}
