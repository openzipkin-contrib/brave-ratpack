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

import zipkin2.Span
import zipkin2.reporter.Reporter

import java.util.concurrent.ConcurrentLinkedDeque

class TestReporter implements Reporter<Span> {

	private final ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>()

	@Override
	void report(Span span) {
		spans.add(span)
	}

	List<Span> getSpans() {
		return spans.asImmutable().toList()
	}

	void reset() {
		spans.clear()
	}
}
