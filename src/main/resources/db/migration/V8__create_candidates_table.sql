CREATE TABLE candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    headline VARCHAR(255),
    summary TEXT,
    default_cv_url VARCHAR(255),
    cv_embedding vector(1536),
    parsed_cv_text TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_candidates_user_id ON candidates(user_id);
CREATE INDEX hnsw_candidate_cv_idx ON candidates USING hnsw (cv_embedding vector_cosine_ops);
