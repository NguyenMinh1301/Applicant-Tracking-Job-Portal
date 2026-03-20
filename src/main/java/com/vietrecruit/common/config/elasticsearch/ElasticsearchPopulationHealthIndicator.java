package com.vietrecruit.common.config.elasticsearch;

import static com.vietrecruit.common.config.elasticsearch.ElasticsearchConstants.*;

import java.io.IOException;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Health indicator that reports OUT_OF_SERVICE when:
 *
 * <ul>
 *   <li>Bootstrap is in progress for any index (via {@link ElasticsearchBootstrapState}), or
 *   <li>Any of the 3 ES indices has 0 documents after bootstrap completes.
 * </ul>
 *
 * This prevents load balancers from routing traffic before Elasticsearch is fully populated.
 *
 * <p>Configured in application.yaml:
 *
 * <pre>
 * management.endpoint.health.group.readiness.include: readinessState,elasticsearchPopulation
 * </pre>
 */
@Slf4j
@Component("elasticsearchPopulation")
@RequiredArgsConstructor
public class ElasticsearchPopulationHealthIndicator implements HealthIndicator {

    private final ElasticsearchClient esClient;
    private final ElasticsearchBootstrapState bootstrapState;

    @Override
    public Health health() {
        if (bootstrapState.isInProgress()) {
            return Health.outOfService()
                    .withDetail("reason", "Elasticsearch bootstrap is in progress")
                    .build();
        }

        try {
            long jobsCount = getDocCount(INDEX_JOBS);
            long candidatesCount = getDocCount(INDEX_CANDIDATES);
            long companiesCount = getDocCount(INDEX_COMPANIES);

            Health.Builder builder =
                    new Health.Builder()
                            .withDetail("jobs_count", jobsCount)
                            .withDetail("candidates_count", candidatesCount)
                            .withDetail("companies_count", companiesCount);

            if (jobsCount == 0 || candidatesCount == 0 || companiesCount == 0) {
                return builder.outOfService()
                        .withDetail("reason", "One or more ES indices have 0 documents")
                        .build();
            }

            return builder.up().build();
        } catch (IOException e) {
            log.warn("Failed to check ES population health: {}", e.getMessage());
            return Health.down(e).build();
        }
    }

    private long getDocCount(String index) throws IOException {
        try {
            return esClient.count(c -> c.index(index)).count();
        } catch (Exception e) {
            log.debug("Index [{}] not found or not accessible: {}", index, e.getMessage());
            return 0;
        }
    }
}
