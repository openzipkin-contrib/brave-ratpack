# ratpack-zipkin

[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin)
[![Build Status](https://github.com/openzipkin-contrib/brave-ratpack/workflows/test/badge.svg)](https://github.com/openzipkin-contrib/brave-ratpack/actions?query=workflow%3Atest)
[![Maven Central](https://img.shields.io/maven-central/v/io.zipkin.brave.ratpack/brave-ratpack.svg)](https://search.maven.org/search?q=g:io.zipkin.brave.ratpack%20AND%20a:brave-ratpack)

[Zipkin](https://zipkin.io) support for [Ratpack](http://www.ratpack.io).

Uses [Brave](https://github.com/openzipkin/brave) for the underlying Zipkin support.

## Getting Started

### Zipkin

The quickest way to get started is to fetch the [latest released server](https://search.maven.org/remote_content?g=io.zipkin&a=zipkin-server&v=LATEST&c=exec) as a self-contained executable jar. Note that the Zipkin server requires minimum JRE 8. For example:

```bash
curl -sSL https://zipkin.io/quickstart.sh | bash -s
java -jar zipkin.jar
```

You can also start Zipkin via Docker.
```bash
# Note: this is mirrored as ghcr.io/openzipkin/zipkin
docker run -d -p 9411:9411 openzipkin/zipkin
```

## Local development

To build and install into your local environment:

```
./mvnw clean install
```

## Artifacts
The artifact published is `brave-ratpack` under the group ID `io.zipkin.brave.ratpack`

### Library Releases
Releases are at [Sonatype](https://oss.sonatype.org/content/repositories/releases) and [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.zipkin.brave.ratpack%22)

### Library Snapshots
Snapshots are uploaded to [Sonatype](https://oss.sonatype.org/content/repositories/snapshots) after
commits to master.

## Ratpack-zipkin V2 

Version 2 of this library incorporates Brave 4.x.

### v2 Usage

#### Server Spans

The minimal configuration for enabling tracing of server spans (SR/SS):

```
RatpackServer.start(server -> server
    .serverConfig(config -> config.port(serverPort))
    .registry(Guice.registry(binding -> binding
        .module(ServerTracingModule.class, config -> {
          config
              .serviceName("ratpack-demo")
              .sampler(Sampler.ALWAYS_SAMPLE)
              .spanReporter(aSpanReporter);
        })
    ))
    .handlers(chain -> chain
       ... 
    )
);
```

Where `aSpanReporter` is some instance of `zipkin.reporter.SpanReporter` (e.g. `Reporter.CONSOLE`).

By default, the tracing module uses the Brave HTTP's `HttpServerParser` for parsing HTTP requests
into spans. To customize this behaviour (e.g. add tags based on some data in the request), you'll 
need to subclass `HttpServerParser` and configure the module to use the custom parser.

Span names can be customized by configuring `SpanNameProvider`:

```
config.spanNameProvider((request,pathBindingOpt) -> pathBindingOpt
    .map(pathBinding -> pathBinding.getDescription())
    .orElse(request.getPath())) )
```

Note that due to some Ratpack implementation details, the `PathBinding` may not be present in some edge cases (e.g. if
for some reason an error occurs and no response is sent) - hence the `Optional` type.

#### Client Spans

Client span tracing, for the most part, works the same in v2 as it did in v1. To trace HTTP client spans, use the `@Zipkin` 
annotation to inject a Zipkin instrumented implementation of the Ratpack `HttpClient`.

e.g.


```
@Inject
@Zipkin
HttpClient client
...
client.get(new URI("http://example.com", requestSpec -> ...))
    ... 
```

As with v1, both field and constructor injection will work.

One area where v2 client span tracing differs from v1 is the way in which HTTP requests are parsed into spans. As with 
server spans, by default the tracing module will use Brave HTTP to parse the HTTP request into spans (`HttpClientParser`).
Again, to customize this behaviour, you'll need to extends the Brave HTTP class, and configure the module to use the custom
parser.

#### Nested Spans

This is a feature that we pretty much get "for free" by moving to Brave 4 - and allows you to nest spans. Since it is just
using functionality provided by Brave, there's nothing really Ratpack specific about it. In order to use this feature,
you'll need to `@Inject` a reference to an instance of `brave.http.HttpTracing`. Using that object, you'll be able to
create and start child spans.

```
public class NestedSpanHandler implements Handler {
  @Inject
  private HttpTracing httpTracing;

  @Override
  public void handle(final Context ctx) throws Exception {
    Tracer tracer = httpTracing.tracing().tracer();
    Span child_1 = tracer.newChild(tracer.currentSpan().context()).name("child_1");
    child_1.start();
    try (Tracer.SpanInScope spanInScope_1 = tracer.withSpanInScope(child_1)) {
      Span child_2 = tracer.newChild(child_1.context()).name("child_2");
      child_2.start();
      try (Tracer.SpanInScope spanInScope_2 = tracer.withSpanInScope(child_2)) {
        child_2.finish();
        child_1.finish();
        ctx.getResponse().send("Nested Spans!.");
      }
    }
  }
}
```

One thing to watch out for, `Tracer.SpanInScope` implements `Closable`, which means you can use `try-with-resources` in some
cases. In situations where there is async work going on (e.g. a nested HTTP call), you will probably have to deal with the
closing of each `SpanInScope` yourself.


#### Traced Parallel Batches

`TracedParallelBatch` provides some factory methods that can be used to create instances of `ParallelBatch`, with a given
parent trace context.

Example:

```
ParallelBatch<Integer> batch
    = TracedParallelBatch.of(
    client.get(new URI("http://foo.com"))
          .map(ReceivedResponse::getStatusCode),
    client.get(new URI("http://bar.com"))
          .map(ReceivedResponse::getStatusCode),
    client.get(new URI("http://bazz.com"))
          .map(ReceivedResponse::getStatusCode))
                          .withContext(currentContext);
```

### Zipkin V2 Support

To configure the library to use Zipkin v2, set the `SpanReporter` like this:

```
.module(ServerTracingModule.class, config -> {
        config
            .serviceName("ratpack-demo")
            .spanReporterV2(reporter);
    })
```

...there `reporter` is some Zipkin v2 reporter.

E.g.

```
Reporter<Span> reporter =
    AsyncReporter.create(OkHttpSender
                         .create(String.format("http://%s:9411/api/v2/spans",
                                               okHttpHost)));
```

Note:
- The V1 version of this library targeting the older Brave library is now *deprecated* and is no longer released.
  If this is a problem for anyone please open an issue and we will re-evaluate the need to continue support for this.
- The Brave V1 Span Reporter support is now *deprecated*.
