# micrometer-tracing #1450 reproducer

Minimal reproducer for [micrometer-metrics/tracing#1450](https://github.com/micrometer-metrics/tracing/issues/1450):
an OpenTelemetry `Sampler` cannot tell which observation started a span. It gets no
usable span name and no attributes.

Spring Boot 4.1.1, `spring-boot-starter-opentelemetry` (Micrometer Tracing 1.7.1, OpenTelemetry SDK 1.62.0), Java 21.

```
./gradlew test
```

All tests pass. Each test pins the current behavior.

| Test | What the `Sampler` gets | Name of the ended span |
|---|---|---|
| `ScheduledTaskSamplingTests` (`@Scheduled ReportScheduler.run`) | name `<unspecified span name>`, no attributes | `task reportScheduler.run` |
| `HttpServerSamplingTests` (`GET /reports/42`) | name `<unspecified span name>`, kind `SERVER`, no attributes | `http get /reports/{id}` |
| `DisabledTaskObservationTests` (task observation turned off by an `ObservationPredicate`) | the observation created inside the task (`report.query`) becomes its own root span | `report.query` |

The tests also record what each `Observation.Context` holds when the handlers' `onStart` runs:

| Observation | `name` at start | `contextualName` at start | low cardinality key values at start |
|---|---|---|---|
| scheduled task | `tasks.scheduled.execution` | `null` | `code.namespace=com.example.sampling.ReportScheduler`, `code.function=run`, `exception=none`, `outcome=UNKNOWN` |
| HTTP server | `http.server.requests` | `null` | `method=GET`, `uri=UNKNOWN`, `status=200`, `outcome=SUCCESS`, `exception=none` |

So the observation name and the convention's low cardinality key values are already on the
context at `onStart`. Some of those values are provisional: the HTTP request has not been handled
yet, but `status=200` and `outcome=SUCCESS` are already there. `SimpleObservation.start()` applies the convention before it notifies
the handlers. The tracing handlers start the span without them, and set the name and tags in `onStop`.

## Where the captures come from

`TracingCapture` registers three beans:

- a `Sampler` that records every `shouldSample` call and samples everything
- a `SpanProcessor` that records ended spans
- an `ObservationHandler` that snapshots each context at `onStart`
