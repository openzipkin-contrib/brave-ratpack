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

import com.google.common.net.HttpHeaders;
import ratpack.path.PathBinding;
import ratpack.zipkin.ServerRequest;
import ratpack.zipkin.ServerResponse;
import zipkin2.Endpoint;

/**
 * This class is responsible for adapting Ratpack-specific request and responses
 * to something that brave.http.HttpServerParser can use to create the Span.
 */
final class ServerHttpAdapter extends brave.http.HttpServerAdapter<ServerRequest, ServerResponse> {
  @Override
  public boolean parseClientAddress(final ServerRequest serverRequest,
                                    final Endpoint.Builder builder) {
    String forwardedFor = requestHeader(serverRequest, HttpHeaders.X_FORWARDED_FOR);
    if (forwardedFor != null) {
      return builder.parseIp(forwardedFor);
    }
    return builder.parseIp(serverRequest.getRemoteAddress().getHostText());
  }

  @Override public String method(ServerRequest request) {
    return request.getMethod().getName();
  }

  @Override public String path(ServerRequest request) {
    // docs say request.getPath() is without a leading slash, but it isn't guaranteed.
    String result = request.getPath();
    return result.indexOf('/') == 0 ? result : "/" + result;
  }

  @Override public String url(ServerRequest request) {
    return request.getUrl();
  }

  @Override public String requestHeader(ServerRequest request, String name) {
    return request.getHeaders().get(name);
  }

  @Override public String methodFromResponse(ServerResponse response) {
    return response.getRequest().getMethod().getName();
  }

  @Override public String route(ServerResponse response) {
    String result = response.pathBinding().map(PathBinding::getDescription).orElse("");
    if (result.isEmpty()) return result;
    return result.indexOf('/') == 0 ? result : "/" + result;
  }

  @Override public Integer statusCode(ServerResponse response) {
    return response.getStatus().getCode();
  }
}
