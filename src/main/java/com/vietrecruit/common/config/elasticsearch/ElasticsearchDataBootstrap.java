package com.vietrecruit.common.config.elasticsearch;

import static com.vietrecruit.common.config.elasticsearch.ElasticsearchConstants.INDEX_CANDIDATES;
import static com.vietrecruit.common.config.elasticsearch.ElasticsearchConstants.INDEX_COMPANIES;
import static com.vietrecruit.common.config.elasticsearch.ElasticsearchConstants.INDEX_JOBS;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.candidate.document.CandidateDocument;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.category.repository.CategoryRepository;
import com.vietrecruit.feature.company.document.CompanyDocument;
import com.vietrecruit.feature.company.entity.Company;
import com.vietrecruit.feature.company.repository.CompanyRepository;
import com.vietrecruit.feature.job.document.JobDocument;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.repository.JobRepository;
import com.vietrecruit.feature.job.repository.JobSpecification;
import com.vietrecruit.feature.location.repository.LocationRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import lombok.extern.slf4j.Slf4j;

/**
 * Bootstraps Elasticsearch indices from PostgreSQL data on startup. Runs after {@link
 * ElasticsearchIndexInitializer} (Order 1 vs Order 2) once all beans are ready.
 *
 * <p>Bootstrap is idempotent: if an index already has documents (count > 0), it is skipped
 * entirely. A partially-populated index (bootstrap interrupted mid-run) will also be skipped since
 * count > 0. To force a full re-bootstrap, the operator must delete the index manually and restart
 * the application. See {@code docs/elasticsearch-ops.md} for the procedure.
 */
@Slf4j
@Component
@Order(2)
public class ElasticsearchDataBootstrap implements ApplicationListener<ApplicationReadyEvent> {

    private static final int PAGE_SIZE = 500;

    private final ElasticsearchClient esClient;
    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final CompanyRepository companyRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final ElasticsearchBootstrapState bootstrapState;
    private final ThreadPoolTaskExecutor bootstrapExecutor;

    public ElasticsearchDataBootstrap(
            ElasticsearchClient esClient,
            JobRepository jobRepository,
            CandidateRepository candidateRepository,
            CompanyRepository companyRepository,
            CategoryRepository categoryRepository,
            LocationRepository locationRepository,
            ElasticsearchBootstrapState bootstrapState,
            @Qualifier("esBootstrapExecutor") ThreadPoolTaskExecutor bootstrapExecutor) {
        this.esClient = esClient;
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.companyRepository = companyRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.bootstrapState = bootstrapState;
        this.bootstrapExecutor = bootstrapExecutor;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        CompletableFuture.runAsync(
                () -> runBootstrap(INDEX_JOBS, this::bootstrapJobs), bootstrapExecutor);
        CompletableFuture.runAsync(
                () -> runBootstrap(INDEX_CANDIDATES, this::bootstrapCandidates), bootstrapExecutor);
        CompletableFuture.runAsync(
                () -> runBootstrap(INDEX_COMPANIES, this::bootstrapCompanies), bootstrapExecutor);
    }

    private void runBootstrap(String indexName, Runnable task) {
        bootstrapState.markStarted();
        try {
            task.run();
        } finally {
            bootstrapState.markCompleted();
        }
    }

    private void bootstrapJobs() {
        try {
            long esCount = esClient.count(c -> c.index(INDEX_JOBS)).count();
            if (esCount > 0) {
                log.info(
                        "Index [{}] already has {} documents, skipping bootstrap",
                        INDEX_JOBS,
                        esCount);
                return;
            }
        } catch (Exception e) {
            log.error(
                    "Failed to check document count for index [{}], aborting bootstrap: {}",
                    INDEX_JOBS,
                    e.getMessage(),
                    e);
            return;
        }

        Instant start = Instant.now();

        Map<UUID, String> companyNames =
                companyRepository.findAll().stream()
                        .collect(Collectors.toMap(Company::getId, Company::getName));
        Map<UUID, String> categoryNames =
                categoryRepository.findAll().stream()
                        .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));
        Map<UUID, String> locationNames =
                locationRepository.findAll().stream()
                        .collect(Collectors.toMap(l -> l.getId(), l -> l.getName()));

        long totalJobs = jobRepository.count(JobSpecification.isPublished());
        int totalPages = (int) Math.ceil((double) totalJobs / PAGE_SIZE);
        long totalIndexed = 0;
        List<Integer> failedPages = new ArrayList<>();

        for (int pageNum = 0; pageNum < totalPages; pageNum++) {
            try {
                Page<Job> page =
                        jobRepository.findAll(
                                JobSpecification.isPublished(), PageRequest.of(pageNum, PAGE_SIZE));
                if (!page.hasContent()) {
                    break;
                }

                final Map<UUID, String> companyNamesRef = companyNames;
                final Map<UUID, String> categoryNamesRef = categoryNames;
                final Map<UUID, String> locationNamesRef = locationNames;

                List<BulkOperation> ops =
                        page.getContent().stream()
                                .map(
                                        job ->
                                                BulkOperation.of(
                                                        b ->
                                                                b.index(
                                                                        idx ->
                                                                                idx.index(
                                                                                                INDEX_JOBS)
                                                                                        .id(
                                                                                                job.getId()
                                                                                                        .toString())
                                                                                        .document(
                                                                                                toJobDocument(
                                                                                                        job,
                                                                                                        companyNamesRef,
                                                                                                        categoryNamesRef,
                                                                                                        locationNamesRef)))))
                                .collect(Collectors.toList());

                esClient.bulk(br -> br.operations(ops));
                totalIndexed += page.getNumberOfElements();
                log.info(
                        "Bootstrapped page {} — {} documents indexed into {}",
                        pageNum,
                        totalIndexed,
                        INDEX_JOBS);
            } catch (Exception e) {
                log.error(
                        "Bootstrap failed on page {} for index [{}]: {}",
                        pageNum,
                        INDEX_JOBS,
                        e.getMessage(),
                        e);
                failedPages.add(pageNum);
            }
        }

        long durationMs = Duration.between(start, Instant.now()).toMillis();
        log.info(
                "Bootstrap complete for [{}]: {} documents indexed in {}ms",
                INDEX_JOBS,
                totalIndexed,
                durationMs);
        if (!failedPages.isEmpty()) {
            log.warn(
                    "Bootstrap for [{}] had {} failed pages: {}",
                    INDEX_JOBS,
                    failedPages.size(),
                    failedPages);
        }
    }

    private void bootstrapCandidates() {
        try {
            long esCount = esClient.count(c -> c.index(INDEX_CANDIDATES)).count();
            if (esCount > 0) {
                log.info(
                        "Index [{}] already has {} documents, skipping bootstrap",
                        INDEX_CANDIDATES,
                        esCount);
                return;
            }
        } catch (Exception e) {
            log.error(
                    "Failed to check document count for index [{}], aborting bootstrap: {}",
                    INDEX_CANDIDATES,
                    e.getMessage(),
                    e);
            return;
        }

        // Only index non-deleted candidates — soft-deleted rows must not enter ES.
        org.springframework.data.jpa.domain.Specification<Candidate> activeSpec =
                (root, query, cb) -> cb.isNull(root.get("deletedAt"));

        Instant start = Instant.now();
        long totalCandidates = candidateRepository.count(activeSpec);
        int totalPages = (int) Math.ceil((double) totalCandidates / PAGE_SIZE);
        long totalIndexed = 0;
        List<Integer> failedPages = new ArrayList<>();

        for (int pageNum = 0; pageNum < totalPages; pageNum++) {
            try {
                Page<Candidate> page =
                        candidateRepository.findAll(activeSpec, PageRequest.of(pageNum, PAGE_SIZE));
                if (!page.hasContent()) {
                    break;
                }

                List<BulkOperation> ops =
                        page.getContent().stream()
                                .map(
                                        candidate ->
                                                BulkOperation.of(
                                                        b ->
                                                                b.index(
                                                                        idx ->
                                                                                idx.index(
                                                                                                INDEX_CANDIDATES)
                                                                                        .id(
                                                                                                candidate
                                                                                                        .getId()
                                                                                                        .toString())
                                                                                        .document(
                                                                                                toCandidateDocument(
                                                                                                        candidate)))))
                                .collect(Collectors.toList());

                esClient.bulk(br -> br.operations(ops));
                totalIndexed += page.getNumberOfElements();
                log.info(
                        "Bootstrapped page {} — {} documents indexed into {}",
                        pageNum,
                        totalIndexed,
                        INDEX_CANDIDATES);
            } catch (Exception e) {
                log.error(
                        "Bootstrap failed on page {} for index [{}]: {}",
                        pageNum,
                        INDEX_CANDIDATES,
                        e.getMessage(),
                        e);
                failedPages.add(pageNum);
            }
        }

        long durationMs = Duration.between(start, Instant.now()).toMillis();
        log.info(
                "Bootstrap complete for [{}]: {} documents indexed in {}ms",
                INDEX_CANDIDATES,
                totalIndexed,
                durationMs);
        if (!failedPages.isEmpty()) {
            log.warn(
                    "Bootstrap for [{}] had {} failed pages: {}",
                    INDEX_CANDIDATES,
                    failedPages.size(),
                    failedPages);
        }
    }

    private void bootstrapCompanies() {
        try {
            long esCount = esClient.count(c -> c.index(INDEX_COMPANIES)).count();
            if (esCount > 0) {
                log.info(
                        "Index [{}] already has {} documents, skipping bootstrap",
                        INDEX_COMPANIES,
                        esCount);
                return;
            }
        } catch (Exception e) {
            log.error(
                    "Failed to check document count for index [{}], aborting bootstrap: {}",
                    INDEX_COMPANIES,
                    e.getMessage(),
                    e);
            return;
        }

        Instant start = Instant.now();
        long totalCompanies = companyRepository.count();
        int totalPages = (int) Math.ceil((double) totalCompanies / PAGE_SIZE);
        long totalIndexed = 0;
        List<Integer> failedPages = new ArrayList<>();

        for (int pageNum = 0; pageNum < totalPages; pageNum++) {
            try {
                Page<Company> page = companyRepository.findAll(PageRequest.of(pageNum, PAGE_SIZE));
                if (!page.hasContent()) {
                    break;
                }

                List<BulkOperation> ops =
                        page.getContent().stream()
                                .map(
                                        company ->
                                                BulkOperation.of(
                                                        b ->
                                                                b.index(
                                                                        idx ->
                                                                                idx.index(
                                                                                                INDEX_COMPANIES)
                                                                                        .id(
                                                                                                company.getId()
                                                                                                        .toString())
                                                                                        .document(
                                                                                                toCompanyDocument(
                                                                                                        company)))))
                                .collect(Collectors.toList());

                esClient.bulk(br -> br.operations(ops));
                totalIndexed += page.getNumberOfElements();
                log.info(
                        "Bootstrapped page {} — {} documents indexed into {}",
                        pageNum,
                        totalIndexed,
                        INDEX_COMPANIES);
            } catch (Exception e) {
                log.error(
                        "Bootstrap failed on page {} for index [{}]: {}",
                        pageNum,
                        INDEX_COMPANIES,
                        e.getMessage(),
                        e);
                failedPages.add(pageNum);
            }
        }

        long durationMs = Duration.between(start, Instant.now()).toMillis();
        log.info(
                "Bootstrap complete for [{}]: {} documents indexed in {}ms",
                INDEX_COMPANIES,
                totalIndexed,
                durationMs);
        if (!failedPages.isEmpty()) {
            log.warn(
                    "Bootstrap for [{}] had {} failed pages: {}",
                    INDEX_COMPANIES,
                    failedPages.size(),
                    failedPages);
        }
    }

    private JobDocument toJobDocument(
            Job job,
            Map<UUID, String> companyNames,
            Map<UUID, String> categoryNames,
            Map<UUID, String> locationNames) {
        return JobDocument.builder()
                .id(job.getId().toString())
                .title(job.getTitle())
                .description(job.getDescription())
                .requirements(job.getRequirements())
                .status(job.getStatus() != null ? job.getStatus().name() : null)
                .companyId(job.getCompanyId() != null ? job.getCompanyId().toString() : null)
                .companyName(
                        job.getCompanyId() != null ? companyNames.get(job.getCompanyId()) : null)
                .categoryId(job.getCategoryId() != null ? job.getCategoryId().toString() : null)
                .categoryName(
                        job.getCategoryId() != null ? categoryNames.get(job.getCategoryId()) : null)
                .locationId(job.getLocationId() != null ? job.getLocationId().toString() : null)
                .locationName(
                        job.getLocationId() != null ? locationNames.get(job.getLocationId()) : null)
                .minSalary(job.getMinSalary() != null ? job.getMinSalary().doubleValue() : null)
                .maxSalary(job.getMaxSalary() != null ? job.getMaxSalary().doubleValue() : null)
                .currency(job.getCurrency())
                .isNegotiable(job.getIsNegotiable())
                .viewCount(job.getViewCount())
                .applicationCount(job.getApplicationCount())
                .isHot(job.getIsHot())
                .isFeatured(job.getIsFeatured())
                .publishedAt(job.getPublishedAt())
                .deadline(job.getDeadline() != null ? job.getDeadline().toString() : null)
                .publicLink(job.getPublicLink())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private CandidateDocument toCandidateDocument(Candidate candidate) {
        return CandidateDocument.builder()
                .id(candidate.getId().toString())
                .userId(candidate.getUserId() != null ? candidate.getUserId().toString() : null)
                .headline(candidate.getHeadline())
                .summary(candidate.getSummary())
                .desiredPosition(candidate.getDesiredPosition())
                .desiredPositionLevel(candidate.getDesiredPositionLevel())
                .yearsOfExperience(candidate.getYearsOfExperience())
                .skills(candidate.getSkills())
                .workType(candidate.getWorkType())
                .desiredSalaryMin(candidate.getDesiredSalaryMin())
                .desiredSalaryMax(candidate.getDesiredSalaryMax())
                .educationLevel(candidate.getEducationLevel())
                .educationMajor(candidate.getEducationMajor())
                .isOpenToWork(candidate.getIsOpenToWork())
                .availableFrom(
                        candidate.getAvailableFrom() != null
                                ? candidate.getAvailableFrom().toString()
                                : null)
                .createdAt(candidate.getCreatedAt())
                .updatedAt(candidate.getUpdatedAt())
                .build();
    }

    private CompanyDocument toCompanyDocument(Company company) {
        return CompanyDocument.builder()
                .id(company.getId().toString())
                .name(company.getName())
                .domain(company.getDomain())
                .website(company.getWebsite())
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .build();
    }
}
