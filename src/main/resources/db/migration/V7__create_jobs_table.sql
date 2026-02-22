CREATE TABLE jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    department_id UUID REFERENCES departments(id),
    location_id UUID REFERENCES locations(id),
    category_id UUID REFERENCES categories(id),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    requirements TEXT,
    min_salary NUMERIC(15, 2),
    max_salary NUMERIC(15, 2),
    currency VARCHAR(10) DEFAULT 'VND',
    is_negotiable BOOLEAN DEFAULT FALSE,
    status job_status DEFAULT 'DRAFT',
    deadline DATE,
    public_link VARCHAR(255) UNIQUE,
    embedding vector(1536),
    created_by UUID REFERENCES users(id),
    updated_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_jobs_company_id ON jobs(company_id);
CREATE INDEX idx_jobs_department_id ON jobs(department_id);
CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_jobs_deleted_at ON jobs(deleted_at);
CREATE INDEX hnsw_job_embedding_idx ON jobs USING hnsw (embedding vector_cosine_ops);
