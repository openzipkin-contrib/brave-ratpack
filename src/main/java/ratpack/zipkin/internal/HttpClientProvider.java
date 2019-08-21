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

import com.google.inject.Inject;
import com.google.inject.Provider;
import ratpack.http.client.HttpClient;
import ratpack.util.Exceptions;
import ratpack.zipkin.ClientTracingInterceptor;

/**
 * Provide the zipkin annotated http client,layered on top of a default
 * or consumer provided http client.
 */
public class HttpClientProvider implements Provider<HttpClient> {

    private HttpClient httpClient;
    private ClientTracingInterceptor clientTracingInterceptor;

    @Inject
    public HttpClientProvider(HttpClient httpClient, ClientTracingInterceptor clientTracingInterceptor) {
        this.clientTracingInterceptor = clientTracingInterceptor;
        this.httpClient = httpClient;
    }

    @Override
    public HttpClient get() {
        return Exceptions.uncheck(() ->
                httpClient.copyWith((s) -> {
                    s.requestIntercept(clientTracingInterceptor::request);
                    s.responseIntercept(clientTracingInterceptor::response);
                    s.errorIntercept(clientTracingInterceptor::error);
                }));
    }

}
