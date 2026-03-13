-- ============================================================
-- VietRecruit | Migration V1__create_types.sql
-- Description: PostgreSQL extensions, custom enum types,
--              sequences, and implicit varchar→enum casts.
-- Depends on:  none
-- ============================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Enum types
CREATE TYPE job_status          AS ENUM ('DRAFT', 'PUBLISHED', 'CLOSED');
CREATE TYPE application_status  AS ENUM ('NEW', 'SCREENING', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED');
CREATE TYPE interview_status    AS ENUM ('SCHEDULED', 'COMPLETED', 'CANCELED');
CREATE TYPE scorecard_result    AS ENUM ('PASS', 'FAIL', 'CONSIDERING');
CREATE TYPE offer_status        AS ENUM ('DRAFT', 'SENT', 'ACCEPTED', 'DECLINED');
CREATE TYPE subscription_status AS ENUM ('ACTIVE', 'EXPIRED', 'CANCELLED');
CREATE TYPE payment_status      AS ENUM ('PENDING', 'PAID', 'CANCELLED', 'EXPIRED', 'FAILED');
CREATE TYPE billing_cycle       AS ENUM ('MONTHLY', 'YEARLY');

-- PayOS order code sequence
CREATE SEQUENCE payos_order_code_seq START WITH 1000000 INCREMENT BY 1;

-- Implicit casts: Hibernate 6 sends enum values as VARCHAR;
-- PostgreSQL has no built-in implicit cast from varchar to custom enums.
CREATE CAST (varchar AS job_status)          WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS application_status)  WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS interview_status)    WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS scorecard_result)    WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS offer_status)        WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS subscription_status) WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS payment_status)      WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS billing_cycle)       WITH INOUT AS IMPLICIT;
