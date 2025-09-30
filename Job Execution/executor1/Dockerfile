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
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Install netcat + curl for wait-for & healthcheck
RUN apt-get update && \
    apt-get install -y netcat-openbsd curl && \
    rm -rf /var/lib/apt/lists/*

# Copy wait-for script
COPY wait-for.sh /wait-for.sh
RUN chmod +x /wait-for.sh && sed -i 's/\r$//' /wait-for.sh || true

# Copy JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Healthcheck -> Spring Boot actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

# ENTRYPOINT script to wait for dependencies before starting app
ENTRYPOINT ["/bin/sh", "-c", "\
    /wait-for.sh discovery-service 8090 && \
    /wait-for.sh config-server 8092 && \
    /wait-for.sh kafka 9092 && \
    exec java -jar app.jar"]


# Start the Spring Boot application
# CMD ["java", "-jar", "app.jar"]
