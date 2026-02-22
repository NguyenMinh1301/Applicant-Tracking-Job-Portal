CREATE TABLE interview_interviewers (
    interview_id UUID NOT NULL REFERENCES interviews(id),
    user_id UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY (interview_id, user_id)
);
