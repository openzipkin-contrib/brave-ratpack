/**
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
package ratpack.zipkin.support


enum B3PropagationHeaders {
    TRACE_ID("X-B3-TraceId"),
    SPAN_ID("X-B3-SpanId"),
    PARENT_ID("X-B3-ParentSpanId"),
    SAMPLED("X-B3-Sampled")

    public final String value;
    B3PropagationHeaders(final String value) {
        this.value = value
    }
}