# VietRecruit Pre-Production Audit Report

Generated: 2026-03-27
Audited by: Claude Code Orchestrator + 9 Sub-Agents

## Executive Summary

- **Total unique findings: 68**
- **CRITICAL: 9**
- **HIGH: 20**
- **MEDIUM: 24**
- **LOW: 15**

---

## Findings by Workstream

---

### Workstream 1 — Exception & Runtime Failures

#### CRITICAL

**[C-01] `tryActivateSubscription` self-invocation bypasses `REQUIRES_NEW` — two-phase payment design broken**
- Sources: Sub-Agent 1B, 1D, 2D (deduplicated)
- File: `PaymentServiceImpl.java:278,298-312`
- `handleWebhook` calls `this.tryActivateSubscription(tx)` directly. Spring AOP proxies only intercept external calls. The `REQUIRES_NEW` annotation is silently ignored. Both the PAID status update and subscription activation run in the same transaction. If activation throws, the PAID status also rolls back — the exact failure mode the two-transaction design was built to prevent. The recovery task (`PaymentActivationRecoveryTask`) depends on PAID status surviving after activation failure — currently broken.

**[C-02] R2 upload orphan pattern across `uploadCv`, `uploadAvatar`, `uploadBanner`**
- Sources: Sub-Agent 1B, 2A
- Files: `CandidateServiceImpl.java:88-168`, `ClientUserServiceImpl.java:72-102,141-172`
- R2 upload happens inside `@Transactional` + `@Retry`. If DB save fails after R2 succeeds, the R2 object is orphaned with no cleanup. On retry, a new UUID key is generated, orphaning the previous attempt's object. In `uploadCv`, old CV is deleted before commit — if transaction rolls back, old object is permanently gone but DB still points to its URL. Three methods share this defect pattern.

**[C-06] SecurityConfig `ROLE_` prefix mismatch — admin endpoints accessible to all authenticated users**
- Source: Sub-Agent 1E (elevated from MEDIUM to CRITICAL)
- File: `SecurityConfig.java:113`
- `hasAnyAuthority("SYSTEM_ADMIN", "COMPANY_ADMIN")` but `JwtAuthenticationFilter` (line 61) adds authorities with `ROLE_` prefix: `new SimpleGrantedAuthority("ROLE_" + role)`. The authority check never matches. All `/vietrecruit/admin/**` endpoints fall through to `anyRequest().authenticated()`, granting access to any authenticated user regardless of role. This is an authorization bypass on the entire admin namespace.

#### HIGH

**[H-01] PaymentWebhookController returns 500 for signature failure — PayOS retries indefinitely**
- Sources: Sub-Agent 1A, 1D (deduplicated)
- File: `PaymentWebhookController.java:33-39`
- `catch (Exception e)` swallows `ApiException(PAYMENT_WEBHOOK_INVALID_SIGNATURE)` and returns HTTP 500. PayOS interprets 500 as transient infra error and retries the invalid webhook indefinitely.

**[H-02] Webhook idempotency guard is a TOCTOU race — double quota possible on concurrent retry**
- Source: Sub-Agent 1D
- File: `PaymentServiceImpl.java:247-268`
- `if (tx.getStatus() != PENDING)` is a read-then-write without DB-level locking. Two concurrent webhook deliveries can both pass the guard. `@Version` on `PaymentTransaction` is not checked during the status update path (no `ObjectOptimisticLockingFailureException` handling).

**[H-03] `@Retry` config for `payosPayment` defined in YAML but never applied to any method**
- Source: Sub-Agent 1D
- File: `PaymentServiceImpl.java:90`; `resilience4j-prod.yml:39-44`
- `@Retry(name = "payosPayment")` is absent from `initiateCheckout` and `reconcilePendingPayments`. A single flaky network call trips the circuit breaker without any retry.

**[H-04] `PaymentServiceImpl.initiateCheckout` — PayOS call succeeds but transaction fails, orphaned payment link**
- Source: Sub-Agent 1B
- File: `PaymentServiceImpl.java:88-198`
- If PayOS returns `checkoutUrl` but `paymentTransactionRepository.save` fails, user can follow the live PayOS link and pay, but the webhook will find no local record and silently return.

**[H-05] `KnowledgeIngestionServiceImpl.upload` — R2 upload before DB save, file orphan on failure**
- Source: Sub-Agent 1B
- File: `KnowledgeIngestionServiceImpl.java:66-105`

**[H-06] `KnowledgeIngestionServiceImpl.delete` — pgvector delete before DB delete, inconsistent state on rollback**
- Source: Sub-Agent 1B
- File: `KnowledgeIngestionServiceImpl.java:119-145`
- Execution order: R2 delete → pgvector delete → DB delete. If DB delete fails and rolls back, pgvector embeddings are already gone. Document persists in DB with status INDEXED but no backing vectors.

**[H-07] `notificationService.send` (Kafka) inside `@Transactional` across 4+ services — synchronous throw rolls back DB**
- Source: Sub-Agent 1B
- Files: `InvitationServiceImpl.java:101`, `ApplicationServiceImpl.java:105`, `AuthServiceImpl.java:163-170`, `OfferServiceImpl.java:187`
- `KafkaTemplate.send` is normally non-blocking, but a synchronous exception (producer closed/uninitialized) rolls back the entire transaction. For `AuthServiceImpl#register`, this silently rolls back user + candidate creation.

**[H-08] `ScreeningServiceImpl.triggerAsyncScoring` — `@Async` with no `@Transactional`, detached entities, partial batch state**
- Source: Sub-Agent 1B
- File: `ScreeningServiceImpl.java:97-179`
- Each `applicationRepository.save(app)` runs in its own auto-commit transaction. JVM thread interruption mid-loop leaves some applications scored, others not. Detached entity risk on the loaded batch.

**[H-09] JWT filter silently swallows all exceptions — expired/tampered tokens produce 403 instead of 401**
- Source: Sub-Agent 1E
- File: `JwtAuthenticationFilter.java:77-79`
- `catch (Exception e)` at DEBUG level → `filterChain.doFilter` continues with no authentication → `anyRequest().authenticated()` rejects with 403 (no `AuthenticationEntryPoint` configured for 401). Expired token indistinguishable from forged signature.

**[H-10] OAuth2 cookie deserialization failure throws uncaught `IllegalStateException` — 500 error**
- Source: Sub-Agent 1E
- File: `CookieUtils.java:79-86`, `HttpCookieOAuth2AuthorizationRequestRepository.java:22-25`
- Corrupted `oauth2_auth_request` cookie crashes the OAuth2 flow with unhandled 500.

**[H-11] `CacheInvalidationConsumer` — no `@RetryableTopic`, no DLT, no configured error handler**
- Source: Sub-Agent 1C
- File: `CacheInvalidationConsumer.java:39-41`
- Rethrows `DataAccessException` expecting Kafka retry, but no `@RetryableTopic` or `CommonErrorHandler` configured. Default `DefaultErrorHandler` applies 2 retries then offset commit. Permanently failing messages silently lost.

**[H-12] AI ingestion consumers — circuit breaker fallback throws `ApiException` which wastes retry slots**
- Source: Sub-Agent 1C
- Files: `CvUploadedIngestionConsumer.java:79`, `JobPublishedIngestionConsumer.java:80`
- `EmbeddingService.embedAndStoreFallback` throws `ApiException`. `@RetryableTopic` retries this 3 times while circuit is open — each attempt immediately fails from fallback. Wasted retries.

**[H-13] `AgentMemoryStore` — Redis write failure silently swallowed, `JsonProcessingException` only caught**
- Source: Sub-Agent 1C
- File: `AgentMemoryStore.java:35-40`
- `DataAccessException` from Redis SET propagates uncaught. `JsonProcessingException` is logged and swallowed — caller receives no indication memory was not persisted.

**[H-14] `AdminTransactionController` — does not extend `BaseController`, rate limiter fallback method unresolvable**
- Source: Sub-Agent 1A
- File: `AdminTransactionController.java:36`
- `@RateLimiter(fallbackMethod = "rateLimit")` references method from `BaseController` which this controller does not extend. Rate limiter fires → `FallbackExecutionException` → HTTP 500.

**[H-15] `GlobalExceptionHandler` missing handler for `MaxUploadSizeExceededException`**
- Source: Sub-Agent 1A
- File: `GlobalExceptionHandler.java`
- 4 upload endpoints affected. `ApiErrorCode.FILE_TOO_LARGE` exists but is unreachable. Returns 500 instead of 413.

**[H-16] `closeJob` — quota decrement with no optimistic lock retry, blocks job closure on contention**
- Source: Sub-Agent 1B
- File: `JobServiceImpl.java:132-148`
- `decrementActiveJobs` has no retry for `ObjectOptimisticLockingFailureException` (unlike `incrementActiveJobs`). Concurrent close operations → one rolls back → job stays PUBLISHED.

#### MEDIUM

**[M-01] `PaymentReconciliationTask` — `activateAfterPayment` joins outer `@Transactional`, constraint violation rolls back PAID status**
- Source: Sub-Agent 1D
- File: `PaymentReconciliationTask.java:36,67`

**[M-02] `tryActivateSubscription` swallows all `Exception` types — permanent errors deferred to recovery loop**
- Source: Sub-Agent 1D
- File: `PaymentServiceImpl.java:303-310`

**[M-03] HMAC-SHA512 used instead of documented RS256 — documentation/implementation mismatch**
- Source: Sub-Agent 1E
- File: `JwtService.java:35,57`

**[M-04] Password reset — no per-email rate limit, global rate limiter insufficient**
- Source: Sub-Agent 1E
- File: `AuthController.java:126`, `AuthServiceImpl.java:486-514`

**[M-05] `removeAuthorizationRequest` does not clean up cookies — OAuth2 replay window 180s**
- Source: Sub-Agent 1E
- File: `HttpCookieOAuth2AuthorizationRequestRepository.java:55-59`

**[M-06] CDC sync consumers — `IOException` wrapped in `RuntimeException` retries permanent ES errors unnecessarily**
- Source: Sub-Agent 1C
- Files: `JobSyncConsumer.java:106-109`, `CandidateSyncConsumer.java:66-69`, `CompanySyncConsumer.java:66-69`

**[M-07] `CacheInvalidationConsumer` — non-`DataAccessException` from Redis SCAN silently swallowed mid-operation**
- Source: Sub-Agent 1C
- File: `CacheInvalidationConsumer.java:127-145`

**[M-08] `KafkaConfig` standard factory has no `CommonErrorHandler` — invisible default retry behavior**
- Source: Sub-Agent 1C
- File: `KafkaConfig.java:79-84`

**[M-09] `AuthServiceImpl.register` — Redis OTP stored before Kafka send; rollback leaves orphaned OTP key**
- Source: Sub-Agent 1B
- File: `AuthServiceImpl.java:153-172`

**[M-10] `AuthServiceImpl.resetPassword` — Redis token deleted before DB rollback possible**
- Source: Sub-Agent 1B
- File: `AuthServiceImpl.java:518-544`

**[M-11] `ApplicationServiceImpl.insertHistory` — public method with no `@Transactional`, safe only by caller context**
- Source: Sub-Agent 1B
- File: `ApplicationServiceImpl.java:243-261`

**[M-12] `CompanyServiceImpl` missing class-level `@Transactional(readOnly = true)` default**
- Source: Sub-Agent 1B
- File: `CompanyServiceImpl.java:23-88`

**[M-13] `IllegalStateException` from `SecurityUtils` mapped to 400 — leaks internal principal value**
- Source: Sub-Agent 1A
- File: `GlobalExceptionHandler.java:66-68`, `SecurityUtils.java:22`

**[M-14] `InterviewQuestionServiceImpl.parseQuestionsJson` throws raw `RuntimeException`**
- Source: Sub-Agent 1A
- File: `InterviewQuestionServiceImpl.java:346-353`

**[M-15] `WebhookVerificationException` not registered in `GlobalExceptionHandler`**
- Source: Sub-Agent 1A
- File: `GlobalExceptionHandler.java`

#### LOW

**[L-01] `PaymentExpiryTask` — PayOS cancel call inside `@Transactional` loop, no circuit breaker**
- Source: Sub-Agent 1D
- File: `PaymentExpiryTask.java:31-64`

**[L-02] `payosPayment` retry config missing `retryExceptions`/`ignoreExceptions`**
- Source: Sub-Agent 1D
- File: `resilience4j-prod.yml:39-44`

**[L-03] `JwtService` — no startup validation of HS512 key length**
- Source: Sub-Agent 1E
- File: `JwtService.java:35-36`

**[L-04] GitHub OAuth2 email fallback — dead code, identical `getAttribute("email")` call**
- Source: Sub-Agent 1E
- File: `OAuth2AuthenticationSuccessHandler.java:74-79`

**[L-05] `EmailConsumer.handleDlt` — no null guard on `record.value()`**
- Source: Sub-Agent 1C
- File: `EmailConsumer.java:57-68`

**[L-06] `JobSyncConsumer` Caffeine cache — null on DB miss not cached, masks `DataAccessException`**
- Source: Sub-Agent 1C
- File: `JobSyncConsumer.java:185-198`

**[L-07] `GlobalExceptionHandler.handleApiException` — no logging for 5xx-mapped errors**
- Source: Sub-Agent 1A
- File: `GlobalExceptionHandler.java:29-31`

**[L-08] `AdminTransactionController` response wrapper inconsistency (raw `Page` vs `PageResponse`)**
- Source: Sub-Agent 1A

**[L-09] Stub controllers (`MatchingController`, `ScreeningController`) registered with no endpoints**
- Source: Sub-Agent 1A

---

### Workstream 2 — User Flow & Business Logic

#### CRITICAL

**[C-03] Quota validate+increment not atomic — concurrent publish can exceed plan limit**
- Sources: Sub-Agent 1D, 2B (deduplicated)
- Files: `JobServiceImpl.java:88-89`, `QuotaGuard.java:32-56`
- `validateCanPublishJob` reads quota → `incrementActiveJobs` reads again and saves. Between reads, concurrent thread passes validation. Optimistic lock catch exists but retry does not re-validate capacity. At READ COMMITTED isolation, stale reads are possible.

**[C-04] `CvUploadedEvent` published before transaction commits**
- Source: Sub-Agent 2A
- File: `CandidateServiceImpl.java:128-152`
- Kafka event published inside `@Transactional` before commit. If transaction rolls back, the event is already on the topic. AI ingestion consumer will process a non-existent or stale CV record.

**[C-05] Recovery query `findPaidWithoutActiveSubscription` picks up all historical PAID records**
- Source: Sub-Agent 2D
- File: `PaymentTransactionRepository.java:44-55`
- Historical PAID transactions from expired subscriptions are picked up every 5-minute cycle, triggering `activateSubscription` which creates new subscriptions — effectively granting free re-activations.

**[C-07] Duplicate application — no DB unique constraint on `(job_id, candidate_id)`**
- Source: Sub-Agent 2A
- File: `ApplicationServiceImpl.java:88`
- `existsByJobIdAndCandidateId` read-then-write race. Also does not filter `deleted_at IS NULL`.

**[C-08] Scorecard can be submitted against a SCHEDULED interview**
- Source: Sub-Agent 2C
- File: `ScorecardServiceImpl.java:107-111`
- Guard only rejects CANCELED. Interviewer can submit evaluations before the interview occurs.

**[C-09] Offer acceptance — offer saved as ACCEPTED but application status update can fail, leaving entities desynchronized**
- Source: Sub-Agent 2C
- File: `OfferServiceImpl.java:230-243`
- Offer entity saved, then Application entity save can throw `ObjectOptimisticLockingFailureException`. Offer is ACCEPTED, application still OFFER.

#### HIGH

**[H-17] INTERVIEW → OFFER transition allowed without verifying a COMPLETED interview exists**
- Source: Sub-Agent 2C
- Files: `ApplicationServiceImpl.java:56-63`, `OfferServiceImpl.java:60-68`
- `ALLOWED_TRANSITIONS` permits the transition. Neither `updateStatus` nor `createOffer` check for a completed interview record.

**[H-18] Skill filter applied post-pagination — inconsistent page sizes**
- Source: Sub-Agent 2A
- File: `CandidateServiceImpl.java:282-301`
- Java stream filter after DB pagination. If 80% fail skill filter, response contains far fewer entries than requested.

**[H-19] AI interview question authorization — INTERVIEWER role not company-scoped**
- Source: Sub-Agent 2B
- File: `InterviewQuestionController.java:40,57`
- `@PreAuthorize` permits any INTERVIEWER across all companies. Service-level `companyId` check may mitigate — needs verification.

**[H-20] Order code collision risk — only 90 possible values per millisecond**
- Source: Sub-Agent 2D
- File: `PaymentServiceImpl.java:135-138`
- `System.currentTimeMillis() + ThreadLocalRandom.nextInt(10, 100)`. Under concurrent load, collisions are non-trivial.

#### MEDIUM

**[M-16] CV upload trusts client-supplied Content-Type — no byte-level MIME detection**
- Source: Sub-Agent 2A
- File: `CandidateServiceImpl.java:92`

**[M-17] AI recommendation returns empty list silently when CV not yet ingested**
- Source: Sub-Agent 2A
- File: `RecommendationServiceImpl.java:99-101`

**[M-18] `CV_NOT_PARSED` error indistinguishable from "parsing in progress"**
- Source: Sub-Agent 2A
- File: `CvImprovementServiceImpl.java:82`

**[M-19] Department association not enforced on job creation/publish**
- Source: Sub-Agent 2B

**[M-20] Scorecard duplicate race — DB unique constraint on `(interview_id, interviewer_id)` must be verified in Flyway**
- Source: Sub-Agent 2C

**[M-21] Offer decline does not send rejection notification to candidate**
- Source: Sub-Agent 2C
- File: `OfferServiceImpl.java:238`

**[M-22] Active plan replacement blocked — no upgrade/downgrade path exists**
- Source: Sub-Agent 2D
- File: `PaymentServiceImpl.java:99-104`

**[M-23] Free plan can be re-activated after cancellation**
- Source: Sub-Agent 2D
- File: `PaymentServiceImpl.java:115-118`

---

### Workstream 3 — Custom Queries

#### CRITICAL

**[C-09b] `UserRepository.findByIdWithRolesAndPermissions` — dual collection JOIN FETCH creates Cartesian product**
- Source: Sub-Agent 3A
- File: `UserRepository.java:20-25`
- Two `LEFT JOIN FETCH` across `roles` and `roles.permissions` produces Cartesian product. For 3 roles x 10 permissions = 30 rows transmitted. Called in 4 locations: login, refresh, getProfile, admin get. No `DISTINCT`. Violates Hibernate HHH90003004 warning.

#### HIGH

**[H-21] `RefreshTokenRepository.revokeAllByUserId` — `@Modifying` without `@Transactional` or `clearAutomatically = true`**
- Source: Sub-Agent 3A
- File: `RefreshTokenRepository.java:19-22`
- Bulk JPQL UPDATE bypasses first-level cache. No `clearAutomatically = true`. Stale `RefreshToken` entities in same persistence context.

**[H-22] N+1 on `Interview.interviewers` collection in list query**
- Source: Sub-Agent 3A
- File: `InterviewRepository.java:19`
- `findByApplicationIdAndDeletedAtIsNull` returns entities with lazy `@ManyToMany` `interviewers`. Mapper accesses the collection → N+1 query per interview.

**[H-23] ES SearchServiceImpl — `TransportException` (non-IOException) bypasses catch block, unlogged**
- Source: Sub-Agent 3B
- Files: `JobSearchServiceImpl.java:90`, `CandidateSearchServiceImpl.java:87`, `CompanySearchServiceImpl.java:64`
- ES HTTP 429/503/500 throws `TransportException` (extends `RuntimeException`), not `IOException`. Bypasses catch block entirely.

**[H-24] `JobSearchServiceImpl` — `sort` parameter accepted in DTO but never applied to query**
- Source: Sub-Agent 3B
- File: `JobSearchServiceImpl.java`, `JobSearchRequest.java:36`

**[H-25] `CandidateSearchServiceImpl` — `RECENCY_SCRIPT` no null guard on `doc['updated_at']`**
- Source: Sub-Agent 3B
- File: `CandidateSearchServiceImpl.java:43-44`
- Unlike `PUBLISHED_AT_DECAY_SCRIPT` in `JobSearchServiceImpl` which correctly checks `containsKey`/`.empty`.

**[H-26] `EmbeddingService.search` — unbounded caller-controlled `topK`, `deleteByMetadata` hardcoded topK=100 leaves orphans**
- Source: Sub-Agent 3C
- File: `EmbeddingService.java:56-75,81`

**[H-27] `AgentService.execute` — NPE on null `chatResponse.getResult().getOutput().getText()`**
- Source: Sub-Agent 3C
- File: `AgentService.java:77`
- `RagService` and `SalaryBenchmarkServiceImpl` perform null checks. `AgentService` does not.

**[H-28] `AgentMemoryStore.append` — GET-mutate-SET race condition, non-atomic cap enforcement**
- Source: Sub-Agent 3C
- File: `AgentMemoryStore.java:28-41`
- Under concurrent virtual thread requests, two `append` calls can overwrite each other's writes, losing messages. Architecture spec calls for LTRIM (atomic), implementation uses read-modify-write.

#### MEDIUM

**[M-24] `PaymentTransactionRepository.findByCompanyIdAndStatus` returns `Optional` on non-unique compound**
- Source: Sub-Agent 3A
- File: `PaymentTransactionRepository.java:21`

**[M-25] `JobSpecification` factory methods — no null guard on nullable UUID parameters**
- Source: Sub-Agent 3A
- File: `JobSpecification.java`

**[M-26] `ApplicationRepository.findByUserId` — non-association JPQL JOIN, unidiomatic**
- Source: Sub-Agent 3A
- File: `ApplicationRepository.java:42-45`

**[M-27] `CompanySearchServiceImpl` — `domain` is `keyword` field queried via `multiMatch`**
- Source: Sub-Agent 3B
- File: `CompanySearchServiceImpl.java:81`

**[M-28] `ElasticsearchIndexInitializer` — no startup failure on index creation error**
- Source: Sub-Agent 3B
- File: `ElasticsearchIndexInitializer.java:62-68,97-99`

**[M-29] UUID format assumption between search filter and CDC serialization unvalidated**
- Source: Sub-Agent 3B
- File: `JobSearchServiceImpl.java:247-262`

**[M-30] `RagService` — no token budget guard before LLM call, context window overflow possible**
- Source: Sub-Agent 3C
- File: `RagService.java:47-68`

**[M-31] Embedding cache key — whitespace not normalized before SHA-256**
- Source: Sub-Agent 3C
- File: `EmbeddingService.java:90-103,112-120`

**[M-32] `RecommendationService` — empty stub, no fallback design for pgvector unavailability**
- Source: Sub-Agent 3C

#### LOW

**[L-10] `ElasticsearchIndexInitializer` — `icu_tokenizer` configured with empty `ruleFiles("")`**
- Source: Sub-Agent 3B

**[L-11] `JobSearchServiceImpl.autocomplete` — redundant dedup (`skipDuplicates` + `.distinct()`)**
- Source: Sub-Agent 3B

**[L-12] `JobRepository.getSalaryBenchmarkByText` — unbounded LIKE pattern, no length limit**
- Source: Sub-Agent 3A

**[L-13] `TransactionRecordRepository.existsByOrderCode` — race without DB unique constraint**
- Source: Sub-Agent 3A

**[L-14] `InterviewRepository.findByInterviewerUserId` — dead code, no call site**
- Source: Sub-Agent 3A

**[L-15] `AgentService` Resilience4j annotation ordering — implicit aspect precedence dependency**
- Source: Sub-Agent 3C

---

## Cross-Cutting Issues

### XC-01: Kafka events published inside `@Transactional` (pre-commit)
Affects: `CandidateServiceImpl.uploadCv` (C-04), `InvitationServiceImpl.create`, `ApplicationServiceImpl.apply`, `AuthServiceImpl.register`, `OfferServiceImpl.sendOffer` (H-07), `KnowledgeIngestionServiceImpl.upload` (H-05).
Root cause: No `@TransactionalEventListener(AFTER_COMMIT)` pattern adopted. Events can be consumed before the transaction commits, or events can fire for rolled-back transactions.

### XC-02: R2/external-system writes inside `@Transactional` with no compensation
Affects: `CandidateServiceImpl.uploadCv` (C-02), `ClientUserServiceImpl.uploadAvatar/Banner` (C-02), `KnowledgeIngestionServiceImpl.upload/delete` (H-05, H-06), `PaymentServiceImpl.initiateCheckout` (H-04).
Root cause: External I/O (R2, PayOS) executed inside DB transactions. Rollback does not undo external writes.

### XC-03: Self-invocation AOP proxy bypass
Affects: `PaymentServiceImpl.tryActivateSubscription` (C-01).
Root cause: `this.method()` calls bypass Spring proxy. Any `@Transactional`, `@Async`, `@CircuitBreaker` annotation on the self-invoked method is silently ignored.

### XC-04: Optimistic locking without retry on decrement paths
Affects: `QuotaGuard.decrementActiveJobs` (H-16), `SubscriptionServiceImpl.cancelSubscription` (L-LOW).
Root cause: `@Version` exists on entities but only `incrementActiveJobs` has retry logic. Decrement paths have no retry, causing rollback on contention.

---

## Files With No Findings

- `common/exception/ApiException.java`
- `feature/auth/controller/AuthController.java`
- `feature/auth/controller/OAuth2ExchangeController.java`
- `feature/company/controller/CompanyController.java`
- `feature/application/controller/ApplicationController.java`
- `feature/application/controller/InterviewController.java`
- `feature/application/controller/OfferController.java`
- `feature/candidate/controller/CandidateController.java`
- `feature/job/controller/JobController.java`
- `feature/user/controller/AdminUserController.java`
- `feature/user/controller/ClientUserController.java`
- `feature/subscription/controller/PlanController.java`
- `feature/subscription/controller/SubscriptionController.java`
- `feature/category/controller/CategoryController.java`
- `feature/location/controller/LocationController.java`
- `feature/department/controller/DepartmentController.java`
- `feature/invitation/controller/InvitationController.java`
- `feature/payment/controller/PaymentController.java`
- `feature/payment/controller/TransactionHistoryController.java`
- `feature/ai/cv/controller/CvImprovementController.java`
- `feature/ai/jd/controller/JdController.java`
- `feature/ai/salary/controller/SalaryBenchmarkController.java`
- `feature/ai/cv/service/impl/CvImprovementServiceImpl.java` (no DB writes)
- `feature/ai/jd/service/impl/JdGeneratorServiceImpl.java` (no direct DB writes)
- `feature/ai/salary/service/impl/SalaryBenchmarkServiceImpl.java` (read-only)
- `feature/notification/service/impl/NotificationServiceImpl.java` (Kafka only)
- `feature/payment/service/impl/TransactionHistoryServiceImpl.java` (read-only)
- `feature/subscription/service/impl/PlanServiceImpl.java` (read-only)
- `feature/candidate/service/impl/CandidateSearchServiceImpl.java` (ES read-only)
- `feature/company/service/impl/CompanySearchServiceImpl.java` (ES read-only)
- `feature/job/service/impl/JobSearchServiceImpl.java` (ES read-only)
- `common/config/elasticsearch/ElasticsearchConstants.java`
