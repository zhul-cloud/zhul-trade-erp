# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN sed -i 's|http://|https://|g' /etc/apt/sources.list \
    && apt-get update --error-on=any \
    && apt-get install -y --no-install-recommends \
        ca-certificates curl libreoffice-calc fonts-noto-cjk fonts-liberation ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 zhul \
    && useradd --uid 10001 --gid 10001 --create-home zhul
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/zhul-erp-*.jar /app/app.jar
COPY docker-entrypoint.sh /usr/local/bin/zhul-entrypoint
RUN sed -i 's/\r$//' /usr/local/bin/zhul-entrypoint \
    && chmod 755 /usr/local/bin/zhul-entrypoint \
    && mkdir -p /app/uploads /app/private-uploads \
    && chown -R 10001:10001 /app
# 统一北京时间（不用 Compose 单独部署时也生效）；JVM 自带时区库，不依赖系统 tzdata
ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=docker \
    UPLOAD_DIR=/app/uploads \
    UPLOAD_PRIVATE_DIR=/app/private-uploads
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=3 \
    CMD curl --fail --silent --show-error --max-time 8 http://127.0.0.1:8080/api/v1/public/health || exit 1
ENTRYPOINT ["/usr/local/bin/zhul-entrypoint"]
CMD ["java", "-jar", "/app/app.jar"]
