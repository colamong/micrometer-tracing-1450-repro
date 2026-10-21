package com.example.sampling;

import com.example.sampling.TracingCapture.EndedSpans;
import com.example.sampling.TracingCapture.RecordingSampler;
import com.example.sampling.TracingCapture.SamplerInput;
import io.micrometer.observation.ObservationPredicate;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The usual workaround: since the Sampler cannot tell the task apart, turn the task
 * observation off with an {@link ObservationPredicate}. Observations created inside the
 * task then start their own traces.
 */
@SpringBootTest
@Import({ TracingCapture.class, DisabledTaskObservationTests.DisableScheduledTasks.class })
class DisabledTaskObservationTests {

    @Autowired
    RecordingSampler sampler;

    @Autowired
    EndedSpans endedSpans;

    @Test
    void childObservationOfDisabledTaskBecomesRootSpan() {
        SpanData querySpan = this.endedSpans.awaitEnded((span) -> span.getName().equals("report.query"));

        assertThat(querySpan.getParentSpanContext().isValid()).isFalse();
        SamplerInput input = this.sampler.rootOf(querySpan.getTraceId());
        assertThat(input.name()).isEqualTo("<unspecified span name>");
        assertThat(this.endedSpans.spans).noneMatch((span) -> span.getName().startsWith("task "));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DisableScheduledTasks {

        @Bean
        ObservationPredicate disableScheduledTasks() {
            return (name, context) -> !name.equals("tasks.scheduled.execution");
        }

    }

}
