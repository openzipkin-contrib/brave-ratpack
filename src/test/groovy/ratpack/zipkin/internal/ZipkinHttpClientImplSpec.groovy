/**
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
package ratpack.zipkin.internal

import brave.Tracing
import brave.http.HttpTracing
import brave.propagation.TraceContext
import brave.sampler.Sampler
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.handler.codec.http.HttpResponseStatus
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions
import ratpack.exec.Execution
import ratpack.exec.Promise
import ratpack.func.Action
import ratpack.http.HttpMethod
import ratpack.http.client.HttpClient
import ratpack.http.client.RequestSpec
import ratpack.http.client.StreamedResponse
import ratpack.server.ServerConfig
import ratpack.test.exec.ExecHarness
import ratpack.util.Exceptions
import ratpack.zipkin.ClientTracingInterceptor
import ratpack.zipkin.support.TestReporter
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll
import zipkin2.Annotation
import zipkin2.Span

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

import static org.assertj.core.api.Assertions.assertThat

class ZipkinHttpClientImplSpec extends Specification {

	MockWebServer webServer
	URI uri
	@AutoCleanup
	ExecHarness harness = ExecHarness.harness()
	HttpTracing httpTracing

	HttpClient zipkinHttpClient
	BlockingQueue<Span> spans = new LinkedBlockingDeque<>()

	Action<? super RequestSpec> action = Action.noop()

	def setup() {
		webServer = new MockWebServer()
		webServer.start()
		uri = webServer.url("/").url().toURI()
	}

	def cleanup() {
		webServer.shutdown()
	}

	Span takeSpan() {
		Span result = this.spans.poll(3L, TimeUnit.SECONDS)
		assertThat(result).withFailMessage("", new Object[0]).isNotNull()
		assertThat(result.annotations().find {v -> v.value() == "context.leak"} == null)
		return result
	}

	void harnessSetup(Execution e) {

		httpTracing = HttpTracing.create(Tracing.newBuilder()
				.currentTraceContext(new RatpackCurrentTraceContext({ -> e}))
				.spanReporter({ s ->
					TraceContext current = this.httpTracing.tracing().currentTraceContext().get()
					boolean contextLeak = false;
					if (current != null && current.spanIdString() == s.id()) {
						s = s.toBuilder().addAnnotation(s.timestampAsLong(), "context.leak").build()
						contextLeak = true
					}
					this.spans.add(s)
					if (contextLeak) {
						throw new AssertionError("context.leak on " + Thread.currentThread().getName())
					}
				}).sampler(Sampler.ALWAYS_SAMPLE)
				.localServiceName("embedded")
				.build())

		ClientTracingInterceptor clientTracingInterceptor = new DefaultClientTracingInterceptor(httpTracing, e)

		zipkinHttpClient = HttpClient.of { spec -> spec
				.poolSize(0)
				.requestIntercept(clientTracingInterceptor.&request)
				.responseIntercept(clientTracingInterceptor.&response)
				.errorIntercept(clientTracingInterceptor.&error)
				.byteBufAllocator(UnpooledByteBufAllocator.DEFAULT)
				.maxContentLength(ServerConfig.DEFAULT_MAX_CONTENT_LENGTH)
		}
	}

	@Unroll
	def "#method requests should be traced"(HttpMethod method) {
		given:
			webServer.enqueue(new MockResponse().setResponseCode(200))
		when:
			harness.yield { e ->
				harnessSetup(e)
				zipkinHttpClient.request(uri, {spec -> spec.method(method)})
			}
		then:
			Span span = takeSpan()
			span.name() == method.name.toLowerCase()
		and: "should contain client span"
			assertThat(span.kind() == Span.Kind.CLIENT)
		where:
			method | _
			HttpMethod.GET | _
			HttpMethod.POST | _
			HttpMethod.PUT | _
			HttpMethod.PATCH | _
			HttpMethod.DELETE | _
			HttpMethod.HEAD | _
			HttpMethod.OPTIONS | _
	}

	def "Request should not duplicate headers sent"() {
		given:
			webServer.enqueue(new MockResponse().setResponseCode(200))

		when:
			harness.yield { e ->
				harnessSetup(e)
				zipkinHttpClient.request(uri, {spec ->
					spec.method(HttpMethod.GET).headers.add("X-TEST", "test")
				})
			}

		then:
			Span span = takeSpan()
			span.name == "get"

		then:
			RecordedRequest request = webServer.takeRequest()
			request.headers.values("X-TEST").size() == 1
	}

	def "Request returning 2xx includes HTTP_METHOD and HTTP_PATH tags (but *not* status code)"(HttpResponseStatus status) {
		given:
			webServer.enqueue(new MockResponse().setResponseCode(status.code()))
		when:
			harness.yield { e ->
				harnessSetup(e)
				zipkinHttpClient.get(uri, action)
			}.value
		then:
			Span span = takeSpan()
		and: "should contain method and path tag but not status code tag"
			assertThat(span.tags()).containsOnlyKeys("http.method", "http.path")
		where:
			status | _
			HttpResponseStatus.OK | _
			HttpResponseStatus.ACCEPTED | _
			HttpResponseStatus.ACCEPTED | _
			HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION | _
			HttpResponseStatus.NO_CONTENT | _
			HttpResponseStatus.RESET_CONTENT | _
			HttpResponseStatus.PARTIAL_CONTENT | _
	}

	def "Request returning 4xx includes HTTP_METHOD, HTTP_PATH and HTTP_STATUS_CODE tags"(HttpResponseStatus status) {
		given:
			webServer.enqueue(new MockResponse().setResponseCode(status.code()))
		when:
			harness.yield { e ->
				harnessSetup(e)
				zipkinHttpClient.post(uri, action)
			}
		then:
			Span span = takeSpan()
		and: "should contain http status code, path and error tags"
			assertThat(span.tags()).containsOnlyKeys("http.method", "http.path", "http.status_code", "error")
		where:
			status | _
			HttpResponseStatus.BAD_REQUEST | _
			HttpResponseStatus.UNAUTHORIZED | _
			HttpResponseStatus.PAYMENT_REQUIRED | _
			HttpResponseStatus.FORBIDDEN | _
			HttpResponseStatus.NOT_FOUND | _
			HttpResponseStatus.METHOD_NOT_ALLOWED | _
			HttpResponseStatus.NOT_ACCEPTABLE | _
			HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED | _
			HttpResponseStatus.REQUEST_TIMEOUT | _
			HttpResponseStatus.CONFLICT | _
			HttpResponseStatus.GONE | _
	}

	def "Request returning 5xx includes HTTP_METHOD, HTTP_PATH and HTTP_STATUS_CODE tags"(HttpResponseStatus status) {
		given:
			webServer.enqueue(new MockResponse().setResponseCode(status.code()))
		when:
			harness.yield { e ->
				harnessSetup(e)
				zipkinHttpClient.get(uri, action)
			}
		then:
			Span span = takeSpan()
		and: "should contain status, path and error tags"
			assertThat(span.tags()).containsOnlyKeys("http.method", "http.path", "http.status_code", "error")
		where:
			status | _
			HttpResponseStatus.INTERNAL_SERVER_ERROR | _
			HttpResponseStatus.NOT_IMPLEMENTED | _
			HttpResponseStatus.BAD_GATEWAY | _
			HttpResponseStatus.SERVICE_UNAVAILABLE | _
			HttpResponseStatus.GATEWAY_TIMEOUT | _
			HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED | _
			HttpResponseStatus.VARIANT_ALSO_NEGOTIATES | _
	}

	def "Should trace streamed requests" () {
		given:
			webServer.enqueue(new MockResponse().setResponseCode(200))
		when:
			StreamedResponse response = harness.yield { e ->
				harnessSetup(e)
				zipkinHttpClient.requestStream(uri, action.append({ RequestSpec s -> s.get()}))
			}.value
		then:
			response.getStatusCode() == 200
			Span span = takeSpan()
		and: "should contain method and path tags, but not status code tag"
			assertThat(span.tags()).containsOnlyKeys("http.method", "http.path")
	}
}
