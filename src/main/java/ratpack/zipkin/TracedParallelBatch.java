/*
 * Copyright 2016-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package ratpack.zipkin;

import brave.propagation.TraceContext;
import java.util.Arrays;
import ratpack.exec.Promise;
import ratpack.exec.util.ParallelBatch;
import ratpack.zipkin.internal.RatpackCurrentTraceContext;

/**
 * Provides factory methods for creating a RatPack {@link ParallelBatch}, with
 * the correct trace context.
 *
 * This is necessary because {@link ParallelBatch}es create forked executions
 * which do not "inherit" registries from the calling execution. Consequently,
 * the trace context must be passed explicitly to the forked executions.
 *
 * @deprecated As of 2.4 brave-ratpack is now providing an {@link ratpack.exec.ExecInitializer} bound in guice by {@link ServerTracingModule}
 * which will deal with passing tracing contexts around Ratpack executions. The propagation is done in {@link ratpack.zipkin.internal.RatpackCurrentTraceContext.TracingPropagationExecInitializer},
 * this kind of propagation is only possible with Ratpack version 1.6 and above as the parent execution is now avaialble.
 *
 * @param <T> the type of value produced by each promise in the batch.
 */
@Deprecated
public final class TracedParallelBatch<T> {

  private final Iterable<? extends Promise<T>> promises;

  private TracedParallelBatch(Iterable<? extends Promise<T>> promises) {
    this.promises = promises;
  }

  /**
   * Create a {@link TracedParallelBatch} for list of {@link Promise}s.
   *
   * @param promises an iterable of Promises
   * @param <T> the type of value produced by each promise
   *
   * @return an instance of {@link TracedParallelBatch}.
   */
  public static <T> TracedParallelBatch<T> of(Iterable<? extends Promise<T>> promises) {
    return new TracedParallelBatch<>(promises);
  }

  /**
   * Create a {@link TracedParallelBatch} for list of {@link Promise}s.
   *
   * @param promises vararg containing a list of {@link Promise}s.
   * @param <T> the type of value produced by each promise
   *
   * @return an instance of {@link TracedParallelBatch}.
   */
  public static <T> TracedParallelBatch<T> of(Promise<T>... promises) {
    return new TracedParallelBatch<>(Arrays.asList(promises));
  }

  /**
   * Create a {@link TracedParallelBatch} for list of {@link Promise}s,
   * with the specified trace context.
   *
   * @param context the trace context
   * @param promises an iterable of Promises
   * @param <T> the type of value produced by each promise
   *
   * @return an instance of {@link ParallelBatch}.
   */
  public static <T> ParallelBatch<T> of(final TraceContext context, Iterable<? extends Promise<T>> promises) {
    return ParallelBatch.of(promises)
        .execInit(execution -> execution.add(RatpackCurrentTraceContext.wrap(context)));
  }

  /**
   * Create a {@link TracedParallelBatch} for list of {@link Promise}s,
   * with the specified trace context.
   *
   * @param context the trace context.
   * @param promises vararg containing a list of {@link Promise}s.
   * @param <T> the type of value produced by each promise
   *
   * @return an instance of {@link ParallelBatch}.
   */
  public static <T> ParallelBatch<T> of(final TraceContext context, Promise<T>... promises) {
    return of(context, Arrays.asList(promises));
  }

  /**
   * Set the trace context.
   *
   * @param context the trace context.
   * @return a ParallelBatch
   */
  public ParallelBatch<T> withContext(final TraceContext context) {
    return of(context, promises);
  }

}
