CREATE TABLE knowledge_documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(255) NOT NULL,
    category     VARCHAR(100) NOT NULL,
    source_url   VARCHAR(500),
    file_name    VARCHAR(255),
    file_key     VARCHAR(500),
    chunk_count  INT NOT NULL DEFAULT 0,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    uploaded_by  UUID REFERENCES users(id),
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_category ON knowledge_documents(category);
CREATE INDEX idx_knowledge_status   ON knowledge_documents(status);
