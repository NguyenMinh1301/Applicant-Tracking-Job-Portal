# Resilience Strategy

## Overview

System stability is enforced via Resilience4j. Critical boundaries execute within Circuit Breakers and Rate Limiters to prevent failure cascades.

## Circuit Breaker Configuration

Circuit breakers prevent repeated execution of failing external calls (e.g., third-party APIs or slow DB queries).

```yaml
resilience4j:
    circuitbreaker:
        instances:
            default:
                slidingWindowSize: 20
                minimumNumberOfCalls: 10
                waitDurationInOpenState: 30s
                failureRateThreshold: 50
                permittedNumberOfCallsInHalfOpenState: 5
```

## Rate Limiter Configuration

API endpoints are protected against abuse via tiered rate limits.

```yaml
resilience4j:
    ratelimiter:
        instances:
            authStrict:
                limitForPeriod: 10
                limitRefreshPeriod: 60s
                timeoutDuration: 0
            swagger:
                limitForPeriod: 50
                limitRefreshPeriod: 60s
            highTraffic:
                limitForPeriod: 25
                limitRefreshPeriod: 30s
```

## Implementation Rules

Always apply `@CircuitBreaker` or `@RateLimiter` annotations at the `BaseController` or individual `Controller` level, mapped to the exact instance names defined in `src/main/resources/resilience/`.
