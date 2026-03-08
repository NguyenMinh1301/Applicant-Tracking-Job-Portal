# Future Feature: AI_CANDIDATE_MATCHING

## Rationale
Modern recruitment platforms leverage large language models (LLMs) to bridge the semantic gap between a job description and a candidate's resume. Traditional keyword matching is insufficient. The presence of vector embeddings in the schema confirms this architectural intent.

## Schema Evidence
- `jobs.embedding vector(1536)` and the `hnsw_job_embedding_idx` index using `vector_cosine_ops`.
- `candidates.cv_embedding vector(1536)` and the `hnsw_candidate_cv_idx` index using `vector_cosine_ops`.
- `subscription_plans.ai_matching BOOLEAN` implies this feature is paywalled behind premium tiers.
- `candidates.parsed_cv_text TEXT` indicates an extraction pipeline precedes the embedding generation.

## Proposed Scope
- **For Employers**: When viewing a job posting, immediately view a list of "Suggested Candidates" ranked by semantic similarity (cosine distance) to the job description, provided the employer is on a tier where `ai_matching = TRUE`.
- **For Candidates**: When browsing jobs, heavily weight the sort order using their `cv_embedding` against the `job.embedding` to highlight "Best Matches".
- **Batch Processing**: An async pipeline (via Kafka, given the tech stack) that triggers on CV upload or Job creation, calls an embedding API (e.g., OpenAI `text-embedding-ada-002` corresponding to 1536 dims), and updates the vector fields.

## Required Schema Changes
```sql
-- Track AI suggestions explicitly to avoid re-computing dynamic distances if we want to cache matches
CREATE TABLE ai_candidate_matches (
    job_id UUID REFERENCES jobs(id) ON DELETE CASCADE,
    candidate_id UUID REFERENCES candidates(id) ON DELETE CASCADE,
    match_score NUMERIC(5, 4) NOT NULL,
    generated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    is_dismissed BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (job_id, candidate_id)
);

CREATE INDEX idx_ai_matches_job ON ai_candidate_matches(job_id) WHERE is_dismissed = FALSE;
```

## Affected Existing Modules
- **Job Module**: Needs a new endpoint `GET /api/v1/jobs/{id}/suggested-candidates`.
- **Candidate Module**: The CV upload flow must fire a `CvParsedEvent` to trigger embedding generation.
- **Subscription Module**: Must inject an interceptor/filter affirming the `ai_matching` capability before serving the suggested-candidates endpoint.

## Suggested Implementation Order
1. Build the async worker (Kafka consumer) to interface with the LLM Embedding API.
2. Backfill embeddings for existing `parsed_cv_text` and `description`.
3. Implement the internal `/suggested` endpoint using `pgvector` operators (`<=>`).
4. Implement the UI rendering the match scores.

## Open Questions
- What is the threshold (cosine distance limit) for considering a candidate a "Match"?
- Do we embed the entire `parsed_cv_text` as one vector, or chunk it? A 1536-dim vector might lose resolution on a multi-page CV if pooled naively.
