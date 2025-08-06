# FROM eclipse-temurin:21-jdk

# WORKDIR /app

# # Install netcat-openbsd (recommended)
# RUN apt-get update && \
#     apt-get install -y netcat-openbsd && \
#     rm -rf /var/lib/apt/lists/*

# COPY wait-for.sh /wait-for.sh
# RUN chmod +x /wait-for.sh

# # COPY target/JobService-0.0.1-SNAPSHOT.jar /app/
# COPY target/*.jar app.jar

# # CMD ["/wait-for.sh", "kafka", "9092", "java", "-jar", "JobService-0.0.1-SNAPSHOT.jar"]
# CMD ["/wait-for.sh", "kafka", "9092", "java", "-jar", "app.jar"]



FROM eclipse-temurin:21-jdk

WORKDIR /app

# Install netcat-openbsd with clean package management
RUN apt-get update && \
    apt-get install -y netcat-openbsd && \
    rm -rf /var/lib/apt/lists/*

# Copy and prepare wait-for.sh with line ending conversion
COPY wait-for.sh /wait-for.sh
RUN chmod +x /wait-for.sh && \
    sed -i 's/\r$//' /wait-for.sh 2>/dev/null || true

# Copy application jar (using wildcard for version flexibility)
COPY target/*.jar app.jar

# Health check (optional but recommended)
HEALTHCHECK --interval=30s --timeout=3s \
  CMD nc -z localhost 8080 || exit 1

# Entrypoint with proper signal handling
CMD ["/bin/sh", "-c", "/wait-for.sh kafka 9092 && exec java -jar app.jar"]

