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


import java.util.Arrays;

public final class IntBlockPool {
  public final static int INT_BLOCK_SHIFT = 13;
  public final static int INT_BLOCK_SIZE = 1 << INT_BLOCK_SHIFT;
  public final static int INT_BLOCK_MASK = INT_BLOCK_SIZE - 1;
  
  public abstract static class Allocator {
    protected final int blockSize;

    public Allocator(int blockSize) {
      this.blockSize = blockSize;
    }

    public abstract void recycleIntBlocks(int[][] blocks, int start, int end);

    public int[] getIntBlock() {
      return new int[blockSize];
    }
  }
  
  public static final class DirectAllocator extends Allocator {

    public DirectAllocator() {
      super(INT_BLOCK_SIZE);
    }

    @Override
    public void recycleIntBlocks(int[][] blocks, int start, int end) {
    }
  }
  
  public int[][] buffers = new int[10][];

  private int bufferUpto = -1;
  public int intUpto = INT_BLOCK_SIZE;
  public int[] buffer;
  public int intOffset = -INT_BLOCK_SIZE;

  private final Allocator allocator;

  public IntBlockPool() {
    this(new DirectAllocator());
  }
  
  public IntBlockPool(Allocator allocator) {
    this.allocator = allocator;
  }
  
  public void reset() {
    this.reset(true, true);
  }
  
  public void reset(boolean zeroFillBuffers, boolean reuseFirst) {
    if (bufferUpto != -1) {
      // We allocated at least one buffer

      if (zeroFillBuffers) {
        for(int i=0;i<bufferUpto;i++) {
          // Fully zero fill buffers that we fully used
          Arrays.fill(buffers[i], 0);
        }
        // Partial zero fill the final buffer
        Arrays.fill(buffers[bufferUpto], 0, intUpto, 0);
      }
     
      if (bufferUpto > 0 || !reuseFirst) {
        final int offset = reuseFirst ? 1 : 0;  
        // Recycle all but the first buffer
        allocator.recycleIntBlocks(buffers, offset, 1+bufferUpto);
        Arrays.fill(buffers, offset, bufferUpto+1, null);
      }
      if (reuseFirst) {
        // Re-use the first buffer
        bufferUpto = 0;
        intUpto = 0;
        intOffset = 0;
        buffer = buffers[0];
      } else {
        bufferUpto = -1;
        intUpto = INT_BLOCK_SIZE;
        intOffset = -INT_BLOCK_SIZE;
        buffer = null;
      }
    }
  }
  
  public void nextBuffer() {
    if (1+bufferUpto == buffers.length) {
      int[][] newBuffers = new int[(int) (buffers.length*1.5)][];
      System.arraycopy(buffers, 0, newBuffers, 0, buffers.length);
      buffers = newBuffers;
    }
    buffer = buffers[1+bufferUpto] = allocator.getIntBlock();
    bufferUpto++;

    intUpto = 0;
    intOffset += INT_BLOCK_SIZE;
  }
  
  private int newSlice(final int size) {
    if (intUpto > INT_BLOCK_SIZE-size) {
      nextBuffer();
      assert assertSliceBuffer(buffer);
    }
      
    final int upto = intUpto;
    intUpto += size;
    buffer[intUpto-1] = 1;
    return upto;
  }
  
  private static final boolean assertSliceBuffer(int[] buffer) {
    int count = 0;
    for (int i = 0; i < buffer.length; i++) {
      count += buffer[i]; // for slices the buffer must only have 0 values
    }
    return count == 0;
  }
  
  
  // no need to make this public unless we support different sizes
  // TODO make the levels and the sizes configurable
  private final static int[] NEXT_LEVEL_ARRAY = {1, 2, 3, 4, 5, 6, 7, 8, 9, 9};
  
  private final static int[] LEVEL_SIZE_ARRAY = {2, 4, 8, 16, 32, 64, 128, 256, 512, 1024};
  
  private final static int FIRST_LEVEL_SIZE = LEVEL_SIZE_ARRAY[0];

  private int allocSlice(final int[] slice, final int sliceOffset) {
    final int level = slice[sliceOffset];
    final int newLevel = NEXT_LEVEL_ARRAY[level-1];
    final int newSize = LEVEL_SIZE_ARRAY[newLevel];
    // Maybe allocate another block
    if (intUpto > INT_BLOCK_SIZE-newSize) {
      nextBuffer();
      assert assertSliceBuffer(buffer);
    }

    final int newUpto = intUpto;
    final int offset = newUpto + intOffset;
    intUpto += newSize;
    // Write forwarding address at end of last slice:
    slice[sliceOffset] = offset;
        
    // Write new level:
    buffer[intUpto-1] = newLevel;

    return newUpto;
  }
  
  public static class SliceWriter {
    
    private int offset;
    private final IntBlockPool pool;
    
    
    public SliceWriter(IntBlockPool pool) {
      this.pool = pool;
    }
    public void reset(int sliceOffset) {
      this.offset = sliceOffset;
    }
    
    public void writeInt(int value) {
      int[] ints = pool.buffers[offset >> INT_BLOCK_SHIFT];
      assert ints != null;
      int relativeOffset = offset & INT_BLOCK_MASK;
      if (ints[relativeOffset] != 0) {
        // End of slice; allocate a new one
          relativeOffset = pool.allocSlice(ints, relativeOffset);
        ints = pool.buffer;
        offset = relativeOffset + pool.intOffset;
      }
      ints[relativeOffset] = value;
      offset++; 
    }
    
    public int startNewSlice() {
      return offset = pool.newSlice(FIRST_LEVEL_SIZE) + pool.intOffset;
      
    }
    
    public int getCurrentOffset() {
      return offset;
    }
  }
  
  public static final class SliceReader {
    
    private final IntBlockPool pool;
    private int upto;
    private int bufferUpto;
    private int bufferOffset;
    private int[] buffer;
    private int limit;
    private int level;
    private int end;
    
    public SliceReader(IntBlockPool pool) {
      this.pool = pool;
    }

    public void reset(int startOffset, int endOffset) {
      bufferUpto = startOffset / INT_BLOCK_SIZE;
      bufferOffset = bufferUpto * INT_BLOCK_SIZE;
      this.end = endOffset;
      upto = startOffset;
      level = 1;
      
      buffer = pool.buffers[bufferUpto];
      upto = startOffset & INT_BLOCK_MASK;

      final int firstSize = IntBlockPool.LEVEL_SIZE_ARRAY[0];
      if (startOffset+firstSize >= endOffset) {
        // There is only this one slice to read
        limit = endOffset & INT_BLOCK_MASK;
      } else {
        limit = upto+firstSize-1;
      }

    }
    
    public boolean endOfSlice() {
      assert upto + bufferOffset <= end;
      return upto + bufferOffset == end;
    }
    
    public int readInt() {
      assert !endOfSlice();
      assert upto <= limit;
      if (upto == limit)
        nextSlice();
      return buffer[upto++];
    }
    
    private void nextSlice() {
      // Skip to our next slice
      final int nextIndex = buffer[limit];
      level = NEXT_LEVEL_ARRAY[level-1];
      final int newSize = LEVEL_SIZE_ARRAY[level];

      bufferUpto = nextIndex / INT_BLOCK_SIZE;
      bufferOffset = bufferUpto * INT_BLOCK_SIZE;

      buffer = pool.buffers[bufferUpto];
      upto = nextIndex & INT_BLOCK_MASK;

      if (nextIndex + newSize >= end) {
        // We are advancing to the final slice
        assert end - nextIndex > 0;
        limit = end - bufferOffset;
      } else {
        // This is not the final slice (subtract 4 for the
        // forwarding address at the end of this new slice)
        limit = upto+newSize-1;
      }
    }
  }
}

