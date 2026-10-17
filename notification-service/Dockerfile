# --- Stage 1: Dependency Cache Phase ---
FROM maven:3.9-eclipse-temurin-26-alpine AS deps
WORKDIR /build

# Copy ONLY pom files first to build a true dependency cache layer
COPY pom.xml .
COPY dfls-shared-dto/pom.xml dfls-shared-dto/
COPY api-gateway/pom.xml api-gateway/
COPY identity-service/pom.xml identity-service/
COPY account-service/pom.xml account-service/
COPY transfer-service/pom.xml transfer-service/
COPY notification-service/pom.xml notification-service/

# Download all plugins and dependencies. This layer caches heavily!
RUN mvn dependency:go-offline -B

# --- Stage 2: Build Phase ---
FROM deps AS builder
WORKDIR /build

# Copy the actual source code now
COPY dfls-shared-dto/src dfls-shared-dto/src/
COPY api-gateway/src api-gateway/src/
COPY identity-service/src identity-service/src/
COPY account-service/src account-service/src/
COPY transfer-service/src transfer-service/src/
COPY notification-service/src notification-service/src/

# Build the specific module passed via docker build argument
ARG MODULE_NAME
# -am ensures dependent modules (like dfls-shared-dto) are built automatically
RUN mvn -pl $MODULE_NAME -am clean package -DskipTests

# --- Stage 3: Clean Runtime Isolation Phase ---
FROM eclipse-temurin:26-jre-alpine
WORKDIR /app

# Add a non-root group and user for strict security compliance
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the compiled executable jar from the builder layer
ARG MODULE_NAME
COPY --from=builder /build/$MODULE_NAME/target/*.jar app.jar

# Ensure ownership belongs to the secure runtime user
RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]