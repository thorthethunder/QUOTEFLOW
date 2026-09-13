# syntax=docker/dockerfile:1

# QuoteFlow backend — multi-stage production image (Java 21).
# Build: docker build -t quoteflow-backend -f backend/Dockerfile backend

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw \
	&& ./mvnw -B -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package \
	&& cp target/quoteflow-backend-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN apk add --no-cache curl \
	&& addgroup -S quoteflow && adduser -S -G quoteflow quoteflow \
	&& mkdir -p /tmp \
	&& chown -R quoteflow:quoteflow /app /tmp

COPY --from=build --chown=quoteflow:quoteflow /workspace/app.jar /app/app.jar

USER quoteflow
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
	SPRING_PROFILES_ACTIVE=prod \
	TZ=UTC \
	LANG=C.UTF-8

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
	CMD curl -fsS http://127.0.0.1:8080/actuator/health/liveness >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
