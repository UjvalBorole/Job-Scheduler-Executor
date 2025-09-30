# # -------- Stage 1: Build --------
# FROM maven:3.9.6-eclipse-temurin-21 AS builder

# WORKDIR /build

# # First copy only pom.xml to leverage Docker cache
# COPY pom.xml .

# # Pre-download dependencies (cached unless pom.xml changes)
# RUN mvn dependency:go-offline -B

# # Now copy the rest of the source code
# COPY src ./src

# # Build the JAR (skip tests for faster builds)
# RUN mvn clean package -DskipTests


# -------- Stage 1: Build --------
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml and download dependencies (cached)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy everything including src and force rebuild
COPY . .

# Build JAR (skip tests)
RUN mvn clean package -DskipTests

# -------- Stage 2: Runtime --------
# Use Java 21 runtime base image
FROM eclipse-temurin:21-jdk

# Set working directory
WORKDIR /app

# Install netcat for health/wait checks
RUN apt-get update && \
    apt-get install -y netcat-openbsd && \
    rm -rf /var/lib/apt/lists/*

# Copy and prepare wait-for script (to wait for Kafka/DB/etc.)
COPY wait-for.sh /wait-for.sh
RUN chmod +x /wait-for.sh && \
    sed -i 's/\r$//' /wait-for.sh 2>/dev/null || true  # Handle Windows line endings

# Copy application JAR (executor-manual build output)
# COPY target/*.jar app.jar

# Copy application JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar  

# Define healthcheck (uses SERVER_PORT from docker-compose.yml, defaults to 8080)
HEALTHCHECK --interval=30s --timeout=7s --start-period=20s --retries=3 \
  CMD curl -f http://executor-manual-service:${SERVER_PORT:-8080}/actuator/health || exit 1

# Entrypoint waits for Kafka (or other dependencies), then runs app
ENTRYPOINT ["/bin/sh", "-c", "\
    /wait-for.sh discovery-service 8090 && \
    /wait-for.sh config-server 8092 && \
    /wait-for.sh kafka 9092 && \
    exec java -jar app.jar"]


# Start the Spring Boot application
# CMD ["java", "-jar", "app.jar"]

