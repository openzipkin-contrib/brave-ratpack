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
package ratpack.zipkin;

import com.google.common.net.HostAndPort;
import ratpack.http.Headers;
import ratpack.http.HttpMethod;

/**
 * Read-only API for wrappers around Ratpack's {@link ratpack.http.Request}.
 */
public interface ServerRequest {
  /**
   * The method of the request.
   *
   * @return The method of the request.
   */
  HttpMethod getMethod();
  /**
   * The complete URI of the request (path + query string).
   *
   * @return The complete URI of the request (path + query string).
   */
  String getUri();

  /**
   * The URI without the query string and leading forward slash.
   *
   * @return The URI without the query string and leading forward slash
   */
  String getPath();

  /**
   * The request headers.
   *
   * @return The request headers.
   */
  Headers getHeaders();

  /**
   * The full URL of the request (scheme, host, port, etc.)
   * @return the full URL
   */
  String getUrl();

  /**
   * The address of the client making the request.
   * @return the host and port for the client
   */
  HostAndPort getRemoteAddress();
}
