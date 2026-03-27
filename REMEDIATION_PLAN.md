# VietRecruit Remediation Plan

Generated: 2026-03-27
Source: AUDIT_REPORT.md (68 findings)

---

## Priority Order: CRITICAL (9) -> HIGH (20) -> MEDIUM (24) -> LOW (15)

---

## CRITICAL

### TASK-001 — SecurityConfig ROLE_ prefix mismatch (Authorization Bypass)
- **Source:** C-06
- **File(s):** `src/main/java/com/vietrecruit/common/config/security/SecurityConfig.java:113`
- **Problem:** `hasAnyAuthority("SYSTEM_ADMIN", "COMPANY_ADMIN")` never matches because `JwtAuthenticationFilter` stores authorities with `ROLE_` prefix. All admin endpoints accessible to any authenticated user.
- **Fix:** Change to `hasAnyRole("SYSTEM_ADMIN", "COMPANY_ADMIN")` (which auto-prepends `ROLE_`) OR change to `hasAnyAuthority("ROLE_SYSTEM_ADMIN", "ROLE_COMPANY_ADMIN")`. Verify all `@PreAuthorize` annotations across controllers for the same mismatch.
- **Verification:** Unit test: create `Authentication` with `ROLE_CANDIDATE` authority, assert 403 on admin endpoints. Create with `ROLE_SYSTEM_ADMIN`, assert 200.

### TASK-002 — PaymentServiceImpl self-invocation bypasses REQUIRES_NEW
- **Source:** C-01
- **File(s):** `src/main/java/com/vietrecruit/feature/payment/service/impl/PaymentServiceImpl.java:278,298-312`
- **Problem:** `this.tryActivateSubscription(tx)` bypasses Spring AOP proxy. `@Transactional(REQUIRES_NEW)` is silently ignored. PAID status and subscription activation share the same transaction. Recovery task is broken.
- **Fix:** Inject `PaymentServiceImpl` via `@Lazy` self-reference or extract `tryActivateSubscription` into a separate `@Service` class (e.g., `SubscriptionActivationService`). Call through the injected bean so the proxy intercepts.
- **Verification:** Integration test: mock activation to throw after PAID status set. Assert PAID status persists in DB. Assert recovery task picks it up.

### TASK-003 — Duplicate application race (no DB unique constraint)
- **Source:** C-07
- **File(s):** `src/main/java/com/vietrecruit/feature/application/service/impl/ApplicationServiceImpl.java:88`, Flyway migration
- **Problem:** `existsByJobIdAndCandidateId` is a read-then-write race. No DB unique constraint on `(job_id, candidate_id)`. Also does not filter `deleted_at IS NULL`.
- **Fix:** Add Flyway migration: `CREATE UNIQUE INDEX uq_application_job_candidate ON applications(job_id, candidate_id) WHERE deleted_at IS NULL`. Catch `DataIntegrityViolationException` in service layer and throw `ApiException(APPLICATION_ALREADY_EXISTS)`.
- **Verification:** Concurrent test: two threads submit same (job_id, candidate_id) simultaneously. Assert exactly one succeeds, the other gets APPLICATION_ALREADY_EXISTS.

### TASK-004 — Quota validate+increment not atomic
- **Source:** C-03
- **File(s):** `src/main/java/com/vietrecruit/feature/job/service/impl/JobServiceImpl.java:88-89`, `src/main/java/com/vietrecruit/feature/subscription/service/impl/QuotaGuard.java:32-56`
- **Problem:** `validateCanPublishJob` reads quota, `incrementActiveJobs` reads again and saves. Concurrent publish can exceed plan limit. Optimistic lock retry does not re-validate capacity.
- **Fix:** Merge validate+increment into a single atomic method with `@Retryable` that re-reads and re-validates on `ObjectOptimisticLockingFailureException`. Alternatively, use `UPDATE ... SET active_jobs = active_jobs + 1 WHERE active_jobs < max_jobs RETURNING active_jobs` as a single SQL statement.
- **Verification:** Concurrent test: subscription with 1 remaining slot, 5 threads call publish simultaneously. Assert exactly 1 succeeds.

### TASK-005 — CvUploadedEvent published before transaction commits
- **Source:** C-04
- **File(s):** `src/main/java/com/vietrecruit/feature/candidate/service/impl/CandidateServiceImpl.java:128-152`
- **Problem:** Kafka event published inside `@Transactional` before commit. Rollback leaves orphan event on topic.
- **Fix:** Replace `applicationEventPublisher.publishEvent(new CvUploadedEvent(...))` with `@TransactionalEventListener(phase = AFTER_COMMIT)` pattern. Or use Spring's `TransactionSynchronization.afterCommit()` callback. Apply same pattern to all XC-01 affected locations.
- **Verification:** Unit test: mock DB save to throw after event publish point. Assert no Kafka message sent.

### TASK-006 — Recovery query picks up historical PAID records
- **Source:** C-05
- **File(s):** `src/main/java/com/vietrecruit/feature/payment/repository/PaymentTransactionRepository.java:44-55`
- **Problem:** `findPaidWithoutActiveSubscription` returns all historical PAID transactions, not just recent ones. Recovery task creates new subscriptions for long-expired payments.
- **Fix:** Add time-bound filter: `AND t.updatedAt > :cutoff` where cutoff is e.g., 24 hours ago. Or add a boolean `activation_attempted` column and filter by it.
- **Verification:** Insert a PAID transaction from 30 days ago with no active subscription. Assert recovery query does NOT return it. Insert a PAID transaction from 5 minutes ago. Assert recovery query returns it.

### TASK-007 — Scorecard submitted against SCHEDULED interview
- **Source:** C-08
- **File(s):** `src/main/java/com/vietrecruit/feature/application/service/impl/ScorecardServiceImpl.java:107-111`
- **Problem:** Guard only rejects CANCELED interviews. SCHEDULED interviews accept scorecards before they occur.
- **Fix:** Change guard to `if (interview.getStatus() != InterviewStatus.COMPLETED)` — reject all non-COMPLETED statuses.
- **Verification:** Unit test: create interview with SCHEDULED status. Assert scorecard submission throws ApiException.

### TASK-008 — Offer acceptance desynchronization
- **Source:** C-09
- **File(s):** `src/main/java/com/vietrecruit/feature/application/service/impl/OfferServiceImpl.java:230-243`
- **Problem:** Offer saved as ACCEPTED, then application status update can fail with `ObjectOptimisticLockingFailureException`. Entities desynchronized.
- **Fix:** Wrap both saves in same `@Transactional`. If already in one, ensure the application save uses `@Version` retry with the offer save rolled back on failure. Both entities must commit atomically.
- **Verification:** Mock application save to throw `ObjectOptimisticLockingFailureException`. Assert offer is NOT saved as ACCEPTED.

### TASK-009 — R2 upload orphan pattern
- **Source:** C-02
- **File(s):** `src/main/java/com/vietrecruit/feature/candidate/service/impl/CandidateServiceImpl.java:88-168`, `src/main/java/com/vietrecruit/feature/user/service/impl/ClientUserServiceImpl.java:72-102,141-172`
- **Problem:** R2 upload inside `@Transactional` + `@Retry`. DB failure orphans R2 objects. Retry generates new UUID keys, orphaning previous attempts. Old CV deleted before commit — rollback loses it permanently.
- **Fix:** Move R2 upload outside transaction. Pattern: (1) Upload to R2, (2) Open transaction, save DB, commit, (3) On DB failure, compensate by deleting R2 object. For old CV deletion, delete AFTER commit succeeds using `TransactionSynchronization.afterCommit()`.
- **Verification:** Mock DB save to throw. Assert R2 delete compensation called. Assert old CV not deleted on failure.

### TASK-010 — UserRepository dual JOIN FETCH Cartesian product
- **Source:** C-09b
- **File(s):** `src/main/java/com/vietrecruit/feature/user/repository/UserRepository.java:20-25`
- **Problem:** Two `LEFT JOIN FETCH` across `roles` and `roles.permissions` produces Cartesian product. No `DISTINCT`. Called on every login, refresh, profile fetch.
- **Fix:** Split into two queries: (1) Fetch user with roles, (2) Fetch permissions for those roles. Or use `@EntityGraph` with subgraph. Add `DISTINCT` as minimum fix if keeping single query.
- **Verification:** Enable Hibernate SQL logging. Assert single query with DISTINCT or two queries. Assert no Cartesian explosion in result set.

---

## HIGH

### TASK-011 — PaymentWebhookController returns 500 for signature failure
- **Source:** H-01
- **File(s):** `src/main/java/com/vietrecruit/feature/payment/controller/PaymentWebhookController.java:33-39`
- **Problem:** `catch(Exception)` returns 500 for invalid signatures. PayOS retries indefinitely.
- **Fix:** Catch `ApiException` with `PAYMENT_WEBHOOK_INVALID_SIGNATURE` code separately, return HTTP 200 with error body (or 400). Only return 500 for genuine infrastructure errors.
- **Verification:** Send webhook with invalid signature. Assert HTTP 200/400 returned, not 500.

### TASK-012 — Webhook idempotency TOCTOU race
- **Source:** H-02
- **File(s):** `src/main/java/com/vietrecruit/feature/payment/service/impl/PaymentServiceImpl.java:247-268`
- **Problem:** `if (tx.getStatus() != PENDING)` read-then-write race. No DB locking.
- **Fix:** Use `SELECT ... FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)` on the repository query. Or use `@Version` with `ObjectOptimisticLockingFailureException` catch + retry. Or use `UPDATE ... WHERE status = 'PENDING'` returning affected rows.
- **Verification:** Concurrent test: two threads process same orderCode. Assert exactly one activates subscription.

### TASK-013 — Missing @Retry on PayOS calls
- **Source:** H-03
- **File(s):** `src/main/java/com/vietrecruit/feature/payment/service/impl/PaymentServiceImpl.java:90`, `src/main/resources/resilience/resilience4j-prod.yml:39-44`
- **Problem:** YAML defines `payosPayment` retry config but no method annotates `@Retry(name = "payosPayment")`.
- **Fix:** Add `@Retry(name = "payosPayment")` to `initiateCheckout` and `reconcilePendingPayments` methods.
- **Verification:** Mock PayOS to throw `IOException` once then succeed. Assert method succeeds after retry.

### TASK-014 — PayOS call succeeds but DB save fails (orphaned payment link)
- **Source:** H-04
- **File(s):** `src/main/java/com/vietrecruit/feature/payment/service/impl/PaymentServiceImpl.java:88-198`
- **Problem:** PayOS returns checkoutUrl but `paymentTransactionRepository.save` fails. User can follow live link and pay, but webhook finds no local record.
- **Fix:** Save `PaymentTransaction` with PENDING status BEFORE calling PayOS. Use the saved record's ID as the orderCode. PayOS call failure rolls back the saved record.
- **Verification:** Mock DB save to throw after PayOS call. Assert no orphaned payment link (or compensation call to cancel PayOS link).

### TASK-015 — KnowledgeIngestionServiceImpl R2 upload before DB save
- **Source:** H-05
- **File(s):** `src/main/java/com/vietrecruit/feature/ai/knowledge/service/impl/KnowledgeIngestionServiceImpl.java:66-105`
- **Problem:** R2 upload before DB save. File orphaned on DB failure.
- **Fix:** Same pattern as TASK-009: upload R2 outside transaction, compensate on failure.
- **Verification:** Mock DB save to throw. Assert R2 delete compensation called.

### TASK-016 — KnowledgeIngestionServiceImpl delete ordering
- **Source:** H-06
- **File(s):** `src/main/java/com/vietrecruit/feature/ai/knowledge/service/impl/KnowledgeIngestionServiceImpl.java:119-145`
- **Problem:** R2 delete -> pgvector delete -> DB delete. If DB delete fails, vectors already gone.
- **Fix:** Reverse order: DB delete (soft or hard) first within transaction, then pgvector delete, then R2 delete in `afterCommit`. Or use soft-delete flag first, then clean up external resources.
- **Verification:** Mock DB delete to throw. Assert pgvector and R2 resources still intact.

### TASK-017 — Kafka send inside @Transactional across 4+ services
- **Source:** H-07
- **File(s):** `InvitationServiceImpl.java:101`, `ApplicationServiceImpl.java:105`, `AuthServiceImpl.java:163-170`, `OfferServiceImpl.java:187`
- **Problem:** `KafkaTemplate.send` synchronous exception rolls back entire DB transaction.
- **Fix:** Move Kafka sends to `@TransactionalEventListener(phase = AFTER_COMMIT)`. Same fix as XC-01.
- **Verification:** Mock KafkaTemplate to throw. Assert DB transaction commits successfully.

### TASK-018 — ScreeningServiceImpl async detached entity + partial batch
- **Source:** H-08
- **File(s):** `src/main/java/com/vietrecruit/feature/ai/screening/service/impl/ScreeningServiceImpl.java:97-179`
- **Problem:** `@Async` with no `@Transactional`. Each save is auto-commit. Thread interruption leaves partial state.
- **Fix:** Wrap entire batch in `@Transactional`. Add error handling for partial failures: either all succeed or all fail. Re-attach detached entities via `merge()` or re-fetch by ID.
- **Verification:** Mock AI scoring to fail on 3rd of 5 applications. Assert either all 5 scored or none.

### TASK-019 — JWT filter swallows exceptions (403 instead of 401)
- **Source:** H-09
- **File(s):** `src/main/java/com/vietrecruit/common/security/JwtAuthenticationFilter.java:77-79`
- **Problem:** `catch(Exception)` at DEBUG level, continues filter chain with no auth. Returns 403 instead of 401.
- **Fix:** In catch block, set response status to 401 and return (do not continue filter chain). Or configure `AuthenticationEntryPoint` in SecurityConfig to return 401. Log at WARN level with token failure reason.
- **Verification:** Send request with expired JWT. Assert HTTP 401 returned, not 403.

### TASK-020 — OAuth2 cookie deserialization throws 500
- **Source:** H-10
- **File(s):** `src/main/java/com/vietrecruit/common/security/CookieUtils.java:79-86`, `HttpCookieOAuth2AuthorizationRequestRepository.java:22-25`
- **Problem:** Corrupted `oauth2_auth_request` cookie causes unhandled `IllegalStateException`.
- **Fix:** Wrap deserialization in try-catch. On failure, delete the corrupted cookie and redirect to login. Log at WARN.
- **Verification:** Send request with malformed oauth2_auth_request cookie. Assert redirect to login, not 500.

### TASK-021 — CacheInvalidationConsumer no retry/DLT
- **Source:** H-11
- **File(s):** `src/main/java/com/vietrecruit/common/config/cache/CacheInvalidationConsumer.java:39-41`
- **Problem:** No `@RetryableTopic`, no DLT. Permanently failing messages silently lost after default 2 retries.
- **Fix:** Add `@RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2))` with DLT handler. Or configure explicit `CommonErrorHandler` with `DeadLetterPublishingRecoverer`.
- **Verification:** Mock Redis SCAN to throw permanently. Assert message lands in DLT after retries exhausted.

### TASK-022 — AI ingestion consumer fallback wastes retry slots
- **Source:** H-12
- **File(s):** `src/main/java/com/vietrecruit/feature/ai/ingestion/consumer/CvUploadedIngestionConsumer.java:79`, `JobPublishedIngestionConsumer.java:80`
- **Problem:** Circuit breaker fallback throws `ApiException`. `@RetryableTopic` retries while circuit is open — each retry immediately fails from fallback.
- **Fix:** Fallback should throw a non-retryable exception (implement `ExceptionClassifier` or use `@RetryableTopic(exclude = ApiException.class)`). Or fallback should return gracefully and let the DLT handler process it.
- **Verification:** Open circuit breaker. Send event. Assert max 1 attempt, not 3 wasted retries.

### TASK-023 — AgentMemoryStore Redis write failure propagation
- **Source:** H-13
- **File(s):** `src/main/java/com/vietrecruit/feature/ai/shared/memory/AgentMemoryStore.java:35-40`
- **Problem:** `DataAccessException` from Redis SET propagates uncaught. `JsonProcessingException` logged but caller unaware.
- **Fix:** Catch `DataAccessException`, log at ERROR, throw domain-specific exception or return failure indicator. Catch both exception types consistently.
- **Verification:** Mock Redis SET to throw `DataAccessException`. Assert proper error handling, no uncaught propagation.

### TASK-024 — AdminTransactionController missing BaseController
- **Source:** H-14
- **File(s):** `src/main/java/com/vietrecruit/feature/payment/controller/AdminTransactionController.java:36`
- **Problem:** Does not extend `BaseController`. `@RateLimiter(fallbackMethod = "rateLimit")` references unresolvable method.
- **Fix:** Extend `BaseController`. Or define local `rateLimit` fallback method matching the signature.
- **Verification:** Trigger rate limiter. Assert proper fallback response, not 500.

### TASK-025 — Missing MaxUploadSizeExceededException handler
- **Source:** H-15
- **File(s):** `src/main/java/com/vietrecruit/common/exception/GlobalExceptionHandler.java`
- **Problem:** 4 upload endpoints return 500 instead of 413. `ApiErrorCode.FILE_TOO_LARGE` exists but unreachable.
- **Fix:** Add `@ExceptionHandler(MaxUploadSizeExceededException.class)` method returning `ApiResponse` with `FILE_TOO_LARGE` code and HTTP 413.
- **Verification:** Upload file exceeding `spring.servlet.multipart.max-file-size`. Assert HTTP 413 with FILE_TOO_LARGE code.

### TASK-026 — closeJob quota decrement no retry
- **Source:** H-16
- **File(s):** `src/main/java/com/vietrecruit/feature/job/service/impl/JobServiceImpl.java:132-148`
- **Problem:** `decrementActiveJobs` has no retry for `ObjectOptimisticLockingFailureException`. Concurrent close operations fail.
- **Fix:** Add `@Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3)` or manual retry loop matching `incrementActiveJobs` pattern.
- **Verification:** Two threads close jobs concurrently on same subscription. Assert both succeed.

### TASK-027 — INTERVIEW->OFFER without COMPLETED interview check
- **Source:** H-17
- **File(s):** `src/main/java/com/vietrecruit/feature/application/service/impl/ApplicationServiceImpl.java:56-63`, `OfferServiceImpl.java:60-68`
- **Problem:** No verification that a COMPLETED interview exists before creating an offer.
- **Fix:** In `createOffer`, add check: `interviewRepository.existsByApplicationIdAndStatus(applicationId, COMPLETED)`. Throw if false.
- **Verification:** Create application in INTERVIEW status with no COMPLETED interview. Assert offer creation fails.

### TASK-028 — Skill filter applied post-pagination
- **Source:** H-18
- **File(s):** `src/main/java/com/vietrecruit/feature/candidate/service/impl/CandidateServiceImpl.java:282-301`
- **Problem:** Java stream filter after DB pagination. Pages return fewer items than requested.
- **Fix:** Move skill filtering into the SQL/JPQL query (JOIN on skills table with IN clause). Or use Specification with skills predicate.
- **Verification:** Request page size 10. Assert response contains exactly 10 items (or total remaining if less).

### TASK-029 — AI interview question cross-company access
- **Source:** H-19
- **File(s):** `src/main/java/com/vietrecruit/feature/ai/interview/controller/InterviewQuestionController.java:40,57`
- **Problem:** `@PreAuthorize` allows any INTERVIEWER across all companies.
- **Fix:** Add service-level company scope check: verify the authenticated user's companyId matches the target job's companyId.
- **Verification:** Interviewer from Company A requests questions for Company B's job. Assert 403.

### TASK-030 — PayOS order code collision risk
- **Source:** H-20
- **File(s):** `src/main/java/com/vietrecruit/feature/payment/service/impl/PaymentServiceImpl.java:135-138`
- **Problem:** Only 90 possible values per millisecond. Collisions under concurrent load.
- **Fix:** Use a DB sequence or UUID-based order code. Or use `System.nanoTime()` with wider random range.
- **Verification:** Generate 1000 order codes concurrently. Assert zero duplicates.

### TASK-031 — RefreshTokenRepository missing clearAutomatically
- **Source:** H-21
- **File(s):** `src/main/java/com/vietrecruit/feature/auth/repository/RefreshTokenRepository.java:19-22`
- **Problem:** `@Modifying` bulk UPDATE bypasses L1 cache. Stale entities in persistence context.
- **Fix:** Add `@Modifying(clearAutomatically = true)`. Add `@Transactional` if not inherited.
- **Verification:** Revoke tokens, then query same user's tokens in same transaction. Assert revoked state reflected.

### TASK-032 — Interview.interviewers N+1
- **Source:** H-22
- **File(s):** `src/main/java/com/vietrecruit/feature/application/repository/InterviewRepository.java:19`
- **Problem:** `findByApplicationIdAndDeletedAtIsNull` lazy-loads `interviewers` collection per interview.
- **Fix:** Add `@EntityGraph(attributePaths = "interviewers")` to the query method. Or use `JOIN FETCH` in JPQL.
- **Verification:** Enable Hibernate SQL logging. Assert single query with JOIN, not N+1 selects.

### TASK-033 — ES TransportException bypasses IOException catch
- **Source:** H-23
- **File(s):** `JobSearchServiceImpl.java:90`, `CandidateSearchServiceImpl.java:87`, `CompanySearchServiceImpl.java:64`
- **Problem:** ES HTTP 429/503 throws `TransportException` (RuntimeException), not `IOException`. Bypasses catch block.
- **Fix:** Add `catch (RuntimeException e)` block handling `TransportException`. Or catch `Exception` with specific handling for transport vs. other errors.
- **Verification:** Mock ES to return 429. Assert proper error handling, not unlogged propagation.

### TASK-034 — Sort parameter ignored in job search
- **Source:** H-24
- **File(s):** `src/main/java/com/vietrecruit/feature/job/service/impl/JobSearchServiceImpl.java`, `JobSearchRequest.java:36`
- **Problem:** `sort` field in DTO accepted but never applied to ES query.
- **Fix:** Apply sort clause to ES SearchRequest builder based on DTO sort value.
- **Verification:** Search with sort=salary_desc. Assert results ordered by salary descending.

### TASK-035 — CandidateSearchServiceImpl RECENCY_SCRIPT null guard
- **Source:** H-25
- **File(s):** `src/main/java/com/vietrecruit/feature/candidate/service/impl/CandidateSearchServiceImpl.java:43-44`
- **Problem:** No null guard on `doc['updated_at']` in Painless script. NPE on missing field.
- **Fix:** Add `containsKey` + `.empty` check matching `JobSearchServiceImpl.PUBLISHED_AT_DECAY_SCRIPT` pattern.
- **Verification:** Index candidate document with null `updated_at`. Assert search returns results without script error.

### TASK-036 — EmbeddingService unbounded topK + orphan cleanup
- **Source:** H-26
- **File(s):** `src/main/java/com/vietrecruit/feature/ai/shared/service/EmbeddingService.java:56-75,81`
- **Problem:** No upper bound on caller-controlled `topK`. `deleteByMetadata` hardcoded topK=100 leaves orphans if document has >100 chunks.
- **Fix:** Clamp `topK` to max 100. Use paginated delete loop for `deleteByMetadata` until zero results returned.
- **Verification:** Insert 150 embeddings for one document. Call delete. Assert zero remaining.

### TASK-037 — AgentService NPE on null chat response
- **Source:** H-27
- **File(s):** `src/main/java/com/vietrecruit/feature/ai/shared/service/AgentService.java:77`
- **Problem:** No null check on `chatResponse.getResult().getOutput().getText()`.
- **Fix:** Add null checks matching `RagService`/`SalaryBenchmarkServiceImpl` pattern. Return fallback message on null.
- **Verification:** Mock ChatClient to return null output text. Assert no NPE, fallback message returned.

### TASK-038 — AgentMemoryStore GET-mutate-SET race condition
- **Source:** H-28
- **File(s):** `src/main/java/com/vietrecruit/feature/ai/shared/memory/AgentMemoryStore.java:28-41`
- **Problem:** Non-atomic read-modify-write. Concurrent appends overwrite each other.
- **Fix:** Use Redis LPUSH + LTRIM in a single pipeline or Lua script for atomic cap enforcement. Or use MULTI/EXEC transaction.
- **Verification:** 10 concurrent appends. Assert all 10 messages present (up to cap). Assert cap not exceeded.

---

## MEDIUM

### TASK-039 — PaymentReconciliationTask transaction joining
- **Source:** M-01
- **File(s):** `src/main/java/com/vietrecruit/feature/payment/service/impl/PaymentReconciliationTask.java:36,67`
- **Problem:** `activateAfterPayment` joins outer transaction. Constraint violation rolls back PAID status.
- **Fix:** Ensure activation runs in `REQUIRES_NEW` via separate bean (see TASK-002 fix).
- **Verification:** Mock activation to throw constraint violation. Assert PAID status survives.

### TASK-040 — tryActivateSubscription swallows permanent errors
- **Source:** M-02
- **File(s):** `PaymentServiceImpl.java:303-310`
- **Problem:** All `Exception` types caught and deferred to recovery loop.
- **Fix:** Distinguish transient (DataAccessException) from permanent (IllegalStateException, NullPointerException). Log permanent errors at ERROR and mark transaction for manual review.
- **Verification:** Throw `NullPointerException` in activation. Assert logged at ERROR, not silently deferred.

### TASK-041 — JWT documentation mismatch (HMAC-SHA512 vs RS256)
- **Source:** M-03
- **File(s):** `src/main/java/com/vietrecruit/common/security/JwtService.java:35,57`
- **Problem:** Implementation uses HMAC-SHA512, documentation says RS256.
- **Fix:** Update documentation to match implementation. Or migrate to RS256 if RS256 is the intended design.
- **Verification:** Documentation review.

### TASK-042 — Password reset no per-email rate limit
- **Source:** M-04
- **File(s):** `AuthController.java:126`, `AuthServiceImpl.java:486-514`
- **Problem:** Global rate limiter insufficient. Attacker can enumerate emails.
- **Fix:** Add Redis-based per-email rate limit: max 3 requests per email per 15 minutes. Key pattern: `ratelimit:password-reset:{email}`.
- **Verification:** Send 4 reset requests for same email within 15 minutes. Assert 4th returns 429.

### TASK-043 — OAuth2 cookie replay window
- **Source:** M-05
- **File(s):** `HttpCookieOAuth2AuthorizationRequestRepository.java:55-59`
- **Problem:** `removeAuthorizationRequest` does not clean up cookies. 180s replay window.
- **Fix:** Delete `oauth2_auth_request` and `redirect_uri` cookies in `removeAuthorizationRequest`.
- **Verification:** Complete OAuth2 flow. Assert cookies deleted from response.

### TASK-044 — CDC sync IOException retry classification
- **Source:** M-06
- **File(s):** `JobSyncConsumer.java:106-109`, `CandidateSyncConsumer.java:66-69`, `CompanySyncConsumer.java:66-69`
- **Problem:** `IOException` wrapped in `RuntimeException` — permanent ES errors retried unnecessarily.
- **Fix:** Classify ES errors: 400 (bad mapping) = non-retryable, 429/503 = retryable. Use `@RetryableTopic(exclude = ...)` for non-retryable.
- **Verification:** Mock ES 400 response. Assert message sent to DLT immediately, not retried.

### TASK-045 — CacheInvalidationConsumer SCAN partial failure
- **Source:** M-07
- **File(s):** `CacheInvalidationConsumer.java:127-145`
- **Problem:** Non-`DataAccessException` from Redis SCAN silently swallowed mid-operation.
- **Fix:** Catch `Exception` (not just DataAccessException), log at ERROR, and rethrow for Kafka retry.
- **Verification:** Mock Redis SCAN to throw `RedisConnectionFailureException`. Assert message retried.

### TASK-046 — KafkaConfig standard factory no error handler
- **Source:** M-08
- **File(s):** `src/main/java/com/vietrecruit/common/config/kafka/KafkaConfig.java:79-84`
- **Problem:** Standard Kafka listener factory has no explicit `CommonErrorHandler`. Default retry behavior invisible.
- **Fix:** Configure `DefaultErrorHandler` with `FixedBackOff` and `DeadLetterPublishingRecoverer`. Set explicit retry count and backoff.
- **Verification:** Configuration review. Assert error handler present on factory.

### TASK-047 — AuthServiceImpl Redis OTP before Kafka
- **Source:** M-09
- **File(s):** `AuthServiceImpl.java:153-172`
- **Problem:** OTP stored in Redis before Kafka email send. Transaction rollback leaves orphaned OTP.
- **Fix:** Move OTP storage to `afterCommit` callback, or accept OTP orphan with TTL expiry (if TTL is short, e.g., 5 min, this is acceptable risk).
- **Verification:** Mock Kafka send to throw. Assert OTP not stored in Redis (or TTL < 5 min).

### TASK-048 — AuthServiceImpl resetPassword Redis token ordering
- **Source:** M-10
- **File(s):** `AuthServiceImpl.java:518-544`
- **Problem:** Redis token deleted before DB password update can fail. Rollback leaves token deleted but password unchanged.
- **Fix:** Delete Redis token AFTER DB commit. Use `afterCommit` callback.
- **Verification:** Mock DB save to throw. Assert Redis token still exists.

### TASK-049 — ApplicationServiceImpl.insertHistory no @Transactional
- **Source:** M-11
- **File(s):** `ApplicationServiceImpl.java:243-261`
- **Problem:** Public method with no `@Transactional`. Safe only by caller context.
- **Fix:** Add `@Transactional(propagation = MANDATORY)` to enforce caller must have active transaction. Prevents accidental auto-commit usage.
- **Verification:** Call `insertHistory` without active transaction. Assert `IllegalTransactionStateException`.

### TASK-050 — CompanyServiceImpl missing readOnly default
- **Source:** M-12
- **File(s):** `src/main/java/com/vietrecruit/feature/company/service/impl/CompanyServiceImpl.java:23-88`
- **Problem:** No class-level `@Transactional(readOnly = true)`. Read methods open read-write connections.
- **Fix:** Add `@Transactional(readOnly = true)` at class level. Add `@Transactional` (read-write) on mutating methods.
- **Verification:** Configuration review.

### TASK-051 — SecurityUtils IllegalStateException leaks principal
- **Source:** M-13
- **File(s):** `GlobalExceptionHandler.java:66-68`, `SecurityUtils.java:22`
- **Problem:** `IllegalStateException` mapped to 400. Error message may contain internal principal value.
- **Fix:** Handle `IllegalStateException` from SecurityUtils specifically. Return generic "Authentication required" message. Log actual value at DEBUG.
- **Verification:** Trigger SecurityUtils with invalid principal type. Assert response contains no internal value.

### TASK-052 — InterviewQuestionServiceImpl raw RuntimeException
- **Source:** M-14
- **File(s):** `InterviewQuestionServiceImpl.java:346-353`
- **Problem:** Throws raw `RuntimeException` instead of domain exception.
- **Fix:** Throw `ApiException(AI_PARSE_ERROR)` or similar domain-specific exception.
- **Verification:** Mock JSON parse failure. Assert ApiException thrown, not RuntimeException.

### TASK-053 — WebhookVerificationException unhandled
- **Source:** M-15
- **File(s):** `GlobalExceptionHandler.java`
- **Problem:** `WebhookVerificationException` not registered. Returns generic 500.
- **Fix:** Add `@ExceptionHandler(WebhookVerificationException.class)` returning 400 with PAYMENT_WEBHOOK_INVALID_SIGNATURE code.
- **Verification:** Throw `WebhookVerificationException`. Assert HTTP 400, not 500.

### TASK-054 — CV upload trusts client Content-Type
- **Source:** M-16
- **File(s):** `CandidateServiceImpl.java:92`
- **Problem:** No byte-level MIME detection. Client can upload executable with PDF content-type.
- **Fix:** Use Apache Tika `detect()` on InputStream to verify actual MIME type matches allowed types (application/pdf).
- **Verification:** Upload `.exe` file with `Content-Type: application/pdf`. Assert rejection.

### TASK-055 — AI recommendation empty when CV not ingested
- **Source:** M-17
- **File(s):** `RecommendationServiceImpl.java:99-101`
- **Problem:** Returns empty list with no explanation when CV is not yet ingested.
- **Fix:** Check CV ingestion status. If not ingested, throw `ApiException(CV_NOT_YET_PROCESSED)` with informative message.
- **Verification:** Request recommendations with un-ingested CV. Assert meaningful error, not empty list.

### TASK-056 — CV_NOT_PARSED indistinguishable from "parsing in progress"
- **Source:** M-18
- **File(s):** `CvImprovementServiceImpl.java:82`
- **Problem:** Same error for "not parsed" and "parsing in progress".
- **Fix:** Add ingestion status check. Return `CV_PARSING_IN_PROGRESS` if ingestion event exists but processing not complete.
- **Verification:** Upload CV, immediately request improvement. Assert "in progress" status, not generic error.

### TASK-057 — Department association not enforced on job creation
- **Source:** M-19
- **Problem:** Job can be created without valid department association.
- **Fix:** Add validation in `JobServiceImpl.createJob` to verify `departmentId` belongs to the company.
- **Verification:** Create job with departmentId from different company. Assert rejection.

### TASK-058 — Scorecard duplicate race (DB constraint verification)
- **Source:** M-20
- **Problem:** Unique constraint on `(interview_id, interviewer_id)` must be verified in Flyway migrations.
- **Fix:** Verify migration exists. If not, add Flyway migration: `CREATE UNIQUE INDEX uq_scorecard_interview_interviewer ON scorecards(interview_id, interviewer_id) WHERE deleted_at IS NULL`.
- **Verification:** Check Flyway migration history for constraint.

### TASK-059 — Offer decline no rejection notification
- **Source:** M-21
- **File(s):** `OfferServiceImpl.java:238`
- **Problem:** Candidate not notified when offer is declined.
- **Fix:** Publish notification event on offer decline, similar to `sendOffer` pattern.
- **Verification:** Decline offer. Assert notification event published.

### TASK-060 — No plan upgrade/downgrade path
- **Source:** M-22
- **File(s):** `PaymentServiceImpl.java:99-104`
- **Problem:** Active plan blocks new subscription. No upgrade/downgrade mechanism.
- **Fix:** Design decision required. Options: (1) Allow upgrade with prorated billing, (2) Allow downgrade at end of current billing cycle. Mark as architectural — requires product decision.
- **Verification:** N/A — product decision required.

### TASK-061 — Free plan re-activation after cancellation
- **Source:** M-23
- **File(s):** `PaymentServiceImpl.java:115-118`
- **Problem:** Free plan can be repeatedly activated/cancelled.
- **Fix:** Add check: if user previously had free plan and cancelled, block re-activation. Or accept as feature — depends on product policy.
- **Verification:** Product decision required.

### TASK-062 — PaymentTransactionRepository Optional on non-unique compound
- **Source:** M-24
- **File(s):** `PaymentTransactionRepository.java:21`
- **Problem:** `findByCompanyIdAndStatus` returns `Optional` but compound is not unique.
- **Fix:** Change return type to `List<PaymentTransaction>` or `Optional<PaymentTransaction>` with `@Query` using `ORDER BY createdAt DESC LIMIT 1`.
- **Verification:** Insert two records with same company+status. Assert no `IncorrectResultSizeDataAccessException`.

### TASK-063 — JobSpecification null UUID parameters
- **Source:** M-25
- **File(s):** `src/main/java/com/vietrecruit/feature/job/repository/JobSpecification.java`
- **Problem:** No null guard on nullable UUID parameters in specification factory methods.
- **Fix:** Add null checks returning `Specification.where(null)` (no-op) for null parameters.
- **Verification:** Call specification with null categoryId. Assert no NPE.

### TASK-064 — ApplicationRepository non-association JOIN
- **Source:** M-26
- **File(s):** `src/main/java/com/vietrecruit/feature/application/repository/ApplicationRepository.java:42-45`
- **Problem:** Non-association JPQL JOIN. Unidiomatic but functional.
- **Fix:** Low priority. Refactor to use association-based join if entity mapping supports it.
- **Verification:** Functional test: assert query returns correct results.

### TASK-065 — CompanySearchServiceImpl keyword field in multiMatch
- **Source:** M-27
- **File(s):** `CompanySearchServiceImpl.java:81`
- **Problem:** `domain` is a `keyword` field queried via `multiMatch`. Analyzed text matching on keyword field returns no results unless exact.
- **Fix:** Change to `term` query for `domain` field, or change ES mapping to `text` with keyword sub-field.
- **Verification:** Search for partial domain. Assert results returned.

### TASK-066 — ElasticsearchIndexInitializer no startup failure
- **Source:** M-28
- **File(s):** `ElasticsearchIndexInitializer.java:62-68,97-99`
- **Problem:** Index creation error logged but application starts normally. Silent degradation.
- **Fix:** Throw on critical index creation failure to prevent app startup with broken search.
- **Verification:** Mock ES to reject index creation. Assert application fails to start.

### TASK-067 — UUID format assumption in search filter
- **Source:** M-29
- **File(s):** `JobSearchServiceImpl.java:247-262`
- **Problem:** UUID format between search filter and CDC serialization unvalidated.
- **Fix:** Add UUID format validation on filter input. Reject malformed UUIDs before ES query.
- **Verification:** Pass malformed UUID to search filter. Assert validation error.

### TASK-068 — RagService no token budget guard
- **Source:** M-30
- **File(s):** `src/main/java/com/vietrecruit/feature/ai/shared/service/RagService.java:47-68`
- **Problem:** No token budget guard before LLM call. Context window overflow possible with large retrieval sets.
- **Fix:** Estimate token count of retrieved context. Truncate or limit documents to fit within model's context window minus prompt size.
- **Verification:** Retrieve 50+ large documents. Assert total tokens sent to LLM within budget.

### TASK-069 — Embedding cache key whitespace normalization
- **Source:** M-31
- **File(s):** `EmbeddingService.java:90-103,112-120`
- **Problem:** Whitespace not normalized before SHA-256 hashing. "hello world" and "hello  world" produce different cache keys for semantically identical text.
- **Fix:** Normalize whitespace (collapse multiple spaces, trim) before hashing.
- **Verification:** Embed "hello  world" and "hello world". Assert same cache key.

### TASK-070 — RecommendationService empty stub
- **Source:** M-32
- **Problem:** No fallback design for pgvector unavailability.
- **Fix:** Add circuit breaker on pgvector calls with fallback returning empty results + warning message.
- **Verification:** Mock pgvector unavailable. Assert graceful degradation, not 500.

---

## LOW

### TASK-071 — PaymentExpiryTask no circuit breaker on PayOS cancel loop
- **Source:** L-01
- **File(s):** `PaymentExpiryTask.java:31-64`
- **Fix:** Add `@CircuitBreaker` on PayOS cancel call. Process loop items independently so one failure does not block others.

### TASK-072 — payosPayment retry config missing exception classification
- **Source:** L-02
- **File(s):** `resilience4j-prod.yml:39-44`
- **Fix:** Add `retryExceptions: [IOException.class]` and `ignoreExceptions: [ApiException.class]`.

### TASK-073 — JwtService no startup key length validation
- **Source:** L-03
- **File(s):** `JwtService.java:35-36`
- **Fix:** Add `@PostConstruct` validation asserting key length >= 64 bytes for HS512.

### TASK-074 — GitHub OAuth2 email fallback dead code
- **Source:** L-04
- **File(s):** `OAuth2AuthenticationSuccessHandler.java:74-79`
- **Fix:** Remove dead fallback branch or implement GitHub user email API call.

### TASK-075 — EmailConsumer.handleDlt null guard
- **Source:** L-05
- **File(s):** `EmailConsumer.java:57-68`
- **Fix:** Add `if (record.value() == null) { log.warn(...); return; }`.

### TASK-076 — JobSyncConsumer Caffeine null-on-miss handling
- **Source:** L-06
- **File(s):** `JobSyncConsumer.java:185-198`
- **Fix:** Cache null results with short TTL to prevent repeated DB hits. Catch `DataAccessException` from cache loader.

### TASK-077 — GlobalExceptionHandler no logging for 5xx
- **Source:** L-07
- **File(s):** `GlobalExceptionHandler.java:29-31`
- **Fix:** Add `log.error(...)` for ApiException with 5xx-mapped error codes.

### TASK-078 — AdminTransactionController response wrapper
- **Source:** L-08
- **Fix:** Wrap raw `Page` response in `PageResponse` to match API envelope convention.

### TASK-079 — Stub controllers with no endpoints
- **Source:** L-09
- **Fix:** Remove `MatchingController` and `ScreeningController` stubs, or implement pending endpoints.

### TASK-080 — icu_tokenizer empty ruleFiles
- **Source:** L-10
- **Fix:** Remove `ruleFiles("")` parameter or provide valid ICU rules file.

### TASK-081 — JobSearchServiceImpl redundant dedup
- **Source:** L-11
- **Fix:** Remove either `skipDuplicates` or `.distinct()`. Both serve same purpose.

### TASK-082 — JobRepository unbounded LIKE pattern
- **Source:** L-12
- **Fix:** Add `@Param` length validation (max 100 chars) before LIKE query.

### TASK-083 — TransactionRecordRepository existsByOrderCode race
- **Source:** L-13
- **Fix:** Add DB unique constraint on `order_code` column via Flyway migration.

### TASK-084 — InterviewRepository dead code
- **Source:** L-14
- **Fix:** Remove `findByInterviewerUserId` method.

### TASK-085 — AgentService Resilience4j annotation ordering
- **Source:** L-15
- **Fix:** Add `@Order` annotation to explicitly control aspect precedence. Ensure `@CircuitBreaker` wraps `@Retry`.

---

## Out of Scope (Architectural Changes)

The following require product decisions, multi-session implementation, or cross-team coordination:

1. **Transactional Outbox Pattern** — Replace all Kafka-inside-`@Transactional` patterns (XC-01) with an outbox table + Debezium relay. Affects 6+ services. Requires new Flyway migration, Debezium connector config, and outbox consumer. Estimated: 3-5 sessions.

2. **Saga/Compensation Framework for R2 + PayOS** — Replace ad-hoc external-system-inside-transaction patterns (XC-02) with a structured saga. Requires compensation service, idempotency keys, and dead-letter reconciliation. Estimated: 2-3 sessions.

3. **Plan Upgrade/Downgrade Flow** (M-22) — Requires product decision on proration, billing cycle alignment, and quota adjustment. Involves payment, subscription, and job modules.

4. **RBAC Overhaul** — Current `ROLE_`-based simple RBAC may need company-scoped permissions for multi-tenant isolation (H-19). Requires schema change, new middleware, and controller audit.

5. **Event-Driven Architecture Migration** — Move from synchronous service-to-service calls to event-driven patterns for decoupling. Affects all feature modules.

---

## Execution Notes

- Tasks are ordered by severity within each priority band.
- TASK-001 (authorization bypass) is the highest urgency — deploy immediately.
- TASK-002 through TASK-010 (remaining CRITICALs) should be completed before any production release.
- HIGH tasks can be batched into 2-3 PRs grouped by module.
- MEDIUM and LOW tasks can be addressed incrementally over subsequent sprints.
- Cross-cutting fixes (XC-01 through XC-04) are addressed individually per affected location in the task list, but the Out of Scope section captures the systemic architectural solution.
