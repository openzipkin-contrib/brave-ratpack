/*
 * Copyright 2016-2018 The OpenZipkin Authors
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
package ratpack.zipkin.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.function.Supplier;
import org.slf4j.MDC;
import ratpack.exec.Execution;
import ratpack.registry.MutableRegistry;

public final class RatpackCurrentTraceContext extends CurrentTraceContext {

  private static final String TRACE_ID_KEY = "TraceId";

  private final Supplier<MutableRegistry> registrySupplier;

  public RatpackCurrentTraceContext(Supplier<MutableRegistry> registrySupplier) {
    this.registrySupplier = registrySupplier;
  }

  public RatpackCurrentTraceContext() {
    this(Execution::current);
  }

  @Override
  public TraceContext get() {
    return registrySupplier.get()
        .maybeGet(TraceContextHolder.class)
        .map(h -> h.context)
        .orElse(null);
  }

  @Override
  public Scope newScope(TraceContext current) {
    final TraceContextHolder previous = registrySupplier.get()
        .maybeGet(TraceContextHolder.class)
        .orElse(TraceContextHolder.EMPTY);

    if (current != null) {
      registrySupplier.get().add(new TraceContextHolder(current));
      MDC.put(TRACE_ID_KEY, current.traceIdString());
    } else {
      registrySupplier.get().add(TraceContextHolder.EMPTY);
      MDC.remove(TRACE_ID_KEY);
    }

    return () -> {
      registrySupplier.get().add(previous);
      if (previous.context != null) {
        MDC.put(TRACE_ID_KEY, previous.context.traceIdString());
      } else {
        MDC.remove(TRACE_ID_KEY);
      }
    };
  }

  /**
   * Used by TracedParallelBatch where its used to wrap a TraceContext and puts it in the
   * registry for the forked execution.  This is marked deprecated as we prefer not to
   * expose details of the RatpackCurrentTraceContext implementation.
   *
   * @param traceContext a trace context.
   * @return a holder for the trace context, which can be put into the registry.
   */
  @Deprecated
  public static TraceContextHolder wrap(TraceContext traceContext) {
    return (traceContext != null) ? new TraceContextHolder(traceContext) : TraceContextHolder.EMPTY;
  }

  private static final class TraceContextHolder {

    private static final TraceContextHolder EMPTY = new TraceContextHolder(null);

    private final TraceContext context;

    private TraceContextHolder(final TraceContext context) {
      this.context = context;
    }
  }

}
