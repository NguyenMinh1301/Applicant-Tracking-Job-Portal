<p align="center">
    <a href="https://spring.io/" target="_blank"><img src="assets/img/springio.svg" height="90" alt="Spring Boot" /></a>&nbsp;
    <a href="https://www.postgresql.org/" target="_blank"><img src="assets/img/postgresql.svg" height="90" alt="PostgreSQL" /></a>&nbsp;
    <a href="https://redis.io/" target="_blank"><img src="assets/img/redis.svg" height="90" alt="Redis" /></a>&nbsp;
    <a href="https://www.elastic.co/" target="_blank"><img src="assets/img/elastic.svg" height="90" alt="Elasticsearch" /></a>&nbsp;
    <a href="https://kafka.apache.org/" target="_blank"><img src="assets/img/kafka.svg" height="90" alt="Kafka" /></a>&nbsp;
    <a href="https://debezium.io/" target="_blank"><img src="assets/img/debeziumio.svg" height="90" alt="Debezium" /></a>&nbsp;
    <a href="https://www.docker.com/" target="_blank"><img src="assets/img/docker.svg" height="90" alt="Docker" /></a>&nbsp;
    <a href="https://jenkins.io/" target="_blank"><img src="assets/img/jenkins.svg" height="90" alt="Jenkins" /></a>&nbsp;
</p>

# Applicant tracking job portal — Backend

Backend service for VietRecruit, Applicant Tracking System, and job portal.

## Overview

VietRecruit serves two distinct user bases. Candidates build structured profiles and CVs, search published job listings, and submit applications directly through the platform. Employers — company administrators and HR staff — manage company branding, configure internal departments, purchase subscription plans through a PayOS integration to acquire job posting quotas, and publish open roles.

The ATS subsystem tracks each candidate through a defined pipeline: application receipt, interview scheduling with cross-interviewer scorecards, offer generation, and final acceptance or rejection. Role-based access control enforces boundaries between `SYSTEM_ADMIN`, `CUSTOMER_SERVICE`, `COMPANY_ADMIN`, `HR`, `INTERVIEWER`, and `CANDIDATE` principals at every layer.

The system follows a Modular Monolith architecture with Domain-Driven Design. Each feature module encapsulates its own controller, service, repository, entity, mapper, and DTO layers, with no cross-module repository access permitted. Cross-cutting infrastructure (security, caching, messaging, search) lives in a shared `common/` package. For the complete architecture map, module responsibilities, and infrastructure topology, refer to [`docs/`](docs/).
