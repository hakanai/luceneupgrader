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

package org.trypticon.luceneupgrader.lucene9.internal.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ThreadInterruptedException;

/**
 * Executor wrapper responsible for the execution of concurrent tasks. Used to parallelize search
 * across segments as well as query rewrite in some cases. Exposes a single {@link
 * #invokeAll(Collection)} method that takes a collection of {@link Callable}s and executes them
 * concurrently. Once all but one task have been submitted to the executor, it tries to run as many
 * tasks as possible on the calling thread, then waits for all tasks that have been executed in
 * parallel on the executor to be completed and then returns a list with the obtained results.
 *
 * @lucene.experimental
 */
public final class TaskExecutor {
  private final Executor executor;

  /**
   * Creates a TaskExecutor instance
   *
   * @param executor the executor to be used for running tasks concurrently
   */
  public TaskExecutor(Executor executor) {
    Objects.requireNonNull(executor, "Executor is null");
    this.executor =
        r -> {
          try {
            executor.execute(r);
          } catch (
              @SuppressWarnings("unused")
              RejectedExecutionException rejectedExecutionException) {
            // execute directly on the current thread in case of rejection to ensure a rejecting
            // executor only reduces parallelism and does not
            // result in failure
            r.run();
          }
        };
  }

  /**
   * Execute all the callables provided as an argument, wait for them to complete and return the
   * obtained results. If an exception is thrown by more than one callable, the subsequent ones will
   * be added as suppressed exceptions to the first one that was caught.
   *
   * @param callables the callables to execute
   * @return a list containing the results from the tasks execution
   * @param <T> the return type of the task execution
   */
  public <T> List<T> invokeAll(Collection<Callable<T>> callables) throws IOException {
    TaskGroup<T> taskGroup = new TaskGroup<>(callables);
    return taskGroup.invokeAll(executor);
  }

  @Override
  public String toString() {
    return "TaskExecutor(" + "executor=" + executor + ')';
  }

  /**
   * Holds all the sub-tasks that a certain operation gets split into as it gets parallelized and
   * exposes the ability to invoke such tasks and wait for them all to complete their execution and
   * provide their results. Additionally, if one task throws an exception, all other tasks from the
   * same group are cancelled, to avoid needless computation as their results would not be exposed
   * anyways. Creates one {@link FutureTask} for each {@link Callable} provided
   *
   * @param <T> the return type of all the callables
   */
  private static final class TaskGroup<T> {
    private final List<RunnableFuture<T>> futures;

    TaskGroup(Collection<Callable<T>> callables) {
      List<RunnableFuture<T>> tasks = new ArrayList<>(callables.size());
      for (Callable<T> callable : callables) {
        tasks.add(createTask(callable));
      }
      this.futures = Collections.unmodifiableList(tasks);
    }

    RunnableFuture<T> createTask(Callable<T> callable) {
      return new FutureTask<>(callable) {

        private final AtomicBoolean startedOrCancelled = new AtomicBoolean(false);

        @Override
        public void run() {
          if (startedOrCancelled.compareAndSet(false, true)) {
            super.run();
          }
        }

        @Override
        protected void setException(Throwable t) {
          super.setException(t);
          cancelAll();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
          assert mayInterruptIfRunning == false
              : "cancelling tasks that are running is not supported";
          /*
          Future#get (called in invokeAll) throws CancellationException when invoked against a running task that has been cancelled but
          leaves the task running. We rather want to make sure that invokeAll does not leave any running tasks behind when it returns.
          Overriding cancel ensures that tasks that are already started will complete normally once cancelled, and Future#get will
          wait for them to finish instead of throwing CancellationException. A cleaner way would have been to override FutureTask#get and
          make it wait for cancelled tasks, but FutureTask#awaitDone is private. Tasks that are cancelled before they are started will be no-op.
           */
          if (startedOrCancelled.compareAndSet(false, true)) {
            // task is cancelled hence it has no results to return. That's fine: they would be
            // ignored anyway.
            set(null);
            return true;
          }
          return false;
        }
      };
    }

    List<T> invokeAll(Executor executor) throws IOException {
      final int count = futures.size();
      // taskId provides the first index of an un-executed task in #futures
      final AtomicInteger taskId = new AtomicInteger(0);
      // we fork execution count - 1 tasks to execute at least one task on the current thread to
      // minimize needless forking and blocking of the current thread
      if (count > 1) {
        final Runnable work =
            () -> {
              int id = taskId.getAndIncrement();
              if (id < count) {
                futures.get(id).run();
              }
            };
        for (int j = 0; j < count - 1; j++) {
          executor.execute(work);
        }
      }
      // try to execute as many tasks as possible on the current thread to minimize context
      // switching in case of long running concurrent
      // tasks as well as dead-locking if the current thread is part of #executor for executors that
      // have limited or no parallelism
      int id;
      while ((id = taskId.getAndIncrement()) < count) {
        futures.get(id).run();
        if (id >= count - 1) {
          // save redundant CAS in case this was the last task
          break;
        }
      }
      Throwable exc = null;
      List<T> results = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        Future<T> future = futures.get(i);
        try {
          results.add(future.get());
        } catch (InterruptedException e) {
          exc = IOUtils.useOrSuppress(exc, new ThreadInterruptedException(e));
        } catch (ExecutionException e) {
          exc = IOUtils.useOrSuppress(exc, e.getCause());
        }
      }
      assert assertAllFuturesCompleted() : "Some tasks are still running?";
      if (exc != null) {
        throw IOUtils.rethrowAlways(exc);
      }
      return results;
    }

    private boolean assertAllFuturesCompleted() {
      for (RunnableFuture<T> future : futures) {
        if (future.isDone() == false) {
          return false;
        }
      }
      return true;
    }

    private void cancelAll() {
      for (Future<T> future : futures) {
        future.cancel(false);
      }
    }
  }
}
