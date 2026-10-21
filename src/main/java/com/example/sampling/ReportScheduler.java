package com.example.sampling;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A frequently running scheduled task. In a real application this would run every
 * second and its traces are the ones a sampler should drop.
 */
@Component
public class ReportScheduler {

    private final ObservationRegistry observationRegistry;

    public ReportScheduler(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Scheduled(fixedDelay = 200)
    public void run() {
        // Stands in for an observation created inside the task, e.g. a JDBC query
        // instrumented by datasource-micrometer.
        Observation.createNotStarted("report.query", this.observationRegistry).observe(() -> {
        });
    }

}
