# -------- Stage 1: Build --------
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# First copy only pom.xml to leverage Docker cache
COPY pom.xml .

# Pre-download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Now copy the rest of the source code
# COPY src ./src

# Copy everything including src and force rebuild
COPY . .

# Build the JAR (skip tests for faster builds)
RUN mvn clean package -DskipTests

# -------- Stage 2: Runtime --------
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copy compiled JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Expose Eureka server port
EXPOSE 8090

# Health check
HEALTHCHECK --interval=30s --timeout=5s \
  CMD nc -z localhost 8090 || exit 1

# Start Eureka server directly (no wait-for needed)
ENTRYPOINT ["java", "-jar", "app.jar"]
