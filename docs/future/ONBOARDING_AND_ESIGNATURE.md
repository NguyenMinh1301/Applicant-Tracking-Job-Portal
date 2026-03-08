# Future Feature: ONBOARDING_AND_ESIGNATURE

## Rationale
Currently, the pipeline concludes at the `offers` table. While `offer_letter_url` exists and transitions to `ACCEPTED`, there is no structured mechanism for producing legally binding digital signatures, capturing onboarding documentation (tax forms, ID scans), or provisioning internal IT accounts post-hire.

## Schema Evidence
- `offers.offer_letter_url` implies a static file upload, not an interactive, trackable document.
- `offers.status = 'ACCEPTED'` is the final lifecycle state in the schema, but "hired" is not synonymous with "ready to work".
- The `users` table lacks compliance fields (national ID, emergency contacts) typically required post-acceptance.

## Proposed Scope
- **Document Generation**: Automatically merge `offers.base_salary`, candidate names, and start dates into a PDF template.
- **E-Signature Tracking**: Track the digital signature envelope status (Sent, Viewed, Signed) through a provider like DocuSign or HelloSign.
- **Automated Onboarding**: Generate a checklist of tasks (e.g., "Upload ID", "Sign NDA", "Setup Payroll") for the `HIRED` candidate to complete prior to `start_date`.

## Required Schema Changes
```sql
-- e-Signature tracking for offers
CREATE TABLE offer_signatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    offer_id UUID NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL, -- e.g., 'DOCUSIGN'
    envelope_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) DEFAULT 'SENT', -- SENT, DELIVERED, COMPLETED, DECLINED
    signed_at TIMESTAMPTZ,
    audit_trail_url TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Onboarding checklists
CREATE TABLE onboarding_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    task_name VARCHAR(255) NOT NULL,
    description TEXT,
    is_required BOOLEAN DEFAULT TRUE,
    completed_at TIMESTAMPTZ,
    due_date DATE
);

CREATE INDEX idx_offer_sigs_offer ON offer_signatures(offer_id);
```

## Affected Existing Modules
- **Offer Module**: Overhaul the `PUT /api/v1/offers/{id}/send` endpoint to invoke the external e-signature API instead of just emailing a static PDF.
- **Auth Module**: Candidate accounts need elevated/persistent access post-acceptance to view their onboarding portal.

## Suggested Implementation Order
1. Integrate an e-signature provider SDK (e.g., DocuSign).
2. Implement PDF template generation mapping DB fields to document anchors.
3. Build the webhook listener to update `offer_signatures.status`.
4. Implement the candidate-facing Onboarding Dashboard.

## Open Questions
- Does the employer define custom onboarding checklists per department, or is there one global checklist per company?
- When a candidate signs the offer, do they transition to a full `users` record mapped to the employer's `company_id` automatically?
