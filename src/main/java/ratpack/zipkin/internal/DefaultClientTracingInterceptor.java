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
package ratpack.zipkin.internal;

import brave.Span;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import ratpack.exec.Execution;
import ratpack.http.MutableHeaders;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;
import ratpack.zipkin.ClientTracingInterceptor;

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.Supplier;

public class DefaultClientTracingInterceptor implements ClientTracingInterceptor {

  private final HttpClientHandler<RequestSpec, HttpResponse> handler;
  private final TraceContext.Injector<MutableHeaders> injector;
  private final Supplier<Optional<Execution>> registrySupplier;

  @Inject
  public DefaultClientTracingInterceptor(final HttpTracing httpTracing) {
    this(httpTracing, Execution::currentOpt);
  }

  public DefaultClientTracingInterceptor(final HttpTracing httpTracing, final Supplier<Optional<Execution>> registry) {
    this.handler = HttpClientHandler.create(httpTracing, new ClientHttpAdapter());
    this.injector = httpTracing.tracing().propagation().injector(MutableHeaders::set);
    this.registrySupplier = registry;
  }

  @Override
  public void request(RequestSpec spec) {
    registrySupplier.get()
        .ifPresent((execution -> {
          final Span span = this.handler.handleSend(injector, spec.getHeaders(), spec);
          final ClientSpanHolder holder = new ClientSpanHolder(span);
          execution.add(holder);
        }));
  }

  @Override
  public void response(HttpResponse response) {
    registrySupplier.get()
        .ifPresent(execution -> {
          execution
              .maybeGet(ClientSpanHolder.class)
              .ifPresent((s) -> {
                Iterable<? extends ClientSpanHolder> i = execution.getAll(ClientSpanHolder.class);
                execution.remove(ClientSpanHolder.class);
                this.handler.handleReceive(response, null, s.span);
                // special case code for tests to ensure the shared test execution doesn't clear out
                // other client spans that are still in flight.
                i.forEach((csh) -> {
                  if (csh != s) {
                    execution.add(csh);
                  }
                });
              });
    });

  }

  @Override
  public void error(Throwable e) {
    registrySupplier.get()
        .ifPresent(execution -> {
          execution
              .maybeGet(ClientSpanHolder.class)
              .ifPresent((s) -> {
                Iterable<? extends ClientSpanHolder> i = execution.getAll(ClientSpanHolder.class);
                execution.remove(ClientSpanHolder.class);
                this.handler.handleReceive(null, e, s.span);
                // special case code for tests to ensure the shared test execution doesn't clear out
                // other client spans that are still in flight.
                i.forEach((csh) -> {
                  if (csh != s) {
                    execution.add(csh);
                  }
                });
              });
        });
  }

  public static class ClientSpanHolder {
    private Span span;

    public ClientSpanHolder(Span span) {
      this.span = span;
    }

    Span getSpan() {
      return this.span;
    }
  }
}
