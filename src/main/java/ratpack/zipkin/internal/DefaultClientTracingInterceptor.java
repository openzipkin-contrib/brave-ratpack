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
import com.google.common.reflect.TypeToken;
import ratpack.exec.Execution;
import ratpack.func.Action;
import ratpack.http.MutableHeaders;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.ReceivedResponse;
import ratpack.http.client.RequestSpec;
import ratpack.zipkin.ClientTracingInterceptor;

import javax.inject.Inject;

public class DefaultClientTracingInterceptor implements ClientTracingInterceptor {

  private static final TypeToken<Span> SpanToken = new TypeToken<Span>() {};

  private final HttpClientHandler<RequestSpec, Integer> handler;
  private final TraceContext.Injector<MutableHeaders> injector;
  private final Execution execution;

  @Inject
  public DefaultClientTracingInterceptor(final HttpTracing httpTracing, final Execution execution) {
    this.execution = execution;
    this.handler = HttpClientHandler.create(httpTracing, new ClientHttpAdapter());
    this.injector = httpTracing.tracing().propagation().injector(MutableHeaders::set);
  }

  @Override
  public void request(RequestSpec spec) {
    final Span span = this.handler.handleSend(injector, spec.getHeaders(), spec);
    this.execution.add(SpanToken, span);
    spec.onRedirect(res -> redirectHandler(res, span));
  }

  private Action<? super RequestSpec> redirectHandler(ReceivedResponse response, Span span) {
    return (spec) -> handler.handleReceive(response.getStatusCode(), null, span);
  }

  @Override
  public void response(HttpResponse response) {
    this.execution
        .maybeGet(SpanToken)
        .ifPresent(s -> this.handler.handleReceive(response.getStatusCode(), null, s));
  }

  @Override
  public void error(Throwable e) {
    this.execution
        .maybeGet(SpanToken)
        .ifPresent(s -> this.handler.handleReceive(null, e, s));
  }
}
