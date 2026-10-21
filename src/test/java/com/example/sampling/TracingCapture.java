package com.example.sampling;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.awaitility.Awaitility.await;

/**
 * Captures, in-process, what the OpenTelemetry {@link Sampler} is given when a span
 * starts, the spans that end, and what each {@link Observation} knows when it starts.
 */
@TestConfiguration(proxyBeanMethods = false)
class TracingCapture {

    @Bean
    RecordingSampler recordingSampler() {
        return new RecordingSampler();
    }

    @Bean
    EndedSpans endedSpans() {
        return new EndedSpans();
    }

    @Bean
    StartedObservations startedObservations() {
        return new StartedObservations();
    }

    record SamplerInput(String traceId, String name, SpanKind kind, Attributes attributes, boolean hasParent) {
    }

    /** Records every {@code shouldSample} call and samples everything. */
    static class RecordingSampler implements Sampler {

        final List<SamplerInput> inputs = new CopyOnWriteArrayList<>();

        @Override
        public SamplingResult shouldSample(Context parentContext, String traceId, String name, SpanKind spanKind,
                Attributes attributes, List<LinkData> parentLinks) {
            boolean hasParent = Span.fromContext(parentContext).getSpanContext().isValid();
            // Copy: the SDK may hand over the map the started span keeps writing to.
            Attributes snapshot = Attributes.builder().putAll(attributes).build();
            this.inputs.add(new SamplerInput(traceId, name, spanKind, snapshot, hasParent));
            return SamplingResult.recordAndSample();
        }

        /** The sampler input of the root span of the given trace. */
        SamplerInput rootOf(String traceId) {
            return this.inputs.stream()
                .filter((input) -> input.traceId().equals(traceId) && !input.hasParent())
                .findFirst()
                .orElseThrow();
        }

        @Override
        public String getDescription() {
            return "RecordingSampler";
        }

    }

    static class EndedSpans implements SpanProcessor {

        final List<SpanData> spans = new CopyOnWriteArrayList<>();

        SpanData awaitEnded(Predicate<SpanData> predicate) {
            return await().atMost(Duration.ofSeconds(10))
                .until(() -> this.spans.stream().filter(predicate).findFirst(), (span) -> span.isPresent())
                .orElseThrow();
        }

        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
        }

        @Override
        public boolean isStartRequired() {
            return false;
        }

        @Override
        public void onEnd(ReadableSpan span) {
            this.spans.add(span.toSpanData());
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

    }

    record StartedObservation(String name, String contextualName, Map<String, String> lowCardinalityKeyValues) {
    }

    /** Snapshots each observation's context at {@code onStart}. */
    static class StartedObservations implements ObservationHandler<Observation.Context> {

        final List<StartedObservation> observations = new CopyOnWriteArrayList<>();

        StartedObservation first(String name) {
            return this.observations.stream().filter((o) -> name.equals(o.name())).findFirst().orElseThrow();
        }

        @Override
        public void onStart(Observation.Context context) {
            Map<String, String> lowCardinality = StreamSupport
                .stream(context.getLowCardinalityKeyValues().spliterator(), false)
                .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
            this.observations.add(new StartedObservation(context.getName(), context.getContextualName(), lowCardinality));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

}
