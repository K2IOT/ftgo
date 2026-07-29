# syntax=docker/dockerfile:1.7

ARG BUILD_IMAGE=eclipse-temurin:21-jdk-jammy
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-jammy

FROM ${BUILD_IMAGE} AS builder
ARG SERVICE
ARG VERSION
ARG GIT_SHA

WORKDIR /workspace
COPY . .

RUN test -n "${SERVICE}" \
    && test -n "${VERSION}" \
    && test -n "${GIT_SHA}" \
    && chmod +x ./gradlew \
    && --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon ":${SERVICE}:bootJar" \
    && jar_path="$(find "${SERVICE}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && test -n "${jar_path}" \
    && cp "${jar_path}" /workspace/app.jar

FROM ${RUNTIME_IMAGE} AS runtime
ARG SERVICE
ARG VERSION
ARG GIT_SHA

LABEL org.opencontainers.image.title="ftgo-${SERVICE}" \
      org.opencontainers.image.source="https://github.com/K2IOT/ftgo" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.version="${VERSION}"

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 ftgo \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin ftgo \
    && mkdir -p /app /tmp \
    && chown -R 10001:10001 /app /tmp

ENV SERVER_PORT=8080 \
    SERVER_SHUTDOWN=graceful \
    SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=30s \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Djava.io.tmpdir=/tmp -Djava.security.egd=file:/dev/urandom"

WORKDIR /app
COPY --from=builder --chown=10001:10001 /workspace/app.jar /app/app.jar

EXPOSE 8080
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/liveness || exit 1

USER 10001:10001
CMD ["java", "-jar", "/app/app.jar"]
