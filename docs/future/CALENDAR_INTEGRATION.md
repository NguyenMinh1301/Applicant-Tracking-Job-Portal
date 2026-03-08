# Future Feature: CALENDAR_INTEGRATION

## Rationale
Currently, the platform allows HR to schedule interviews by specifying `scheduled_at` and `location_or_link`. However, recruitment operations heavily mandate automated scheduling, free/busy visibility, and automated email invites (ICS files or direct Google Calendar/MS Teams API integrations) to reduce manual overhead.

## Schema Evidence
- `interviews.scheduled_at` and `interviews.duration_minutes` exist.
- `interviews.location_or_link VARCHAR(255)` is a plain text field indicating the user must manually generate and paste a Zoom/Meet link.
- `user_auth_providers` exists for OAuth, but it is bound to the `users` table for *login identity*, not explicitly for *calendar delegated access*.

## Proposed Scope
- Allow HR and Interviewers to link their Google Workspace or Microsoft 365 calendars.
- Allow candidates to see available time slots (Free/Busy check) and self-schedule their interviews based on interviewer availability.
- Automatically generate Google Meet or MS Teams links when an interview transitions to `SCHEDULED` status.
- Sync status changes (e.g., `CANCELED`) directly to the external calendar event.

## Required Schema Changes
```sql
-- Store delegated tokens specifically for calendar sync, separate from login providers
CREATE TABLE user_calendar_integrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL, -- e.g., 'GOOGLE_CALENDAR', 'OUTLOOK'
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    token_expires_at TIMESTAMPTZ NOT NULL,
    sync_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, provider)
);

-- Link our internal interview explicitly to the external provider's event ID
ALTER TABLE interviews 
    ADD COLUMN external_event_id VARCHAR(255),
    ADD COLUMN external_calendar_provider VARCHAR(50);
```

## Affected Existing Modules
- **Interview Module**: Scheduling logic must be overhauled to interact with third-party APIs prior to committing the `interviews` row.
- **Auth Module**: Needs a dedicated OAuth flow for calendar scopes (e.g., `https://www.googleapis.com/auth/calendar.events`), distinct from standard SSO scopes.
- **Notification Services**: Need to handle bidirectional webhooks if a user deletes the event directly on Google Calendar.

## Suggested Implementation Order
1. Setup Google Cloud / Azure AD OAuth clients specifically for calendar scopes.
2. Introduce the `user_calendar_integrations` table and the OAuth callback flow.
3. Modify the `POST /api/v1/applications/{id}/interviews` endpoint to provision the external event.
4. Add candidate self-scheduling UI.

## Open Questions
- How do we handle multi-interviewer scheduling? Do we require a consensus algorithm to find common free time, or just allow the primary HR user to pick?
- If a token expires and refresh fails, how do we gracefully fallback to manual links?
