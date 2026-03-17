CREATE TABLE interview_ai_questions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id    UUID NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    questions_json  JSONB NOT NULL,
    generated_at    TIMESTAMP NOT NULL DEFAULT now(),
    model_used      VARCHAR(50)
);

CREATE UNIQUE INDEX idx_interview_questions_interview
    ON interview_ai_questions(interview_id);
