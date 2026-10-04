# ── Stage 1: build ──────────────────────────────────────────────────
# Use the official Maven image instead of the ./mvnw wrapper: the wrapper
# script fails inside the container (exit 127) when the repo is checked out
# on Windows with CRLF line endings.
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml ./
RUN mvn dependency:go-offline -q

COPY src/ src/
RUN mvn package -DskipTests -q

# ── Stage 2: runtime ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S gymapp && adduser -S gymapp -G gymapp
USER gymapp

COPY --from=builder /app/target/*.jar app.jar

# Container-aware heap sizing; overridable at runtime via JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

# The base image has no system tzdata and defaults the JVM clock to UTC; LocalDate.now()
# (subscription/check-in day boundaries, etc.) would then flip to the next day up to 2h
# early for Italian users. The JDK ships its own tzdb, so no OS package is needed — just
# tell every JVM invocation (via the auto-picked-up JAVA_TOOL_OPTIONS) to use Europe/Rome.
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Europe/Rome"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
