# Future Feature: TALENT_POOL_MANAGEMENT

## Rationale
Employers often encounter high-quality candidates who are not selected for a specific role (e.g., finished second, or applied to an ill-fitting job). Currently, the system loosely tracks candidates via past applications. A structured "Talent Pool" or "CRM" approach allows employers to group, tag, and remarket to these candidates when new jobs open.

## Schema Evidence
- `candidates` are explicitly linked to `users` and completely decoupled from `jobs` (the linkage only happens via the `applications` join table). 
- However, there is no ownership mapping between a `company` and a `candidate` without an active application.
- `applications.status` supports `REJECTED`, but there is no terminal state for "Kept on File".

## Proposed Scope
- Allow HR to explicitly "Save" or "Pool" a candidate profile into custom folders/tags.
- Permit direct messaging/invitations from the Employer to pooled candidates to apply for newly `PUBLISHED` jobs.
- Require candidate consent (Privacy tracking) for an employer to retain their profile indefinitely post-rejection.

## Required Schema Changes
```sql
CREATE TABLE company_talent_pools (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE talent_pool_candidates (
    pool_id UUID NOT NULL REFERENCES company_talent_pools(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    added_by UUID REFERENCES users(id),
    notes TEXT,
    added_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    consent_granted BOOLEAN DEFAULT TRUE,
    PRIMARY KEY (pool_id, candidate_id)
);

CREATE INDEX idx_talent_pool_candidates_candidate ON talent_pool_candidates(candidate_id);
```

## Affected Existing Modules
- **Application Module**: Add an action post-rejection to "Move to Talent Pool".
- **Candidate Module**: Expose candidate visibility settings allowing them to revoke consent from company pools.
- **Job Module**: Add an "Invite from Pool" action when a job transitions to `PUBLISHED`.

## Suggested Implementation Order
1. Define the Data Privacy / Consent architecture for data retention.
2. Build the `company_talent_pools` CRUD endpoints.
3. Build the UI for employers to browse their pooled candidates.
4. Implement the outbound invite system.

## Open Questions
- If a candidate updates their `cv_url` in the global `candidates` table, do the employers in the talent pool see the new CV, or are they restricted to the snapshot from when the candidate originally interacted with the company?
- Is pooling candidates a feature restricted to the `PREMIUM` or `ENTERPRISE` subscription plans?
