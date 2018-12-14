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

import ratpack.http.Status;
import ratpack.path.PathBinding;

import java.util.Optional;

/**
 * Interface for a wrapper around {@link ratpack.http.Response} that provides
 * some additional data that is used in the server parser.
 *
 */
public interface ServerResponse {
  /**
   * The path binding.
   *
   * @return Optional of PathBinding
   */
  Optional<PathBinding> pathBinding();

  /**
   * The original request.
   *
   * @return the server request
   */
  ServerRequest getRequest();

  /**
   * The HTTP status of the response.
   *
   * @return the HTTP status of the response
   */
  Status getStatus();
}
