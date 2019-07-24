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

import brave.propagation.TraceContext
import ratpack.exec.Execution
import ratpack.exec.ExecutionRef
import spock.lang.Specification
import ratpack.zipkin.internal.RatpackCurrentTraceContext.TraceContextHolder
import ratpack.zipkin.internal.RatpackCurrentTraceContext.TracingPropagationExecInitializer

class TracePropagationExecInitializerSpec extends Specification {

    def dummyContext() {
        TraceContext
                .newBuilder()
                .traceId(new Random().nextLong())
                .spanId(new Random().nextLong())
                .build()
    }

    def 'should copy context from parent when present'() {
        given:
            def initializer = new TracingPropagationExecInitializer()
            def parent = Mock(ExecutionRef)
            def execution = Mock(Execution)
            def parentContext = dummyContext()
            def parentContextHolder = new TraceContextHolder(parentContext)
        when:
            initializer.init(execution)
        then:
            1 * execution.maybeParent() >> Optional.of(parent)
            1 * parent.maybeGet(TraceContextHolder.class) >> Optional.of(parentContextHolder)
            1 * parent.maybeGet(DefaultClientTracingInterceptor.ClientSpanHolder.class) >> Optional.empty()
            1 * execution.add(parentContextHolder)
            0 * _
    }

    def 'should not copy context when parent is not present'() {
        given:
            def initializer = new TracingPropagationExecInitializer()
            def execution = Mock(Execution)
        when:
            initializer.init(execution)
        then:
            1 * execution.maybeParent() >> Optional.empty()
            0 * _
    }

    def 'should not copy context when parent context is empty'() {
        given:
            def initializer = new TracingPropagationExecInitializer()
            def parent = Mock(ExecutionRef)
            def execution = Mock(Execution)
        when:
            initializer.init(execution)
        then:
            1 * execution.maybeParent() >> Optional.of(parent)
            1 * parent.maybeGet(TraceContextHolder.class) >> Optional.empty()
            1 * parent.maybeGet(DefaultClientTracingInterceptor.ClientSpanHolder.class) >> Optional.empty()
            0 * _
    }

}
