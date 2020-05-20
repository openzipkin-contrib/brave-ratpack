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

import brave.propagation.CurrentTraceContext;
import brave.test.http.ITHttpAsyncClient;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import ratpack.exec.Promise;
import ratpack.func.Block;
import ratpack.http.client.HttpClient;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;
import ratpack.registry.MutableRegistry;
import ratpack.server.ServerConfig;
import ratpack.test.exec.ExecHarness;
import ratpack.util.Exceptions;
import ratpack.zipkin.ClientTracingInterceptor;
import ratpack.zipkin.internal.DefaultClientTracingInterceptor;
import ratpack.zipkin.internal.RatpackCurrentTraceContext;

public class ITRatpackHttpClient extends ITHttpAsyncClient<HttpClient> {
  private static ExecHarness harness;
  private static AtomicReference<MutableRegistry> registry = new AtomicReference<>();

  @Override protected CurrentTraceContext.Builder currentTraceContextBuilder() {
    return RatpackCurrentTraceContext.newBuilder().registrySupplier(registry::get);
  }

  @BeforeClass
  public static void beforeAll() {
    harness = ExecHarness.harness();
  }

  @AfterClass
  public static void afterAll() {
    harness.close();
  }

  @Override protected HttpClient newClient(int port) {
    return Exceptions.uncheck(() -> harness.yield(e -> {
      ClientTracingInterceptor clientTracingInterceptor =
          new DefaultClientTracingInterceptor(httpTracing, () -> Optional.of(e));
      return Promise.value(HttpClient.of(s -> s
          .poolSize(0)
          .requestIntercept(clientTracingInterceptor::request)
          .responseIntercept(clientTracingInterceptor::response)
          .errorIntercept(clientTracingInterceptor::error)
          .byteBufAllocator(UnpooledByteBufAllocator.DEFAULT)
          .maxContentLength(ServerConfig.DEFAULT_MAX_CONTENT_LENGTH)));
    }).getValue());
  }

  @Override protected void closeClient(HttpClient client) {
    client.close();
  }

  @Override protected void get(HttpClient client, String pathIncludingQuery) {
    try {
      harness.yield(e -> client.get(URI.create(url(pathIncludingQuery)))).getValueOrThrow();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override protected void post(HttpClient client, String pathIncludingQuery, String body) {
    try {
      harness.yield(e ->
          client.post(URI.create(url(pathIncludingQuery)), (request ->
              request.body(b -> b.text(body))
          ))).getValueOrThrow();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void get(HttpClient client, String path, BiConsumer<Integer, Throwable> callback) {
    try {
      harness.run(e ->
          client.requestStream(URI.create(url(path)), RequestSpec::get)
              .map(HttpResponse::getStatusCode)
              .toCompletableFuture()
              .whenComplete(callback)
      );
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override public void usesParentFromInvocationTime() {
    runWithHarness(super::usesParentFromInvocationTime);
  }

  @Override public void callbackContextIsFromInvocationTime() {
    runWithHarness(super::callbackContextIsFromInvocationTime);
  }

  @Override public void callbackContextIsFromInvocationTime_root() {
    runWithHarness(super::callbackContextIsFromInvocationTime_root);
  }

  @Override public void addsStatusCodeWhenNotOk_async() {
    runWithHarness(super::addsStatusCodeWhenNotOk_async);
  }

  @Override public void propagatesNewTrace() {
    runWithHarness(super::propagatesNewTrace);
  }

  @Override public void propagatesChildOfCurrentSpan() {
    runWithHarness(super::propagatesChildOfCurrentSpan);
  }

  @Override public void propagatesUnsampledContext() {
    runWithHarness(super::propagatesUnsampledContext);
  }

  @Override public void propagatesBaggage() {
    runWithHarness(super::propagatesBaggage);
  }

  @Override public void propagatesBaggage_unsampled() {
    runWithHarness(super::propagatesBaggage_unsampled);
  }

  @Override public void customSampler() {
    runWithHarness(super::customSampler);
  }

  // TODO: flakey
  @Ignore @Test  public void clientTimestampAndDurationEnclosedByParent() {
    runWithHarness(super::clientTimestampAndDurationEnclosedByParent);
  }

  @Override public void reportsClientKindToZipkin() {
    runWithHarness(super::reportsClientKindToZipkin);
  }

  // TODO: implement server address
  @Ignore @Test @Override public void reportsServerAddress() {
    runWithHarness(super::reportsServerAddress);
  }

  @Override public void defaultSpanNameIsMethodName() {
    runWithHarness(super::defaultSpanNameIsMethodName);
  }

  // TODO: migrate to Brave's HttpClientRequest/HttpClientResponse types
  @Ignore @Test @Override public void readsRequestAtResponseTime() {
    runWithHarness(super::readsRequestAtResponseTime);
  }

  @Override public void supportsPortableCustomization() {
    runWithHarness(super::supportsPortableCustomization);
  }

  @Override public void supportsDeprecatedPortableCustomization() {
    runWithHarness(super::supportsDeprecatedPortableCustomization);
  }

  @Override public void addsStatusCodeWhenNotOk() {
    runWithHarness(super::addsStatusCodeWhenNotOk);
  }

  // Ignoring for now because the way Ratpack Client does redirects is not compatible
  // with the upstream integration tests.  We should revisit this and determine if there
  // is another way like annotating a span to indicate that the redirect took place.
  @Ignore @Test @Override public void redirect() {
    runWithHarness(super::redirect);
  }

  @Override public void post() {
    runWithHarness(super::post);
  }

  @Override public void httpPathTagExcludesQueryParams() {
    runWithHarness(super::httpPathTagExcludesQueryParams);
  }

  @Override public void spanHandlerSeesError() {
    runWithHarness(super::spanHandlerSeesError);
  }

  @Override public void setsError_onTransportException() {
    runWithHarness(super::setsError_onTransportException);
  }

  /** This ensures the execution environment binds to the {@link RatpackCurrentTraceContext}. */
  static void runWithHarness(Block function) {
    try {
      harness.run(e -> {
            registry.set(e);
            function.execute();
          }
      );
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
