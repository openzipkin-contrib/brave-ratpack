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
package brave.http;

import brave.test.http.ITHttpServer;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import java.io.IOException;
import org.junit.Ignore;
import org.junit.Test;
import ratpack.exec.Promise;
import ratpack.func.Action;
import ratpack.guice.Guice;
import ratpack.server.RatpackServerSpec;
import ratpack.test.embed.EmbeddedApp;
import ratpack.zipkin.ServerTracingModule;

import java.net.URI;

public class ITServerTracingModule extends ITHttpServer {
  private EmbeddedApp app;
  @Override
  protected void init() {
    Module tracingModule = Modules
        .override(new ServerTracingModule())
        .with(binder -> binder.bind(HttpTracing.class).toInstance(httpTracing));
    Action<? super RatpackServerSpec> definition = server ->
            server.registry(Guice.registry(binding -> binding.module(tracingModule)))
                  .handlers(chain -> chain
                      .options("", ctx -> ctx.getResponse().send(""))
                      .get("foo", ctx -> ctx.getResponse().send("bar"))
                      .get("async", ctx ->
                          Promise.async(f -> f.success("bar")).then(ctx::render)
                      )
                      .get("badrequest", ctx -> ctx.getResponse().status(400).send())
                      .get("child", ctx -> {
                        HttpTracing httpTracing = ctx.get(HttpTracing.class);
                        httpTracing.tracing().tracer().nextSpan().name("child").start().finish();
                        ctx.getResponse().send("happy");
                      })
                      .get("items/:itemId", ctx ->
                          ctx.getResponse().send(ctx.getPathTokens().get("itemId"))
                      )
                      .get("async_items/:itemId", ctx ->
                          Promise.async(f -> f.success(ctx.getPathTokens().get("itemId")))
                              .then(ctx::render)
                      )
                      .prefix("nested", nested -> nested.get("items/:itemId", ctx ->
                          ctx.getResponse().send(ctx.getPathTokens().get("itemId"))
                      ))
                      .get("baggage", ctx -> ctx.getResponse().send(BAGGAGE_FIELD.getValue()))
                      .get("exception", ctx -> {
                        throw NOT_READY_ISE;
                      })
                      .get("exceptionAsync",
                          ctx -> Promise.async((f) -> f.error(NOT_READY_ISE))
                              .then(f -> ctx.getResponse().status(503).send())
                      )
                      .all(ctx -> ctx.getResponse().status(404).send()));
    try {
      app = EmbeddedApp.of(definition);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected String url(final String path) {
    URI uri = app.getAddress();
    return String
        .format("%s://%s:%d/%s", uri.getScheme(), "127.0.0.1", uri.getPort(), path.replaceFirst("/", ""));
  }

  // TODO: migrate to Brave's HttpClientRequest/HttpClientResponse types
  @Ignore @Test @Override public void readsRequestAtResponseTime() throws IOException {
    super.readsRequestAtResponseTime();
  }

  // TODO: can't change status code on uncaught exception
  @Ignore @Test @Override
  public void httpStatusCodeSettable_onUncaughtException() throws IOException {
    super.httpStatusCodeSettable_onUncaughtException();
  }
  @Ignore @Test @Override
  public void httpStatusCodeSettable_onUncaughtException_async() throws IOException {
    super.httpStatusCodeSettable_onUncaughtException_async();
  }

  // TODO: errors are not handled in DefaultServerTracingHandler
  @Ignore @Test @Override
  public void setsErrorAndHttpStatusOnUncaughtException() throws IOException {
    super.setsErrorAndHttpStatusOnUncaughtException();
  }
  @Ignore @Test @Override
  public void setsErrorAndHttpStatusOnUncaughtException_async() throws IOException {
    super.setsErrorAndHttpStatusOnUncaughtException_async();
  }
  @Ignore @Test @Override public void spanHandlerSeesError() throws IOException {
    super.spanHandlerSeesError();
  }
  @Ignore @Test @Override public void spanHandlerSeesError_async() throws IOException {
    super.spanHandlerSeesError();
  }
}
