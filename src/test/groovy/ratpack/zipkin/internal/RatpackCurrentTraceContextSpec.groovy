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
package ratpack.zipkin.internal

import brave.propagation.CurrentTraceContext
import brave.propagation.TraceContext
import ratpack.registry.MutableRegistry
import ratpack.registry.Registry
import spock.lang.Specification

class RatpackCurrentTraceContextSpec extends Specification {
    MutableRegistry registry = Registry.mutable()
    CurrentTraceContext traceContext

    def setup() {
        traceContext = new RatpackCurrentTraceContext({ -> registry})
    }

    def dummyContext() {
        TraceContext
            .newBuilder()
            .traceId(new Random().nextLong())
            .spanId(new Random().nextLong())
            .build()
    }

    def 'Initial context should be null'() {
        given:
            def current = traceContext.get()
        expect:
            current == null
    }

    def 'When setting TraceContext span, should return same TraceContext'() {
        given:
            def expected = dummyContext()
        and:
            traceContext.newScope(expected)
        when:
            def result = traceContext.get()
        then:
            result == expected
    }

    def 'When closing a scope, trace context should revert back to previous'() {
        given:
            traceContext.newScope(dummyContext())
            def expected = dummyContext()
            traceContext.newScope(expected)
        and:
            def scope = traceContext.newScope(dummyContext())
        when:
            scope.close()
            def traceContext = traceContext.get()
        then:
            expected ==  traceContext
    }


    def 'When closing a scope, trace context should revert back to previous until null'() {
        given:
            def scope_1 = traceContext.newScope(dummyContext())
            def scope_2 = traceContext.newScope(dummyContext())
            def scope_3 = traceContext.newScope(dummyContext())
        when:
            scope_3.close()
            scope_2.close()
            scope_1.close()
            def traceContext = traceContext.get()
        then:
            traceContext == null
    }

    def 'When TraceContext is null the context should be cleared'() {
        given:
            def expected = dummyContext()
        and:
            traceContext.newScope(expected)
        when:
            def result = traceContext.get()
        then:
            result == expected
        when:
            traceContext.newScope(null)
        then:
            traceContext.get() == null
    }

}
