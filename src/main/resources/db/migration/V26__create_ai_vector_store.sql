-- ============================================================
-- VietRecruit | Migration V26__create_ai_vector_store.sql
-- Description: Spring AI PgVectorStore table for RAG document
--              storage. Separate from entity-level embedding
--              columns on jobs and candidates.
-- Depends on:  V1 (vector extension)
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSONB,
    embedding vector(1536)
);

CREATE INDEX IF NOT EXISTS ai_vector_store_embedding_idx
    ON ai_vector_store USING hnsw (embedding vector_cosine_ops);

COMMENT ON TABLE  ai_vector_store           IS 'Spring AI PgVectorStore for RAG document embeddings';
COMMENT ON COLUMN ai_vector_store.content   IS 'Original document text (CV excerpt, job description, etc.)';
COMMENT ON COLUMN ai_vector_store.metadata  IS 'Document metadata: source type, entity ID, timestamps';
COMMENT ON COLUMN ai_vector_store.embedding IS '1536-dim text-embedding-3-small vector for similarity search';
