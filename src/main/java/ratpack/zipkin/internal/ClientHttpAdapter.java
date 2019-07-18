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

import brave.http.HttpClientAdapter;
import ratpack.http.client.RequestSpec;

public class ClientHttpAdapter extends HttpClientAdapter<RequestSpec, Integer> {

  @Override
  public String method(RequestSpec requestSpec) {
    return requestSpec.getMethod().getName();
  }

  @Override
  public String url(RequestSpec requestSpec) {
    return requestSpec.getUri().toString();
  }

  @Override
  public String requestHeader(RequestSpec requestSpec, String name) {
    return requestSpec.getHeaders().get(name);
  }

  @Override
  public Integer statusCode(Integer status) {
    return status;
  }
}
