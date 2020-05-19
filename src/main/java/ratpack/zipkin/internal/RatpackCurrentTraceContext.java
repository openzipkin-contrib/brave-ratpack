/*
 * Copyright 2016-2020 The OpenZipkin Authors
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
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.MDC;
import ratpack.exec.ExecInitializer;
import ratpack.exec.Execution;
import ratpack.exec.ExecutionRef;
import ratpack.registry.MutableRegistry;

public final class RatpackCurrentTraceContext extends CurrentTraceContext {
  public static CurrentTraceContext create(){
    return new Builder().build();
  }

  public static Builder newBuilder(){
    return new Builder();
  }

  public static final class Builder extends CurrentTraceContext.Builder {
    Supplier<MutableRegistry> registrySupplier = Execution::current;

    public Builder registrySupplier(Supplier<MutableRegistry> registrySupplier) {
      this.registrySupplier = registrySupplier;
      return this;
    }

    @Override public Builder addScopeDecorator(ScopeDecorator scopeDecorator) {
      return (Builder) super.addScopeDecorator(scopeDecorator);
    }

    @Override public CurrentTraceContext build() {
      return new RatpackCurrentTraceContext(this);
    }

    Builder() {
    }
  }

  private static final String TRACE_ID_KEY = "TraceId";

  private final Supplier<MutableRegistry> registrySupplier;

  /** @deprecated Please use {@link #newBuilder()} */
  @Deprecated
  public RatpackCurrentTraceContext(Supplier<MutableRegistry> registrySupplier) {
    this.registrySupplier = registrySupplier;
  }

  /** @deprecated Please use {@link #create()} */
  @Deprecated
  public RatpackCurrentTraceContext() {
    this(Execution::current);
  }

  RatpackCurrentTraceContext(Builder builder) {
    this.registrySupplier = builder.registrySupplier;
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
    final MutableRegistry registry = registrySupplier.get();

    // get previous entry if one exists so we can re-add it when
    // the scope is closed.
    final TraceContextHolder previous = registry
        .maybeGet(TraceContextHolder.class)
        .orElse(TraceContextHolder.EMPTY);

    removeAll(registry);

    if (current != null) {
      registry.add(new TraceContextHolder(current));
      MDC.put(TRACE_ID_KEY, current.traceIdString());
    } else {
      registry.add(TraceContextHolder.EMPTY);
      MDC.remove(TRACE_ID_KEY);
    }

    return () -> {
      removeAll(registry);
      registry.add(previous);
      if (previous.context != null) {
        MDC.put(TRACE_ID_KEY, previous.context.traceIdString());
      } else {
        MDC.remove(TRACE_ID_KEY);
      }
    };
  }

  private void removeAll(MutableRegistry registry) {
    registry
      .getAll(TraceContextHolder.class)
      .forEach(tch -> registry.remove(TraceContextHolder.class));
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

  /**
   * ExecInitializer that will propagate the tracing context into any new execution created.
   */
  public static class TracingPropagationExecInitializer implements ExecInitializer {

    @Override
    public void init(Execution execution) {
      Optional<ExecutionRef> maybeParent =  execution.maybeParent();

      maybeParent
        .flatMap(parent -> parent.maybeGet(TraceContextHolder.class))
        .ifPresent(execution::add);

      // Copies forward the HTTP Client instrumentation Span.
      // This is important since the interceptor execution is forked between
      // the request and the response handling.
      maybeParent
          .flatMap(parent -> parent.maybeGet(DefaultClientTracingInterceptor.ClientSpanHolder.class))
          .ifPresent(execution::add);
    }
  }

}
