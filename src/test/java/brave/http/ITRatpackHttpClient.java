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
package brave.http;

import brave.ScopedSpan;
import brave.Tracer;
import brave.sampler.Sampler;
import brave.test.http.ITHttpAsyncClient;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

import okhttp3.mockwebserver.MockResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import ratpack.exec.Execution;
import ratpack.exec.Promise;
import ratpack.http.client.HttpClient;
import ratpack.http.client.RequestSpec;
import ratpack.server.ServerConfig;
import ratpack.test.exec.ExecHarness;
import ratpack.util.Exceptions;
import ratpack.zipkin.ClientTracingInterceptor;
import ratpack.zipkin.internal.DefaultClientTracingInterceptor;
import ratpack.zipkin.internal.RatpackCurrentTraceContext;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITRatpackHttpClient extends ITHttpAsyncClient<HttpClient> {

    private static ExecHarness harness;

    @BeforeClass
    public static void beforeAll() {
        harness = ExecHarness.harness();
    }

    @AfterClass
    public static void afterAll() {
        harness.close();
    }

    void harnessSetup(Execution execution) {
        currentTraceContext = new RatpackCurrentTraceContext(() -> execution);
        httpTracing = HttpTracing.create(tracingBuilder(Sampler.ALWAYS_SAMPLE).build());
        client = newClient(server.getPort());
    }

    @Override protected HttpClient newClient(int port) {
        return Exceptions.uncheck(() -> harness.yield(e -> {
            ClientTracingInterceptor clientTracingInterceptor = new DefaultClientTracingInterceptor(httpTracing, () -> Optional.of(e));
            return Promise.value(HttpClient.of(s -> s
                .poolSize(0)
                .requestIntercept(clientTracingInterceptor::request)
                .responseIntercept(clientTracingInterceptor::response)
                .errorIntercept(clientTracingInterceptor::error)
                .byteBufAllocator(UnpooledByteBufAllocator.DEFAULT)
                .maxContentLength(ServerConfig.DEFAULT_MAX_CONTENT_LENGTH)));
        }).getValue());
    }

    @Override protected void closeClient(HttpClient client) throws IOException {
        client.close();
    }

    @Override protected void get(HttpClient client, String pathIncludingQuery) throws Exception {
        harness.yield(e -> client.get(URI.create(url(pathIncludingQuery)))).getValueOrThrow();
    }

    @Override protected void post(HttpClient client, String pathIncludingQuery, String body)
        throws Exception {
        harness.yield(e ->
            client.post(URI.create(url(pathIncludingQuery)), (request ->
                request.body(b -> b.text(body))
            ))
        ).getValueOrThrow();
    }

    @Override protected void getAsync(HttpClient client, String pathIncludingQuery) throws Exception {
        harness.yield(e ->
            client.requestStream(URI.create(url(pathIncludingQuery)), RequestSpec::get)
        );
    }

    @Override @Test(expected = AssertionError.class)
    public void reportsServerAddress() throws Exception { // doesn't know the remote address
        super.reportsServerAddress();
    }

    @Test
    @Override public void makesChildOfCurrentSpan() throws Exception {
        harness.run( e -> {
            harnessSetup(e);
            super.makesChildOfCurrentSpan();
        });
    }

    // Ignoring for now because the way Ratpack Client does redirects is not compatible
    // with the upstream integration tests.  We should revisit this and determine if there
    // is another way like annotating a span to indicate that the redirect took place.
    @Ignore @Test
    @Override public void redirect() throws Exception {
        harness.run( e -> {
            harnessSetup(e);
            super.redirect();
        });
    }

    // Ignoring for now because the test hangs on Travis CI.  Locally and with an example app,
    // this pattern doesn't cause any issues.  Maybe with a move to Circle CI we can reenable the test.
    @Ignore @Test
    @Override public void usesParentFromInvocationTime() throws Exception {
        harness.run( e -> {
            harnessSetup(e);
            super.usesParentFromInvocationTime();
        });
    }

    @Test
    @Override public void propagatesExtra_unsampledTrace() throws Exception {
        harness.run( e -> {
            harnessSetup(e);
            super.propagatesExtra_unsampledTrace();
        });
    }

    @Test
    @Override public void propagatesExtra_newTrace() throws Exception {
        harness.run( e -> {
            harnessSetup(e);
            super.propagatesExtra_newTrace();
        });
    }
}
