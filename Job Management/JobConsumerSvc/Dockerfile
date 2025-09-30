# # -------- Stage 1: Build --------
# FROM maven:3.9.6-eclipse-temurin-21 AS builder

# WORKDIR /build

# # Copy pom.xml and download dependencies
# COPY pom.xml .
# RUN mvn dependency:go-offline -B
# # there has the cached of pom.xml file's dependencies

# # Copy source code and build
# COPY src ./src
# RUN mvn clean package -DskipTests -U
# # that only works when any changes made in src not pom

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

# Install dependencies with error handling
RUN apt-get update && \
    apt-get install -y netcat-openbsd && \
    rm -rf /var/lib/apt/lists/* && \
    { [ -e /usr/bin/netcat ] || ln -s /usr/bin/nc /usr/bin/netcat; }

# Environment variables
# ENV SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Copy and prepare wait-for.sh
COPY wait-for.sh /wait-for.sh
RUN chmod +x /wait-for.sh && \
    sed -i 's/\r$//' /wait-for.sh 2>/dev/null || true

# Copy application
# COPY target/*.jar app.jar


# Copy application JAR from builder stage
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