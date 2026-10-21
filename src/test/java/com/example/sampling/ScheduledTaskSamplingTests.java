package com.example.sampling;

import com.example.sampling.TracingCapture.EndedSpans;
import com.example.sampling.TracingCapture.RecordingSampler;
import com.example.sampling.TracingCapture.SamplerInput;
import com.example.sampling.TracingCapture.StartedObservation;
import com.example.sampling.TracingCapture.StartedObservations;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TracingCapture.class)
class ScheduledTaskSamplingTests {

    @Autowired
    RecordingSampler sampler;

    @Autowired
    EndedSpans endedSpans;

    @Autowired
    StartedObservations startedObservations;

    @Test
    void samplerCannotSeeWhichScheduledTaskStarted() {
        SpanData taskSpan = this.endedSpans.awaitEnded((span) -> span.getName().equals("task reportScheduler.run"));

        SamplerInput input = this.sampler.rootOf(taskSpan.getTraceId());
        // Wanted: "task reportScheduler.run", or at least "tasks.scheduled.execution"
        // with code.namespace / code.function, so a Sampler can drop this task.
        assertThat(input.name()).isEqualTo("<unspecified span name>");
        assertThat(input.attributes().isEmpty()).isTrue();

        // The Observation already knew its name and low cardinality key values when
        // the handler's onStart ran. Only the contextual name is computed at stop.
        StartedObservation started = this.startedObservations.first("tasks.scheduled.execution");
        assertThat(started.contextualName()).isNull();
        assertThat(started.lowCardinalityKeyValues()).containsEntry("code.namespace", ReportScheduler.class.getName())
            .containsEntry("code.function", "run");
    }

}
