# -------- Stage 1: Build --------
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
# COPY src ./src

# Copy everything including src and force rebuild
COPY . .

RUN mvn clean package -DskipTests

# -------- Stage 2: Runtime --------
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Install netcat-openbsd (needed for wait-for.sh) and clean up
RUN apt-get update && \
    apt-get install -y netcat-openbsd curl && \
    rm -rf /var/lib/apt/lists/* && \
    { [ -e /usr/bin/netcat ] || ln -s /usr/bin/nc /usr/bin/netcat; }

# Copy and prepare wait-for.sh (handle Windows line endings)
COPY wait-for.sh /wait-for.sh
RUN chmod +x /wait-for.sh && sed -i 's/\r$//' /wait-for.sh || true

# Copy the application JAR from the builder stage
COPY --from=builder /build/target/*.jar app.jar

# Health check (optional)
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

# ENTRYPOINT waits for Discovery, Config Server, Kafka, then runs app
ENTRYPOINT ["/bin/sh", "-c", "\
    /wait-for.sh discovery-service 8090 && \
    /wait-for.sh config-server 8092 && \
    /wait-for.sh kafka 9092 && \
    exec java -jar app.jar"]
