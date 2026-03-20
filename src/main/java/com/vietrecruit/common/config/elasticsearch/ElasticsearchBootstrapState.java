package com.vietrecruit.common.config.elasticsearch;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Shared state tracker for the Elasticsearch bootstrap process. Used by both {@link
 * ElasticsearchDataBootstrap} and {@link ElasticsearchPopulationHealthIndicator} to report
 * OUT_OF_SERVICE while index population is in progress.
 */
@Component
public class ElasticsearchBootstrapState {

    private final AtomicInteger inProgressCount = new AtomicInteger(0);

    void markStarted() {
        inProgressCount.incrementAndGet();
    }

    void markCompleted() {
        inProgressCount.decrementAndGet();
    }

    public boolean isInProgress() {
        return inProgressCount.get() > 0;
    }
}
