# API Gateway Testing Guide

## Overview

This guide explains how to run the API Gateway tests, including circuit breaker and rate limiting integration tests.

## Prerequisites

### Required Infrastructure

The integration tests require the following infrastructure to be running:

1. **Redis** (for rate limiting)
   - Port: 6379
   - Used by: Rate limiting tests

2. **Docker** (for Testcontainers)
   - Required for: RateLimitingIntegrationTest (Redis Testcontainer)
   - Minimum Docker API version: 1.40

### Starting Infrastructure

#### Option 1: Docker Compose (Recommended)

```bash
# Start Redis only
docker run -d -p 6379:6379 redis:7-alpine

# Or start full infrastructure
docker-compose -f deployment/docker-compose.infra.yml up -d
```

#### Option 2: Local Redis Installation

```bash
# Ubuntu/Debian
sudo apt-get install redis-server
sudo systemctl start redis

# macOS
brew install redis
brew services start redis
```

## Running Tests

### Run All API Gateway Tests

```bash
./gradlew :api-gateway:test
```

### Run Specific Test Classes

```bash
# Circuit breaker tests only
./gradlew :api-gateway:test --tests CircuitBreakerIntegrationTest

# Rate limiting tests only
./gradlew :api-gateway:test --tests RateLimitingIntegrationTest

# JWT validation tests only
./gradlew :api-gateway:test --tests JwtValidationUnitTest
```

### Run Specific Test Methods

```bash
# Single circuit breaker test
./gradlew :api-gateway:test --tests CircuitBreakerIntegrationTest.testCircuitBreakerOpensAfter5ConsecutiveFailures

# Single rate limiting test
./gradlew :api-gateway:test --tests RateLimitingIntegrationTest.testRateLimitingReturns429After100Requests
```

## Test Configuration

### Test-Specific Configuration

The tests use `application-test.yml` with the following key differences from production:

- **Circuit Breaker Wait Duration**: 1 second (vs 30 seconds in production)
  - Allows faster test execution
  - Tests can verify half-open state transitions quickly

- **Redis Connection**: Configured via Testcontainers
  - Automatically starts Redis container
  - Isolated test environment

### Environment Variables

You can override test configuration using environment variables:

```bash
# Custom Redis host/port
SPRING_REDIS_HOST=localhost SPRING_REDIS_PORT=6379 ./gradlew :api-gateway:test

# Custom JWT issuer URI
JWT_ISSUER_URI=http://localhost:9000 ./gradlew :api-gateway:test
```

## Troubleshooting

### Issue: Docker API Version Too Old

**Error:**
```
client version 1.32 is too old. Minimum supported API version is 1.40
```

**Solution:**
Update Docker to a newer version or upgrade Testcontainers:

```bash
# Check Docker version
docker version

# Upgrade Docker (Ubuntu/Debian)
sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io

# Or skip Testcontainer tests
./gradlew :api-gateway:test --tests CircuitBreakerIntegrationTest
```

### Issue: Redis Connection Refused

**Error:**
```
Connection refused: localhost/127.0.0.1:6379
```

**Solution:**
Start Redis before running tests:

```bash
docker run -d -p 6379:6379 redis:7-alpine
```

### Issue: Application Context Failure

**Error:**
```
ApplicationContext failure threshold (1) exceeded
```

**Solution:**
This usually indicates a configuration issue. Check:

1. Redis is running and accessible
2. JWT configuration is valid
3. All required dependencies are present

```bash
# Clean and rebuild
./gradlew clean :api-gateway:build

# Run with debug logging
./gradlew :api-gateway:test --debug
```

### Issue: WireMock Port Conflicts

**Error:**
```
Address already in use: bind
```

**Solution:**
Another process is using ports 8081-8087. Stop conflicting processes:

```bash
# Find process using port
lsof -i :8081

# Kill process
kill -9 <PID>
```

## Test Coverage

### Circuit Breaker Tests

| Test | Description | Requirements |
|------|-------------|--------------|
| `testCircuitBreakerOpensAfter5ConsecutiveFailures` | Verifies circuit opens after 5 failures | 10.7, 18 |
| `testCircuitBreakerClosesAfterSuccessfulTestRequest` | Verifies circuit closes after successful test in half-open state | 10.7, 18 |
| `testCircuitBreakerConfiguration` | Validates circuit remains closed below threshold | 18 |
| `testFallbackResponseFormat` | Verifies fallback response structure | 10.5 |
| `testCircuitBreakerPerService` | Tests circuit breaker isolation per service | 10.7, 18 |

### Rate Limiting Tests

| Test | Description | Requirements |
|------|-------------|--------------|
| `testRateLimitingReturns429After100Requests` | Verifies 429 after 100 requests/minute | 10.5, 10.7 |
| `testRateLimitingPerUser` | Tests per-user rate limiting | 10.5 |
| `testRateLimitingResetsAfterTimeWindow` | Verifies rate limit resets after 1 minute | 10.5 |
| `testRateLimitingAppliesToAllRoutes` | Tests rate limiting across all routes | 10.5 |
| `testBurstCapacityAllowsTemporarySpike` | Validates burst capacity (200 requests) | 10.5 |

## Performance Considerations

### Test Execution Time

- **Circuit Breaker Tests**: ~5-10 seconds per test
  - `testCircuitBreakerClosesAfterSuccessfulTestRequest`: ~30 seconds (waits for half-open state)
  
- **Rate Limiting Tests**: ~5-15 seconds per test
  - `testRateLimitingResetsAfterTimeWindow`: ~60 seconds (waits for window reset)

### Optimizing Test Execution

To speed up test execution:

1. **Skip Long-Running Tests:**
   ```bash
   ./gradlew :api-gateway:test --tests CircuitBreakerIntegrationTest --tests '!*ClosesAfterSuccessful*'
   ```

2. **Run Tests in Parallel:**
   ```bash
   ./gradlew :api-gateway:test --parallel --max-workers=4
   ```

3. **Use Test Filters:**
   ```bash
   # Run only fast tests (exclude long-running)
   ./gradlew :api-gateway:test --tests '*IntegrationTest' --tests '!*ResetsAfterTimeWindow'
   ```

## Continuous Integration

### GitHub Actions Example

```yaml
name: API Gateway Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      redis:
        image: redis:7-alpine
        ports:
          - 6379:6379
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Run API Gateway Tests
        run: ./gradlew :api-gateway:test
      
      - name: Upload Test Reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: api-gateway/build/reports/tests/test/
```

## Manual Testing

### Testing Circuit Breaker Manually

1. **Start API Gateway:**
   ```bash
   ./gradlew :api-gateway:bootRun
   ```

2. **Simulate Service Failures:**
   ```bash
   # Make 5 requests to failing service
   for i in {1..5}; do
     curl -v http://localhost:8080/orders/123
   done
   ```

3. **Verify Circuit Opens:**
   ```bash
   # Next request should return fallback (503)
   curl -v http://localhost:8080/orders/123
   ```

4. **Wait for Half-Open State:**
   ```bash
   # Wait 30 seconds
   sleep 30
   
   # Make successful request to close circuit
   curl -v http://localhost:8080/orders/123
   ```

### Testing Rate Limiting Manually

1. **Start API Gateway and Redis:**
   ```bash
   docker run -d -p 6379:6379 redis:7-alpine
   ./gradlew :api-gateway:bootRun
   ```

2. **Make 100 Requests:**
   ```bash
   for i in {1..100}; do
     curl -v http://localhost:8080/orders/123
   done
   ```

3. **Verify Rate Limit:**
   ```bash
   # 101st request should return 429
   curl -v http://localhost:8080/orders/123
   ```

## Additional Resources

- [Spring Cloud Gateway Documentation](https://spring.io/projects/spring-cloud-gateway)
- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [WireMock Documentation](https://wiremock.org/)

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review test logs in `api-gateway/build/reports/tests/test/`
3. Run tests with `--info` or `--debug` for detailed output
4. Consult the implementation summary in `TASK_14_IMPLEMENTATION_SUMMARY.md`
