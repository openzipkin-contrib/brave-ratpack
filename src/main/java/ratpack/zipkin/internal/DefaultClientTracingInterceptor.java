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
import ratpack.registry.MutableRegistry;
import ratpack.zipkin.ClientTracingInterceptor;

import javax.inject.Inject;
import java.util.Iterator;

public class DefaultClientTracingInterceptor implements ClientTracingInterceptor {

  private static final TypeToken<Span> SpanToken = new TypeToken<Span>() {};

  private final HttpClientHandler<RequestSpec, HttpResponse> handler;
  private final HttpTracing httpTracing;
  private final TraceContext.Injector<MutableHeaders> injector;
  private final Execution execution;

  @Inject
  public DefaultClientTracingInterceptor(final HttpTracing httpTracing, final Execution execution) {
    this.execution = execution;
    this.httpTracing = httpTracing;
    this.handler = HttpClientHandler.create(httpTracing, new ClientHttpAdapter());
    this.injector = httpTracing.tracing().propagation().injector(MutableHeaders::set);
  }

  @Override
  public void request(RequestSpec spec) {
    final Span span = this.handler.handleSend(injector, spec.getHeaders(), spec);
    this.execution.add(span);
    spec.onRedirect(res -> redirectHandler(res, span));
  }

  // On redirects the request method is called before the redirect handler. If the Span was stored
  // in the execution it will be overwritten by the redirecting request.  For this reason we
  // need to pass the original span along to complete the request handling here instead.
  private Action<? super RequestSpec> redirectHandler(ReceivedResponse response, Span span) {
    return (spec) -> {
      handler.handleReceive(response, null, span);

      Iterator<? extends Span> stack = this.execution.getAll(Span.class).iterator();

      this.execution.remove(Span.class);

      while(stack.hasNext()) {
        Span next = stack.next();
        if (next.context().spanId() != span.context().spanId()) {
          this.execution.add(next);
        }
      }
    };
  }

  @Override
  public void response(HttpResponse response) {

    Iterator<? extends Span> stack = this.execution.getAll(Span.class).iterator();

    if (stack.hasNext()) {
      Span last = stack.next();
      this.handler.handleReceive(response, null, last);
      this.execution.remove(Span.class);
      while(stack.hasNext()) {
        this.execution.add(stack.next());
      }
    }
  }

  @Override
  public void error(Throwable e) {
    Iterator<? extends Span> stack = this.execution.getAll(Span.class).iterator();

    if (stack.hasNext()) {
      Span last = stack.next();
      this.handler.handleReceive(null, e, last);
      this.execution.remove(Span.class);
      while(stack.hasNext()) {
        this.execution.add(stack.next());
      }
    }
  }
}
