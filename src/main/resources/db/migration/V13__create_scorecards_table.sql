CREATE TABLE scorecards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id UUID NOT NULL REFERENCES interviews(id),
    interviewer_id UUID NOT NULL REFERENCES users(id),
    skill_score NUMERIC(5, 2) CHECK (skill_score >= 0),
    attitude_score NUMERIC(5, 2) CHECK (attitude_score >= 0),
    english_score NUMERIC(5, 2) CHECK (english_score >= 0),
    average_score NUMERIC(5, 2) GENERATED ALWAYS AS ((skill_score + attitude_score + english_score) / 3.0) STORED,
    comments TEXT,
    result scorecard_result NOT NULL,
    created_by UUID REFERENCES users(id),
    updated_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_scorecards_interview_id ON scorecards(interview_id);
