package com.example.sampling;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.example.sampling.TracingCapture.EndedSpans;
import com.example.sampling.TracingCapture.RecordingSampler;
import com.example.sampling.TracingCapture.SamplerInput;
import com.example.sampling.TracingCapture.StartedObservation;
import com.example.sampling.TracingCapture.StartedObservations;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TracingCapture.class)
class HttpServerSamplingTests {

    @Autowired
    RecordingSampler sampler;

    @Autowired
    EndedSpans endedSpans;

    @Autowired
    StartedObservations startedObservations;

    @Autowired
    Environment environment;

    @Test
    void samplerCannotSeeWhichRequestStarted() throws Exception {
        String port = this.environment.getRequiredProperty("local.server.port");
        // Plain JDK client: not instrumented, so the server span is the root span.
        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/reports/42")).build(),
                    HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        SpanData serverSpan = this.endedSpans.awaitEnded((span) -> span.getKind() == SpanKind.SERVER);
        assertThat(serverSpan.getName()).isEqualTo("http get /reports/{id}");

        SamplerInput input = this.sampler.rootOf(serverSpan.getTraceId());
        // Wanted: at least "http.server.requests" with http.method / method.
        assertThat(input.name()).isEqualTo("<unspecified span name>");
        assertThat(input.kind()).isEqualTo(SpanKind.SERVER);
        assertThat(input.attributes().isEmpty()).isTrue();

        // At start the method is known; the URI template is not, since handler mapping
        // has not run yet. status and outcome hold provisional values that only look final.
        StartedObservation started = this.startedObservations.first("http.server.requests");
        assertThat(started.contextualName()).isNull();
        assertThat(started.lowCardinalityKeyValues()).containsExactlyInAnyOrderEntriesOf(Map.of("method", "GET", "uri",
                "UNKNOWN", "status", "200", "outcome", "SUCCESS", "exception", "none"));
    }

}
