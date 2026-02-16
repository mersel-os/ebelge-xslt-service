# ══════════════════════════════════════════════════════════════════════
# MERSEL.Services.XsltService — Multi-stage Docker Build
# ══════════════════════════════════════════════════════════════════════

# ── UI Build Stage ──────────────────────────────────────────────────
# Optional: skip with --build-arg SKIP_UI=true
FROM node:22-alpine AS ui-build
ARG SKIP_UI=false
WORKDIR /ui

RUN corepack enable && corepack prepare pnpm@8.15.3 --activate

COPY xslt-web-ui/package.json xslt-web-ui/pnpm-lock.yaml ./
RUN if [ "$SKIP_UI" != "true" ]; then pnpm install --frozen-lockfile; fi

COPY xslt-web-ui/ .
RUN if [ "$SKIP_UI" != "true" ]; then pnpm build; fi

# ── Java Build Stage ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradle/ gradle/
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle/libs.versions.toml gradle/

COPY xslt-application/ xslt-application/
COPY xslt-infrastructure/ xslt-infrastructure/
COPY xslt-web-api/ xslt-web-api/

# Copy UI build output into static resources
COPY --from=ui-build /ui/dist/ xslt-web-api/src/main/resources/static/

RUN chmod +x gradlew && ./gradlew :xslt-web-api:bootJar --no-daemon -PskipUi

# ── Runtime Stage ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN apk add --no-cache curl su-exec

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/xslt-web-api/build/libs/*.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

# External asset override dizini — appuser needs write access
RUN mkdir -p /opt/xslt-assets && chown -R appuser:appgroup /opt/xslt-assets

HEALTHCHECK --interval=30s --timeout=10s --start-period=15s --retries=3 \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1

# ── JVM Yapılandırması ──────────────────────────────────────────────
# 200MB+ XML → Saxon in-memory tree ~3-5x belge boyutu.
# -Xmx2g: Büyük belgeleri desteklemek için yeterli heap.
# ZGC: Düşük gecikme, büyük geçici nesneler (DOM tree) için ideal.
# MaxDirectMemorySize: NIO buffer'ları ve dosya yüklemeleri için.
ENV JAVA_OPTS="\
  -Xms512m \
  -Xmx2g \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:MaxDirectMemorySize=512m \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# Container root olarak başlar, entrypoint izinleri düzeltir ve
# su-exec ile appuser'a düşer. Bu sayede volume mount izin sorunu çözülür.
ENTRYPOINT ["/app/docker-entrypoint.sh"]
