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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.util;

import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;

public final class CollectionUtil {

  private CollectionUtil() {} // no instance
  
  private static <T> SorterTemplate getSorter(final List<T> list, final Comparator<? super T> comp) {
    if (!(list instanceof RandomAccess))
      throw new IllegalArgumentException("CollectionUtil can only sort random access lists in-place.");
    return new SorterTemplate() {
      @Override
      protected void swap(int i, int j) {
        Collections.swap(list, i, j);
      }
      
      @Override
      protected int compare(int i, int j) {
        return comp.compare(list.get(i), list.get(j));
      }

      @Override
      protected void setPivot(int i) {
        pivot = list.get(i);
      }
  
      @Override
      protected int comparePivot(int j) {
        return comp.compare(pivot, list.get(j));
      }
      
      private T pivot;
    };
  }
  
  private static <T extends Comparable<? super T>> SorterTemplate getSorter(final List<T> list) {
    if (!(list instanceof RandomAccess))
      throw new IllegalArgumentException("CollectionUtil can only sort random access lists in-place.");
    return new SorterTemplate() {
      @Override
      protected void swap(int i, int j) {
        Collections.swap(list, i, j);
      }
      
      @Override
      protected int compare(int i, int j) {
        return list.get(i).compareTo(list.get(j));
      }

      @Override
      protected void setPivot(int i) {
        pivot = list.get(i);
      }
  
      @Override
      protected int comparePivot(int j) {
        return pivot.compareTo(list.get(j));
      }
      
      private T pivot;
    };
  }

  public static <T> void quickSort(List<T> list, Comparator<? super T> comp) {
    final int size = list.size();
    if (size <= 1) return;
    getSorter(list, comp).quickSort(0, size-1);
  }
  
  public static <T extends Comparable<? super T>> void quickSort(List<T> list) {
    final int size = list.size();
    if (size <= 1) return;
    getSorter(list).quickSort(0, size-1);
  }

  // mergeSorts:
  
  public static <T> void mergeSort(List<T> list, Comparator<? super T> comp) {
    final int size = list.size();
    if (size <= 1) return;
    getSorter(list, comp).mergeSort(0, size-1);
  }
  
  public static <T extends Comparable<? super T>> void mergeSort(List<T> list) {
    final int size = list.size();
    if (size <= 1) return;
    getSorter(list).mergeSort(0, size-1);
  }

  // insertionSorts:
  
  public static <T> void insertionSort(List<T> list, Comparator<? super T> comp) {
    final int size = list.size();
    if (size <= 1) return;
    getSorter(list, comp).insertionSort(0, size-1);
  }
  
  public static <T extends Comparable<? super T>> void insertionSort(List<T> list) {
    final int size = list.size();
    if (size <= 1) return;
    getSorter(list).insertionSort(0, size-1);
  }
  
}